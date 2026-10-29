package com.bobincraft.armament.validation;

/** Invalid armament data. Messages identify a JSON-style field path. */
public final class DefinitionValidationException extends IllegalArgumentException {
    private final String path;
    private final String reason;

    public DefinitionValidationException(String path, String message) {
        super(path + ": " + message);
        this.path = path;
        this.reason = message;
    }

    public String path() { return path; }
    public String reason() { return reason; }
}
