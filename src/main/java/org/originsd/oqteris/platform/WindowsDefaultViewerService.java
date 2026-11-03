package org.originsd.oqteris.platform;

import org.originsd.oqteris.ui.icon.AppResources;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Windows implementation of default PDF viewer registration and detection.
 * Adheres to Windows 10/11 default app guidelines and UserChoice consent rules.
 */
public class WindowsDefaultViewerService implements DefaultViewerService {
    public static final String PROG_ID = "Oqteris.PDF";
    public static final String APP_NAME = "Oqteris";

    @Override
    public String getPlatformName() {
        return "Windows";
    }

    @Override
    public boolean canDetectDefault() {
        return true;
    }

    @Override
    public DefaultViewerStatus checkStatus() {
        try {
            // Check UserChoice in FileExts\.pdf
            String userChoiceOutput = runCommand("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\UserChoice",
                    "/v", "ProgId");

            if (userChoiceOutput != null && userChoiceOutput.toLowerCase().contains("oqteris")) {
                return DefaultViewerStatus.DEFAULT;
            }

            // Fallback: check HKCR\.pdf default
            String hkcrOutput = runCommand("reg", "query", "HKCU\\Software\\Classes\\.pdf", "/ve");
            if (hkcrOutput != null && hkcrOutput.toLowerCase().contains("oqteris")) {
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
            // 1. Prepare icon files on disk
            File iconDir = getAppIconDir();
            File docIcon = new File(iconDir, "oqteris-document.ico");
            File appIcon = new File(iconDir, "oqteris-logo.ico");

            AppResources.extractResourceToDisk(AppResources.PDF_DOCUMENT_ICO, docIcon);
            AppResources.extractResourceToDisk(AppResources.APP_LOGO_ICO, appIcon);

            // 2. Prepare launch command
            String launchCommand = buildLaunchCommand();

            // 3. Register ProgID
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID, null, "REG_SZ", "PDF Document");
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID, "FriendlyTypeName", "REG_SZ", "PDF Document");
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID + "\\DefaultIcon", null, "REG_SZ", "\"" + docIcon.getAbsolutePath() + "\"");
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID + "\\shell\\open\\command", null, "REG_SZ", launchCommand);

            // 4. Register OpenWithProgids for .pdf
            executeRegAdd("HKCU\\Software\\Classes\\.pdf\\OpenWithProgids", PROG_ID, "REG_NONE", "");

            // 5. Register Capabilities for Default Programs
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities", "ApplicationDescription", "REG_SZ", "Oqteris PDF Reader and Editor");
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities", "ApplicationName", "REG_SZ", APP_NAME);
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities\\FileAssociations", ".pdf", "REG_SZ", PROG_ID);
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities\\MIMEAssociations", "application/pdf", "REG_SZ", PROG_ID);
            executeRegAdd("HKCU\\Software\\RegisteredApplications", APP_NAME, "REG_SZ", "Software\\Oqteris\\Capabilities");

            // 6. Check if already default
            if (checkStatus() == DefaultViewerStatus.DEFAULT) {
                return RegistrationResult.success("Oqteris is registered and configured as your default PDF viewer.");
            }

            // 7. On modern Windows (Windows 10 / 11), prompt user via Settings
            openWindowsDefaultAppsSettings();

            return RegistrationResult.guidanceRequired(
                    "Oqteris has been registered with Windows as a PDF viewer.",
                    "Windows requires manual confirmation in Default Apps settings:\n\n" +
                    "1. In the Windows Settings window that just opened, find '.pdf' or 'Oqteris'.\n" +
                    "2. Select Oqteris as the default application for .pdf files.\n" +
                    "3. Return to Oqteris."
            );
        } catch (Exception e) {
            return RegistrationResult.error("Failed to register Windows file association: " + e.getMessage());
        }
    }

    private File getAppIconDir() {
        String baseDir = System.getProperty("oqteris.config.dir");
        File dir;
        if (baseDir != null && !baseDir.isBlank()) {
            dir = new File(baseDir, ".oqteris" + File.separator + "icons");
        } else {
            String userHome = System.getProperty("user.home", ".");
            dir = new File(userHome, ".oqteris" + File.separator + "icons");
        }
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.exists()) {
                dir = new File(System.getProperty("java.io.tmpdir", "."), ".oqteris" + File.separator + "icons");
                dir.mkdirs();
            }
        }
        return dir;
    }

    public String buildLaunchCommand() {
        String javaHome = System.getProperty("java.home");
        File javaw = new File(javaHome, "bin" + File.separator + "javaw.exe");
        if (!javaw.exists()) {
            javaw = new File(javaHome, "bin" + File.separator + "java.exe");
        }

        // Check if running from JAR
        try {
            File codeSource = new File(WindowsDefaultViewerService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
                return "\"" + javaw.getAbsolutePath() + "\" -jar \"" + codeSource.getAbsolutePath() + "\" \"%1\"";
            }
        } catch (Exception ignored) {
        }

        // Generic fallback with classpath or launcher
        return "\"" + javaw.getAbsolutePath() + "\" -cp \"" + System.getProperty("java.class.path") + "\" org.originsd.oqteris.ui.OqterisLauncher \"%1\"";
    }

    private void executeRegAdd(String key, String valueName, String type, String data) throws Exception {
        ProcessBuilder pb;
        if (valueName == null) {
            pb = new ProcessBuilder("reg", "add", key, "/ve", "/t", type, "/d", data, "/f");
        } else if ("REG_NONE".equals(type) || data == null || data.isEmpty()) {
            pb = new ProcessBuilder("reg", "add", key, "/v", valueName, "/t", type, "/f");
        } else {
            pb = new ProcessBuilder("reg", "add", key, "/v", valueName, "/t", type, "/d", data, "/f");
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    private void openWindowsDefaultAppsSettings() {
        try {
            // Attempt ms-settings:defaultapps
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "ms-settings:defaultapps");
            pb.start();
        } catch (Exception ignored) {
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
