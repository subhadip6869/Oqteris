package org.originsd.oqteris.platform;

import org.originsd.oqteris.ui.icon.AppResources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * Linux implementation of default PDF viewer registration and detection
 * following freedesktop.org standards (XDG MIME and Desktop Entry Specifications).
 */
public class LinuxDefaultViewerService implements DefaultViewerService {
    public static final String DESKTOP_FILE_NAME = "oqteris.desktop";

    @Override
    public String getPlatformName() {
        return "Linux";
    }

    @Override
    public boolean canDetectDefault() {
        return isCommandAvailable("xdg-mime");
    }

    @Override
    public DefaultViewerStatus checkStatus() {
        if (!canDetectDefault()) {
            return DefaultViewerStatus.UNKNOWN;
        }

        try {
            String output = runCommand("xdg-mime", "query", "default", "application/pdf");
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
        try {
            String userHome = System.getProperty("user.home", ".");

            // 1. Install .desktop file
            File appDir = new File(userHome, ".local/share/applications");
            if (!appDir.exists()) {
                appDir.mkdirs();
            }
            File desktopFile = new File(appDir, DESKTOP_FILE_NAME);

            // 2. Install icon
            File iconDir = new File(userHome, ".local/share/icons/hicolor/256x256/apps");
            if (!iconDir.exists()) {
                iconDir.mkdirs();
            }
            File logoIcon = new File(iconDir, "oqteris-logo.png");
            AppResources.extractResourceToDisk(AppResources.APP_LOGO_PNG, logoIcon);

            String launchExec = buildLaunchCommand();
            try (FileWriter writer = new FileWriter(desktopFile)) {
                writer.write("[Desktop Entry]\n");
                writer.write("Type=Application\n");
                writer.write("Version=1.0\n");
                writer.write("Name=Oqteris\n");
                writer.write("GenericName=PDF Reader & Editor\n");
                writer.write("Comment=View, annotate, and verify digital signatures on PDF documents\n");
                writer.write("Icon=" + logoIcon.getAbsolutePath() + "\n");
                writer.write("Exec=" + launchExec + " %f\n");
                writer.write("Terminal=false\n");
                writer.write("MimeType=application/pdf;\n");
                writer.write("Categories=Office;Viewer;Graphics;\n");
                writer.write("StartupNotify=true\n");
            }

            // 3. Update desktop database
            runCommand("update-desktop-database", appDir.getAbsolutePath());

            // 4. Register MIME association via xdg-mime
            if (isCommandAvailable("xdg-mime")) {
                runCommand("xdg-mime", "default", DESKTOP_FILE_NAME, "application/pdf");
            }

            // 5. Verify status
            if (checkStatus() == DefaultViewerStatus.DEFAULT) {
                return RegistrationResult.success("Oqteris is registered and configured as your default PDF viewer.");
            }

            return RegistrationResult.guidanceRequired(
                    "Desktop entry installed and registered for application/pdf.",
                    "If Oqteris is not yet the active default, select Oqteris in your desktop environment's\n" +
                    "Settings -> Default Applications (or right-click a PDF file -> Open With -> Set as Default)."
            );
        } catch (Exception e) {
            return RegistrationResult.error("Failed to register Linux desktop association: " + e.getMessage());
        }
    }

    private String buildLaunchCommand() {
        String javaHome = System.getProperty("java.home");
        File javaBin = new File(javaHome, "bin/java");
        String javaPath = javaBin.exists() ? javaBin.getAbsolutePath() : "java";

        try {
            File codeSource = new File(LinuxDefaultViewerService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
                return javaPath + " -jar \"" + codeSource.getAbsolutePath() + "\"";
            }
        } catch (Exception ignored) {
        }

        return javaPath + " -cp \"" + System.getProperty("java.class.path") + "\" org.originsd.oqteris.ui.OqterisLauncher";
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
