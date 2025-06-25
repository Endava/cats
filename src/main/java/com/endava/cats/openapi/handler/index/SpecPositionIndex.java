package com.endava.cats.openapi.handler.index;

import com.endava.cats.util.JsonUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import jakarta.inject.Singleton;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.nodes.AnchorNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes YAML *or* JSON specs ⇒ pointer → Position(line,col)
 */
@Singleton
public class SpecPositionIndex {
    private final Map<String, SpecPosition> idx = new HashMap<>();

    public void parseSpecs(Path spec) throws IOException {
        String name = spec.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            parseYaml(spec);
        } else {
            parseJson(spec);
        }
    }

    /**
     * Locates the position of a given JSON pointer in the indexed specification.
     *
     * @param pointer the JSON pointer to locate
     * @return an Optional containing the SpecPosition if found, or empty if not found
     */
    public Optional<SpecPosition> locate(String pointer) {
        return Optional.ofNullable(idx.get(pointer));
    }

    /**
     * Parses a YAML file and builds an index of positions for each node.
     *
     * @param yml the path to the YAML file
     * @throws IOException if an I/O error occurs while reading the file
     */
    private void parseYaml(Path yml) throws IOException {
        LoadSettings ls = LoadSettings.builder().setUseMarks(true).build();
        Node root = new Compose(ls).composeInputStream(Files.newInputStream(yml))
                .orElseThrow();
        walkYaml(root, new ArrayDeque<>());
    }

    /**
     * Recursively walks through the YAML nodes and records their positions in the index.
     *
     * @param n    the current YAML node
     * @param path the current path in the YAML structure
     */
    private void walkYaml(Node n, Deque<String> path) {
        idx.put(JsonUtils.toPointer(path), toPos(n.getStartMark().orElse(null)));

        switch (n.getNodeType()) {
            case MAPPING -> ((MappingNode) n).getValue().forEach(t -> {
                path.addLast(((ScalarNode) t.getKeyNode()).getValue());
                walkYaml(t.getValueNode(), path);
                path.removeLast();
            });

            case SEQUENCE -> {
                int i = 0;
                for (Node child : ((SequenceNode) n).getValue()) {
                    path.addLast(Integer.toString(i++));
                    walkYaml(child, path);
                    path.removeLast();
                }
            }

            case ANCHOR -> walkYaml(((AnchorNode) n).getRealNode(), path);

            default /* SCALAR */ -> { /* already recorded */ }
        }
    }

    /**
     * Parses a JSON file and builds an index of positions for each node.
     *
     * @param json the path to the JSON file
     * @throws IOException if an I/O error occurs while reading the file
     */
    private void parseJson(Path json) throws IOException {
        JsonFactory jf = JsonFactory.builder()
                .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .build();

        try (JsonParser p = jf.createParser(Files.newInputStream(json))) {
            Deque<String> ptr = new ArrayDeque<>();
            Deque<Integer> arr = new ArrayDeque<>();

            while (p.nextToken() != null) {
                JsonToken t = p.currentToken();
                switch (t) {
                    case START_OBJECT -> record(ptr, p);
                    case START_ARRAY -> {
                        record(ptr, p);
                        arr.push(0);
                    }
                    case FIELD_NAME -> ptr.addLast(p.currentName());
                    case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT,
                         VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> {
                        record(ptr, p);
                        safeRemoveLast(ptr);
                        bumpArrIndex(arr, ptr);
                    }
                    case END_OBJECT -> {
                        safeRemoveLast(ptr);
                        bumpArrIndex(arr, ptr);
                    }
                    case END_ARRAY -> {
                        safeRemoveLast(ptr);
                        arr.pop();
                    }
                    default -> { /* ignore */ }
                }
            }
        }
    }

    /**
     * Bumps the last index in the array index stack and updates the pointer accordingly.
     *
     * @param arr the stack of array indices
     * @param ptr the stack of JSON pointers
     */
    private void bumpArrIndex(Deque<Integer> arr, Deque<String> ptr) {
        if (!arr.isEmpty()) {
            int i = arr.pop() + 1;
            arr.push(i);
            if (!ptr.isEmpty()) {
                ptr.removeLast();
                ptr.addLast(Integer.toString(i));
            }
        }
    }

    private static void safeRemoveLast(Deque<?> d) {
        if (!d.isEmpty()) {
            d.removeLast();
        }
    }

    private void record(Deque<String> ptr, JsonParser p) {
        idx.put(JsonUtils.toPointer(ptr), toPos(p.currentLocation()));
    }

    private static SpecPosition toPos(Mark m) {
        return m == null ? null : new SpecPosition(m.getLine() + 1, m.getColumn() + 1);
    }

    private static SpecPosition toPos(JsonLocation l) {
        return new SpecPosition(l.getLineNr(), l.getColumnNr());
    }
}
