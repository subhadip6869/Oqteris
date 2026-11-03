package org.originsd.oqteris.update;

/**
 * Result representing the outcome of checking for application updates on GitHub.
 */
public record UpdateCheckResult(
        Status status,
        ReleaseInfo release,
        int statusCode,
        String errorMessage
) {
    public enum Status {
        /** A newer semantic version is available for download */
        UPDATE_AVAILABLE,

        /** The current version is equal to or newer than the latest published release */
        UP_TO_DATE,

        /** HTTP 404: No releases have been published yet in the repository */
        NO_RELEASES_PUBLISHED,

        /** HTTP 403: GitHub API hourly rate limit reached for anonymous requests */
        RATE_LIMITED,

        /** HTTP 5xx: GitHub servers encountered an error */
        SERVER_ERROR,

        /** Connection exception, timeout, DNS resolution failure, or offline */
        NETWORK_ERROR
    }

    public boolean isSuccess() {
        return status == Status.UPDATE_AVAILABLE || status == Status.UP_TO_DATE || status == Status.NO_RELEASES_PUBLISHED;
    }
}
