package org.originsd.oqteris.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * macOS implementation of default PDF viewer registration and guidance.
 * Coordinates with LaunchServices and provides user instructions.
 */
public class MacDefaultViewerService implements DefaultViewerService {
    public static final String BUNDLE_ID = "org.originsd.oqteris";

    @Override
    public String getPlatformName() {
        return "macOS";
    }

    @Override
    public boolean canDetectDefault() {
        return isCommandAvailable("duti");
    }

    @Override
    public DefaultViewerStatus checkStatus() {
        if (!canDetectDefault()) {
            return DefaultViewerStatus.UNKNOWN;
        }

        try {
            String output = runCommand("duti", "-x", "pdf");
            if (output != null && output.toLowerCase().contains("oqteris")) {
                return DefaultViewerStatus.DEFAULT;
            }
            return DefaultViewerStatus.NOT_DEFAULT;
        } catch (Exception e) {
            return DefaultViewerStatus.UNKNOWN;
        }
    }

    @Override
    public RegistrationResult registerAsDefault() {
        // If duti CLI is installed, attempt programmatic registration
        if (isCommandAvailable("duti")) {
            try {
                runCommand("duti", "-s", BUNDLE_ID, "pdf", "all");
                if (checkStatus() == DefaultViewerStatus.DEFAULT) {
                    return RegistrationResult.success("Oqteris is registered and configured as your default PDF viewer.");
                }
            } catch (Exception ignored) {
            }
        }

        // Standard macOS user-consent guidance
        return RegistrationResult.guidanceRequired(
                "macOS requires user confirmation in Finder to associate all PDF documents.",
                "To set Oqteris as your default PDF viewer:\n\n" +
                "1. In Finder, right-click (or Control-click) any .pdf file.\n" +
                "2. Choose 'Get Info' (Command-I).\n" +
                "3. In the 'Open with:' section, select 'Oqteris'.\n" +
                "4. Click 'Change All...', then confirm when prompted."
        );
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String runCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
