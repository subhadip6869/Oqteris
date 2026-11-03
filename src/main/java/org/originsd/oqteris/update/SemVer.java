package org.originsd.oqteris.update;

/**
 * Semantic version representation supporting major, minor, patch, and pre-release components.
 */
public record SemVer(int major, int minor, int patch, String preRelease) implements Comparable<SemVer> {

    public static SemVer parse(String version) {
        if (version == null || version.isBlank()) {
            return new SemVer(0, 0, 0, "");
        }
        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1).trim();
        }
        String[] parts = clean.split("-", 2);
        String[] nums = parts[0].split("\\.");
        int maj = nums.length > 0 ? tryParse(nums[0]) : 0;
        int min = nums.length > 1 ? tryParse(nums[1]) : 0;
        int pat = nums.length > 2 ? tryParse(nums[2]) : 0;
        String pre = parts.length > 1 ? parts[1].trim() : "";
        return new SemVer(maj, min, pat, pre);
    }

    private static int tryParse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int compareTo(SemVer o) {
        if (o == null) return 1;
        if (this.major != o.major) return Integer.compare(this.major, o.major);
        if (this.minor != o.minor) return Integer.compare(this.minor, o.minor);
        if (this.patch != o.patch) return Integer.compare(this.patch, o.patch);

        // Standard SemVer: release with no pre-release is newer than one with pre-release
        if (this.preRelease.isEmpty() && !o.preRelease.isEmpty()) return 1;
        if (!this.preRelease.isEmpty() && o.preRelease.isEmpty()) return -1;
        return this.preRelease.compareToIgnoreCase(o.preRelease);
    }

    public boolean isNewerThan(SemVer other) {
        return this.compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (preRelease.isEmpty() ? "" : "-" + preRelease);
    }
}
