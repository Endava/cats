package com.endava.cats.report.error.extract;

import com.endava.cats.report.error.contract.ErrorResponseContract;
import com.endava.cats.report.error.fields.ErrorEnvelope;
import com.endava.cats.report.error.fields.ErrorFieldReader;
import com.endava.cats.report.error.fields.ErrorFieldRoleResolver;
import com.endava.cats.report.error.fields.ErrorTypeFields;
import com.endava.cats.report.error.fields.JsonErrorFields;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts the root cause of JSON error bodies. Besides the usual {@code error}/{@code message} fields it reads the
 * structured messages of the common frameworks:
 * <ul>
 *     <li>string arrays: NestJS {@code message: [...]}, Spring {@code messages: [...]};</li>
 *     <li>arrays of objects with a message and a location: FastAPI {@code detail: [{loc, msg}]}, RFC 9457
 *     {@code errors: [{pointer, detail}]}, Spring {@code errors: [{field, defaultMessage}]}, express-validator;</li>
 *     <li>maps of field to messages: ASP.NET, Laravel and Rails {@code errors: {field: [...]}}, and Django REST
 *     framework or Rails scaffolds when the whole body is such a map.</li>
 * </ul>
 * Custom envelopes are read by field role ({@link ErrorFieldReader}): the roles come from the contract when the body
 * matches the error schema it declares for the response ({@link ErrorResponseContract}), from the field names
 * otherwise. Bodies cut in the middle of the JSON (report summaries keep 1000 characters) are recovered from the
 * string fields that survived, read by field name.
 */
public final class StructuredJsonErrorExtractor implements ErrorBodyExtractor {
    private static final List<String> STACK_KEYS = Stream.concat(JsonErrorFields.STACK_KEYS.stream(),
            Stream.of("traces", "backtrace", "stack_trace")).toList();
    private static final List<String> TRACE_ENTRY_KEYS = List.of("trace", "frame");
    private static final List<String> TRUNCATED_DETAIL_KEYS = List.of("detail", "message", "description", "error_description",
            "reason", "msg", "errorMessage", "error_message");
    private static final Pattern RUBY_INSPECT = Pattern.compile("(?s)#<([\\w:]+):\\s*(.*)>");
    private static final Pattern PHP_CLOSURE = Pattern.compile("\\{closure[^}]*}");

    private final ErrorResponseContract contract;

    StructuredJsonErrorExtractor() {
        this(ErrorResponseContract.NONE);
    }

    StructuredJsonErrorExtractor(ErrorResponseContract contract) {
        this.contract = contract;
    }

