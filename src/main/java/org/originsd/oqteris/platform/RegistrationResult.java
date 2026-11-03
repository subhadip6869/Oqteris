package org.originsd.oqteris.platform;

/**
 * Result of attempting to register Oqteris as the default PDF viewer.
 */
public class RegistrationResult {
    public enum Outcome {
        SUCCESS,
        GUIDANCE_REQUIRED,
        ERROR,
        UNSUPPORTED
    }

    private final Outcome outcome;
    private final String message;
    private final String instructions;

    public RegistrationResult(Outcome outcome, String message, String instructions) {
        this.outcome = outcome;
        this.message = message != null ? message : "";
        this.instructions = instructions != null ? instructions : "";
    }

    public static RegistrationResult success(String message) {
        return new RegistrationResult(Outcome.SUCCESS, message, null);
    }

    public static RegistrationResult guidanceRequired(String message, String instructions) {
        return new RegistrationResult(Outcome.GUIDANCE_REQUIRED, message, instructions);
    }

    public static RegistrationResult error(String message) {
        return new RegistrationResult(Outcome.ERROR, message, null);
    }

    public static RegistrationResult unsupported(String message) {
        return new RegistrationResult(Outcome.UNSUPPORTED, message, null);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean isGuidanceRequired() {
        return outcome == Outcome.GUIDANCE_REQUIRED;
    }
}
