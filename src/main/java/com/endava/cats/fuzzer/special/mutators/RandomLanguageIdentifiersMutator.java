package com.endava.cats.fuzzer.special.mutators;

import com.endava.cats.util.CatsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Sends random programming language identifiers in the target field.
 */
@Singleton
public class RandomLanguageIdentifiersMutator implements Mutator {
    private static final List<String> JAVA_KEYWORDS = List.of("abstract", "boolean", "byte", "case", "catch", "continue", "default", "do", "double", "enum", "extends", "final", "finally", "float", "goto", "implements", "instanceof", "interface", "long", "native", "new", "package", "private", "protected", "public", "short", "static", "strictfp", "super", "synchronized", "this", "throw", "throws", "transient", "try", "volatile");
    private static final List<String> PYTHON_KEYWORDS = List.of("assert", "break", "continue", "del", "elif", "except", "exec", "finally", "from", "global", "import", "in", "is", "lambda", "nonlocal", "not", "pass", "raise", "try", "with", "yield");
    private static final List<String> RUST_KEYWORDS = List.of("as", "async", "await", "const", "dyn", "else", "extern", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return", "self", "static", "struct", "super", "trait", "type", "unsafe", "use", "where", "while");
    private static final List<String> NODEJS_KEYWORDS = List.of("break", "case", "catch", "continue", "debugger", "default", "delete", "do", "finally", "for", "function", "instanceof", "throw", "try", "typeof", "var", "void", "while", "with");
    private static final List<String> CSHARP_KEYWORDS = List.of("as", "base", "bool", "break", "byte", "case", "catch", "char", "checked", "const", "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in", "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override", "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof", "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using", "virtual", "void", "volatile", "while", "add", "alias", "ascending", "async", "await", "by", "descending", "dynamic", "equals", "from", "get", "global", "group", "into", "join", "let", "nameof", "on", "orderby", "partial", "remove", "select", "set", "value", "var", "where", "yield");
    private static final List<String> JAVASCRIPT_KEYWORDS = List.of("arguments", "await", "case", "class", "const", "debugger", "delete", "enum", "eval", "export", "extends", "finally", "implements", "import", "interface", "let", "package", "private", "protected", "public", "static", "super", "switch", "this", "throw", "try", "typeof", "var", "void", "with", "yield", "function");
    private static final List<String> TYPESCRIPT_KEYWORDS = List.of("abstract", "any", "as", "bigint", "boolean", "break", "case", "catch", "class", "continue", "const", "constructor", "debugger", "declare", "default", "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for", "from", "function", "get", "if", "implements", "import", "in", "infer", "instanceof", "interface", "is", "keyof", "let", "module", "namespace", "never", "new", "null", "number", "object", "package", "private", "protected", "public", "readonly", "require", "globalThis", "return", "set", "static", "string", "super", "switch", "symbol", "this", "throw", "true", "try", "type", "typeof", "undefined", "union", "unique", "unknown", "var", "void", "while", "with", "yield");
    private static final List<String> PHP_KEYWORDS = List.of("abstract", "and", "array", "as", "callable", "case", "catch", "class", "clone", "const", "continue", "declare", "default", "die", "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final", "finally", "for", "foreach", "function", "global", "goto", "if", "implements", "include", "include_once", "instanceof", "insteadof", "interface", "isset", "list", "namespace", "new", "or", "print", "private", "protected", "public", "require", "require_once", "return", "static", "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor", "__halt_compiler", "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class", "clone", "const", "continue", "declare", "default", "die", "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final", "finally", "for", "foreach", "function", "global", "goto", "if", "implements", "include", "include_once", "instanceof", "insteadof", "interface", "isset", "list", "namespace", "new", "or", "print", "private", "protected", "public", "require", "require_once", "return", "static", "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor", "__halt_compiler");
    private static final List<String> CPP_KEYWORDS = List.of("alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool", "break", "case", "catch", "char", "char16_t", "char32_t", "class", "compl", "const", "constexpr", "const_cast", "continue", "decltype", "default", "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected", "public", "register", "reinterpret_cast", "return", "short", "signed", "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "template", "this", "thread_local", "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq");
    private static final List<String> C_KEYWORDS = List.of("alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool", "break", "case", "catch", "char", "char16_t", "char32_t", "class", "compl", "const", "constexpr", "const_cast", "continue", "decltype", "default", "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected", "public", "register", "reinterpret_cast", "return", "short", "signed", "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "template", "this", "thread_local", "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq");
    private static final List<String> RUBY_KEYWORDS = List.of("__ENCODING__", "__FILE__", "__LINE__", "__END__", "BEGIN", "END", "__ENCODING__", "__FILE__", "__LINE__", "__END__", "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do", "else", "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not", "or", "redo", "rescue", "retry", "return", "self", "super", "then", "true", "undef", "unless", "until", "when", "while", "yield");
    private static final List<String> GOLANG_KEYWORDS = List.of("break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return", "select", "struct", "switch", "type", "var");
    private static final List<String> SQL_KEYWORDS = List.of("ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "BETWEEN", "BY", "CHAR", "CHECK", "COLUMN", "CONSTRAINT", "CREATE", "DATABASE", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "END", "ENUM", "EXCEPT", "EXISTS", "FALSE", "FOR", "FOREIGN", "FROM", "FULL", "GRANT", "GROUP", "HAVING", "IDENTITY", "IF", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE", "LIMIT", "LOCK", "MATCH", "NATURAL", "NO", "NOT", "NULL", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "PROCEDURE", "RIGHT", "ROWNUM", "SELECT", "SET", "TABLE", "THEN", "TOP", "TRUE", "UNION", "UNIQUE", "UPDATE", "USING", "VALUES", "VIEW", "WHEN", "WHERE", "WITH");
    private static final List<String> KOTLIN_KEYWORDS = List.of("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var", "when", "while");
    private static final List<String> SCALA_KEYWORDS = List.of("abstract", "case", "catch", "class", "def", "do", "else", "extends", "false", "final", "finally", "for", "forSome", "if", "implicit", "import", "lazy", "match", "new", "null", "object", "override", "package", "private", "protected", "return", "sealed", "super", "this", "throw", "trait", "try", "true", "type", "val", "var", "while", "with", "yield");

    private final CatsUtil catsUtil;

    @Inject
    public RandomLanguageIdentifiersMutator(CatsUtil catsUtil) {
        this.catsUtil = catsUtil;
    }

    @Override
    public String mutate(String inputJson, String selectedField) {
        List<String> randomKeywordsList = selectRandomKeywords();

        String randomKeywords = CatsUtil.random().ints(0, randomKeywordsList.size())
                .limit(4)
                .mapToObj(randomKeywordsList::get)
                .collect(Collectors.joining(" "));


        return catsUtil.justReplaceField(inputJson, selectedField, randomKeywords).json();
    }

    private static List<String> selectRandomKeywords() {
        int randomNumber = CatsUtil.random().nextInt(15);
        return switch (randomNumber) {
            case 0 -> JAVA_KEYWORDS;
            case 1 -> PYTHON_KEYWORDS;
            case 2 -> RUST_KEYWORDS;
            case 3 -> NODEJS_KEYWORDS;
            case 4 -> CSHARP_KEYWORDS;
            case 5 -> JAVASCRIPT_KEYWORDS;
            case 6 -> TYPESCRIPT_KEYWORDS;
            case 7 -> PHP_KEYWORDS;
            case 8 -> CPP_KEYWORDS;
            case 9 -> C_KEYWORDS;
            case 10 -> RUBY_KEYWORDS;
            case 11 -> GOLANG_KEYWORDS;
            case 12 -> SQL_KEYWORDS;
            case 13 -> KOTLIN_KEYWORDS;
            default -> SCALA_KEYWORDS;
        };
    }

    @Override
    public String name() {
        return "random programming languages keywords";
    }
}