    @Override
    public Optional<ErrorCause> extract(String body) {
        String trimmed = body.strip();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return Optional.empty();
        }
        if (!JsonUtils.isValidJson(trimmed)) {
            return truncated(trimmed);
        }
        try {
            return fromRoot(JsonUtils.parseAsJsonElement(trimmed));
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    private Optional<ErrorCause> fromRoot(JsonElement root) {
        if (root.isJsonObject()) {
            return Optional.of(fromObject(root.getAsJsonObject())).filter(cause -> !cause.isEmpty());
        }
        List<JsonElement> items = root.isJsonArray() ? root.getAsJsonArray().asList() : List.of();
        if (!items.isEmpty() && items.getFirst().isJsonObject()) {
            return fromRoot(items.getFirst());
        }
        boolean messageArray = !items.isEmpty() && items.stream().allMatch(JsonElement::isJsonPrimitive);
        return messageArray ? Optional.of(new ErrorCause("", nameReader().arrayMessages(root.getAsJsonArray()), "")) : Optional.empty();
    }

    private ErrorCause fromObject(JsonObject root) {
        ErrorFieldReader reader = new ErrorFieldReader(contract.rolesFor(root));
        JsonObject obj = ErrorEnvelope.unwrap(root);
        String stack = stackText(obj);
        List<String> messages = ErrorFieldReader.firstNonEmpty(() -> reader.messages(obj), () -> traceMessage(stack));
        String frame = StackFrameParser.crashSite(stack.isEmpty() ? String.join("\n", messages) : stack);
        ErrorCause cause = withRubyInspectionSplit(causeWithType(root, reader.type(obj), messages, frame));
        return cause.isEmpty() ? new ErrorCause("", List.of(reader.leaves(obj)), frame) : cause;
    }

    /**
     * Bodies without a code or type field keep the type of the first nested code, type, message or detail of the
     * error object (Google-style {@code {"error": {"message": "Invalid channel."}}}): the message then partitions the
     * templates and is not repeated as a message.
     */
    private static ErrorCause causeWithType(JsonObject root, ErrorTypeFields.ErrorType type, List<String> messages, String frame) {
        if (!type.value().isEmpty()) {
            return causeOf(type, messages, frame);
        }
        String nestedType = JsonErrorFields.errorType(root);
        List<String> others = messages.stream().filter(message -> !message.strip().equalsIgnoreCase(nestedType)).toList();
        return new ErrorCause(nestedType, others, frame);
    }

    private static ErrorCause causeOf(ErrorTypeFields.ErrorType type, List<String> messages, String frame) {
        return new ErrorCause(type.value(), messages, frame, type.fromCodeField());
    }

    private static ErrorFieldReader nameReader() {
        return new ErrorFieldReader(ErrorFieldRoleResolver.BY_NAME);
    }

    /**
     * Without any message field, the exception line of the trace is the message, e.g. Spring's
     * {@code "trace": "java.lang.NullPointerException: Cannot invoke ...\n\tat ..."}.
     */
    private static List<String> traceMessage(String stack) {
        return StackFrameParser.parse(stack).map(trace -> trace.message(stack)).filter(StringUtils::isNotBlank)
                .map(List::of).orElse(List.of());
    }

    /**
     * Rails reports the exception as {@code #<KeyError: key not found: :owner>}: the class is the error type and the
     * rest is the message when the body has no other one.
     */
    private static ErrorCause withRubyInspectionSplit(ErrorCause cause) {
        Matcher inspection = RUBY_INSPECT.matcher(cause.errorType());
        if (!inspection.matches()) {
            return cause;
        }
        List<String> messages = cause.messages().isEmpty() ? List.of(inspection.group(2)) : cause.messages();
        return new ErrorCause(inspection.group(1), messages, cause.stackFrame());
    }

    private static String stackText(JsonObject obj) {
        return STACK_KEYS.stream().map(obj::get).filter(value -> value != null && !value.isJsonNull())
                .map(StructuredJsonErrorExtractor::traceText).filter(StringUtils::isNotBlank).findFirst().orElse("");
    }

    /**
     * Flattens a trace value into text: a string, an array of lines, Rails' {@code {"Application Trace": [{"trace": ...}]}}
     * or Laravel's {@code [{"file", "line", "function"}]}, which is rendered like a PHP trace.
     */
    private static String traceText(JsonElement trace) {
        if (trace.isJsonPrimitive()) {
            return trace.getAsString();
        }
        Stream<JsonElement> children = trace.isJsonArray() ? trace.getAsJsonArray().asList().stream()
                : trace.getAsJsonObject().asMap().values().stream();
        return children.map(StructuredJsonErrorExtractor::traceEntry).collect(Collectors.joining("\n"));
    }

    private static String traceEntry(JsonElement entry) {
        if (!entry.isJsonObject()) {
            return traceText(entry);
        }
        JsonObject frame = entry.getAsJsonObject();
        String line = ErrorFieldReader.firstPrimitive(frame, TRACE_ENTRY_KEYS);
        return line.isEmpty() && frame.has("file") ? phpTraceLine(frame) : line;
    }

    private static String phpTraceLine(JsonObject frame) {
        String callee = Stream.of("class", "function").map(key -> ErrorFieldReader.firstPrimitive(frame, List.of(key)))
                .filter(StringUtils::isNotBlank).map(name -> PHP_CLOSURE.matcher(name).replaceAll("{closure}"))
                .collect(Collectors.joining("::"));
        return "#0 %s(%s): %s()".formatted(ErrorFieldReader.firstPrimitive(frame, List.of("file")),
                ErrorFieldReader.firstPrimitive(frame, List.of("line")), callee);
    }

    /**
     * Report summaries keep only the first 1000 characters of a body, which often cuts a JSON error object in half.
     * The error type and the first detail are recovered from the string fields that survived, at any depth and by
     * field name: a cut body is neither matched against the contract nor read by field role.
     */
    private static Optional<ErrorCause> truncated(String trimmed) {
        if (!trimmed.startsWith("{")) {
            return Optional.empty();
        }
        Map<String, String> fields = TruncatedJsonFields.recover(trimmed);
        // Trace recovery returns a captured string (possibly empty), or an empty string when absent.
        String stack = Objects.requireNonNull(TruncatedJsonFields.trace(trimmed));
        String detail = firstPresent(fields, TRUNCATED_DETAIL_KEYS);
        List<String> messages = detail.isEmpty() ? traceMessage(stack) : List.of(detail);
        String frame = StackFrameParser.crashSite(stack.isEmpty() ? TruncatedJsonFields.unescape(trimmed) : stack);
        ErrorCause cause = withRubyInspectionSplit(causeOf(truncatedType(fields), messages, frame));
        return Optional.of(cause).filter(found -> !found.isEmpty());
    }

    private static ErrorTypeFields.ErrorType truncatedType(Map<String, String> fields) {
        JsonObject recovered = new JsonObject();
        fields.forEach(recovered::addProperty);
        return ErrorTypeFields.choose(recovered, field -> Optional.empty());
    }

    private static String firstPresent(Map<String, String> fields, List<String> keys) {
        return keys.stream().map(fields::get).filter(Objects::nonNull).filter(StringUtils::isNotBlank).findFirst().orElse("");
    }
}
