package com.endava.cats.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for extracting schema references from OpenAPI specification files.
 * <p>
 * This class supports recursive traversal of YAML or JSON files to identify all
 * referenced schemas, whether defined locally within the same file or across
 * external files with JSON pointers.
 * <p>
 * It avoids cyclic dependencies by tracking visited files and logs any errors or warnings
 * encountered during the process.
 */
public abstract class OpenApiRefExtractor {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(OpenApiRefExtractor.class);
    private static final Pattern REF_PATTERN = Pattern.compile("\\$ref:\\s*['\"]?\\s*([^'\"\\s]+)['\"]?");
    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.(ya?ml|json)$", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts all referenced schema names from an OpenAPI contract file.
     *
     * @param contractPath the path to the OpenAPI contract file (YAML or JSON)
     * @return a set of referenced schema names
     */
    public static Set<String> extractRefsFromOpenAPI(String contractPath) {
        Set<String> referencedSchemas = new HashSet<>();
        Set<Path> visitedFiles = new HashSet<>();

        try {
            Path startPath = Paths.get(contractPath).toAbsolutePath();
            extractRefsRecursive(startPath, referencedSchemas, visitedFiles);
        } catch (Exception e) {
            LOGGER.error("Failed to extract references from OpenAPI contract '{}': {}", contractPath, e.getMessage());
        }

        return referencedSchemas;
    }

    private static void extractRefsRecursive(Path filePath, Set<String> referencedSchemas, Set<Path> visitedFiles) {
        if (visitedFiles.contains(filePath)) {
            return;
        }
        visitedFiles.add(filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            LOGGER.warn("File does not exist or is not a regular file: {}", filePath);
            return;
        }

        try (Stream<String> lines = Files.lines(filePath)) {
            lines.forEach(line -> processLine(line, filePath, referencedSchemas, visitedFiles));
        } catch (IOException e) {
            LOGGER.error("Error reading OpenAPI contract '{}': {}", filePath, e.getMessage());
        }
    }

    private static void processLine(String line, Path currentFile, Set<String> referencedSchemas, Set<Path> visitedFiles) {
        Matcher refMatcher = REF_PATTERN.matcher(line);

        while (refMatcher.find()) {
            String refValue = refMatcher.group(1);
            processReference(refValue, currentFile, referencedSchemas, visitedFiles);
        }
    }

    private static void processReference(String refValue, Path currentFile, Set<String> referencedSchemas, Set<Path> visitedFiles) {
        RefInfo refInfo = parseReference(refValue);

        if (refInfo.isLocalPointer()) {
            extractSchemaFromPointer(refInfo.pointer, referencedSchemas);
        } else if (refInfo.isFileReference()) {
            processFileReference(refInfo, currentFile, referencedSchemas, visitedFiles);
        }
    }

    private static void processFileReference(RefInfo refInfo, Path currentFile, Set<String> referencedSchemas, Set<Path> visitedFiles) {
        Path resolvedPath = resolveRelativePath(currentFile, refInfo.filePath);

        if (refInfo.hasPointer()) {
            extractSchemaFromPointer(refInfo.pointer, referencedSchemas);
            extractRefsRecursive(resolvedPath, referencedSchemas, visitedFiles);
        } else {
            String schemaName = getFileNameWithoutExtension(Paths.get(refInfo.filePath).getFileName().toString());
            referencedSchemas.add(schemaName);
            extractRefsRecursive(resolvedPath, referencedSchemas, visitedFiles);
        }
    }

    private static void extractSchemaFromPointer(String pointer, Set<String> referencedSchemas) {
        if (!pointer.startsWith("#/")) {
            return;
        }

        String[] segments = pointer.substring(2).split("/", -1);

        if (segments.length == 0) {
            return;
        }

        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i].trim();
            if (!segment.isEmpty()) {
                referencedSchemas.add(segment);
                break;
            }
        }
    }

    private static RefInfo parseReference(String refValue) {
        if (refValue.startsWith("#/")) {
            return new RefInfo("", refValue);
        }

        String[] parts = refValue.split("#", 2);
        String filePath = parts[0];
        String pointer = parts.length > 1 ? "#" + parts[1] : "";

        return new RefInfo(filePath, pointer);
    }

    private static boolean isOpenAPIFile(String filePath) {
        return FILE_EXTENSION_PATTERN.matcher(filePath).find();
    }

    private static Path resolveRelativePath(Path currentFile, String relativePath) {
        if (relativePath.isEmpty()) {
            return currentFile;
        }
        return currentFile.getParent().resolve(relativePath).normalize();
    }

    private static String getFileNameWithoutExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
    }

    private record RefInfo(String filePath, String pointer) {
        private RefInfo(String filePath, String pointer) {
            this.filePath = filePath != null ? filePath.trim() : "";
            this.pointer = pointer != null ? pointer.trim() : "";
        }

        boolean isLocalPointer() {
            return filePath.isEmpty() && pointer.startsWith("#/");
        }

        boolean isFileReference() {
            return !filePath.isEmpty() && isOpenAPIFile(filePath);
        }

        boolean hasPointer() {
            return !pointer.isEmpty();
        }
    }
}