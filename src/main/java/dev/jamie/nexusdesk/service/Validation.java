package dev.jamie.nexusdesk.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every field problem in a submission so the form can show them all at once,
 * instead of stopping at the first one.
 */
public final class Validation {
    private final Map<String, String> errors = new LinkedHashMap<>();

    /** Returns the trimmed value, or null after recording a field error. */
    public String text(String field, String value, String label, int max) {
        if (value == null || value.isBlank()) {
            reject(field, label + " is required.");
            return null;
        }
        String clean = value.strip();
        if (clean.length() > max) {
            reject(field, label + " must be " + max + " characters or fewer (it is " + clean.length() + ").");
            return null;
        }
        return clean;
    }

    public String choice(String field, String value, List<String> choices, String label) {
        if (value == null || !choices.contains(value)) {
            reject(field, "Choose a valid " + label + ".");
            return null;
        }
        return value;
    }

    /** Keeps the first message for a field, so the most basic problem is the one shown. */
    public void reject(String field, String message) { errors.putIfAbsent(field, message); }

    public boolean has(String field) { return errors.containsKey(field); }

    public void check() {
        if (!errors.isEmpty()) throw new ValidationProblem(errors);
    }
}
