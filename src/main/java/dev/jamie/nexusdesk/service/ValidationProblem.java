package dev.jamie.nexusdesk.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A 400 problem that knows which form fields were wrong. */
public class ValidationProblem extends Problem {
    private final Map<String, String> fields;

    public ValidationProblem(Map<String, String> fields) {
        super(400, fields.size() == 1 ? fields.values().iterator().next() : "Check the highlighted fields.");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static ValidationProblem of(String field, String message) {
        return new ValidationProblem(Map.of(field, message));
    }

    public Map<String, String> fields() { return fields; }
}
