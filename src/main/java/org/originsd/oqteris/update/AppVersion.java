package org.originsd.oqteris.update;

/**
 * Global application metadata and version details for Oqteris.
 */
public final class AppVersion {

    public static final String APP_NAME = "Oqteris";
    public static final String CURRENT_VERSION = "1.1.0";
    public static final String GITHUB_REPO = "subhadip6869/Oqteris";
    public static final String GITHUB_REPO_URL = "https://github.com/subhadip6869/Oqteris";
    public static final String GITHUB_RELEASES_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private AppVersion() {}

    public static SemVer current() {
        return SemVer.parse(CURRENT_VERSION);
    }
}
