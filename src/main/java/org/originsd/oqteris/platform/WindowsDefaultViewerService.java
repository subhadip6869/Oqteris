package org.originsd.oqteris.platform;

import org.originsd.oqteris.ui.icon.AppResources;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Windows implementation of default PDF viewer registration and detection.
 * Adheres to Windows 10/11 default app guidelines and UserChoice consent rules.
 */
public class WindowsDefaultViewerService implements DefaultViewerService {
    public static final String PROG_ID = "Oqteris.PDF";
    public static final String APP_NAME = "Oqteris";
    public static final String EXE_NAME = "Oqteris.exe";
    public static final String APP_DESCRIPTION = "Dedicated cross-platform desktop PDF reader and editor";

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
            // 1. Check UserChoice in FileExts\.pdf (Windows 10/11 primary association)
            String userChoiceOutput = runCommand("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\UserChoice",
                    "/v", "ProgId");

            if (userChoiceOutput != null) {
                if (userChoiceOutput.toLowerCase().contains("oqteris")) {
                    return DefaultViewerStatus.DEFAULT;
                }
                String progId = extractRegValue(userChoiceOutput, "ProgId");
                if (progId != null && !progId.isBlank() && isProgIdOqteris(progId)) {
                    return DefaultViewerStatus.DEFAULT;
                }
            }

            // 2. Check cmd /c assoc .pdf and ftype
            String assocOutput = runCommand("cmd", "/c", "assoc", ".pdf");
            if (assocOutput != null) {
                if (assocOutput.toLowerCase().contains("oqteris")) {
                    return DefaultViewerStatus.DEFAULT;
                }
                if (assocOutput.contains("=")) {
                    String assocProgId = assocOutput.substring(assocOutput.indexOf('=') + 1).trim();
                    if (!assocProgId.isEmpty()) {
                        String ftypeOutput = runCommand("cmd", "/c", "ftype", assocProgId);
                        if (ftypeOutput != null && ftypeOutput.toLowerCase().contains("oqteris")) {
                            return DefaultViewerStatus.DEFAULT;
                        }
                        if (isProgIdOqteris(assocProgId)) {
                            return DefaultViewerStatus.DEFAULT;
                        }
                    }
                }
            }

            // 3. Fallback: check HKCU\Software\Classes\.pdf default
            String hkcuOutput = runCommand("reg", "query", "HKCU\\Software\\Classes\\.pdf", "/ve");
            if (hkcuOutput != null) {
                if (hkcuOutput.toLowerCase().contains("oqteris")) {
                    return DefaultViewerStatus.DEFAULT;
                }
                String defProgId = extractRegDefaultValue(hkcuOutput);
                if (defProgId != null && isProgIdOqteris(defProgId)) {
                    return DefaultViewerStatus.DEFAULT;
                }
            }

            // 4. Fallback: check HKCR\.pdf default
            String hkcrOutput = runCommand("reg", "query", "HKCR\\.pdf", "/ve");
            if (hkcrOutput != null) {
                if (hkcrOutput.toLowerCase().contains("oqteris")) {
                    return DefaultViewerStatus.DEFAULT;
                }
                String defProgId = extractRegDefaultValue(hkcrOutput);
                if (defProgId != null && isProgIdOqteris(defProgId)) {
                    return DefaultViewerStatus.DEFAULT;
                }
            }

            return DefaultViewerStatus.NOT_DEFAULT;
        } catch (Exception e) {
            return DefaultViewerStatus.UNKNOWN;
        }
    }

    private boolean isProgIdOqteris(String progId) {
        if (progId == null || progId.isBlank()) return false;
        if (progId.toLowerCase().contains("oqteris")) return true;

        // Query open command in HKCU\Software\Classes\<progId>\shell\open\command
        String cmdHkcu = runCommand("reg", "query", "HKCU\\Software\\Classes\\" + progId + "\\shell\\open\\command", "/ve");
        if (cmdHkcu != null && cmdHkcu.toLowerCase().contains("oqteris")) {
            return true;
        }

        // Query open command in HKCR\<progId>\shell\open\command
        String cmdHkcr = runCommand("reg", "query", "HKCR\\" + progId + "\\shell\\open\\command", "/ve");
        return cmdHkcr != null && cmdHkcr.toLowerCase().contains("oqteris");
    }

    private String extractRegValue(String output, String valueName) {
        if (output == null) return null;
        for (String line : output.split("\\r?\\n")) {
            line = line.trim();
            if (line.startsWith(valueName)) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    return parts[2].trim();
                }
            }
        }
        return null;
    }

    private String extractRegDefaultValue(String output) {
        if (output == null) return null;
        for (String line : output.split("\\r?\\n")) {
            line = line.trim();
            if (line.contains("REG_SZ")) {
                int idx = line.indexOf("REG_SZ");
                String val = line.substring(idx + 6).trim();
                if (!val.isEmpty()) {
                    return val;
                }
            }
        }
        return null;
    }

    /**
     * Registers application presence in HKCU so Windows Explorer and Default Apps
     * show "Oqteris" as the friendly name, use the document icon for PDF thumbnails,
     * and list Oqteris automatically under available applications.
     */
    public void registerAppPresence() {
        try {
            // 1. Prepare icon files on disk
            File iconDir = getAppIconDir();
            File docIcon = new File(iconDir, "oqteris-document.ico");
            File appIcon = new File(iconDir, "oqteris-logo.ico");

            AppResources.extractResourceToDisk(AppResources.PDF_DOCUMENT_ICO, docIcon);
            AppResources.extractResourceToDisk(AppResources.APP_LOGO_ICO, appIcon);

            // 2. Prepare launch command and native executable path
            String exePath = findExecutablePath();
            String launchCommand = getEffectiveLaunchCommand(exePath);

            // 3. Register ProgID (with Document Icon so associated files use oqteris-document.ico)
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID, null, "REG_SZ", "PDF Document");
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID, "FriendlyTypeName", "REG_SZ", "PDF Document");
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID + "\\DefaultIcon", null, "REG_SZ", "\"" + docIcon.getAbsolutePath() + "\"");
            executeRegAdd("HKCU\\Software\\Classes\\" + PROG_ID + "\\shell\\open\\command", null, "REG_SZ", launchCommand);

            // 4. Register Applications\Oqteris.exe
            // FriendlyAppName "Oqteris" ensures Windows uses the application name in Default App Selection / Open With
            // DefaultIcon ensures Explorer displays the document icon (oqteris-document) for associated PDFs.
            // SupportedTypes ensures Oqteris is listed automatically in the available applications / Open With list.
            executeRegAdd("HKCU\\Software\\Classes\\Applications\\" + EXE_NAME, "FriendlyAppName", "REG_SZ", APP_NAME);
            executeRegAdd("HKCU\\Software\\Classes\\Applications\\" + EXE_NAME, "ApplicationCompany", "REG_SZ", "OriginSD");
            executeRegAdd("HKCU\\Software\\Classes\\Applications\\" + EXE_NAME + "\\DefaultIcon", null, "REG_SZ", "\"" + docIcon.getAbsolutePath() + "\"");
            executeRegAdd("HKCU\\Software\\Classes\\Applications\\" + EXE_NAME + "\\SupportedTypes", ".pdf", "REG_SZ", "");
            executeRegAdd("HKCU\\Software\\Classes\\Applications\\" + EXE_NAME + "\\shell\\open\\command", null, "REG_SZ", launchCommand);

            // 5. Register OpenWithProgids & OpenWithList for .pdf
            executeRegAdd("HKCU\\Software\\Classes\\.pdf\\OpenWithProgids", PROG_ID, "REG_NONE", "");
            executeRegAdd("HKCU\\Software\\Classes\\.pdf\\OpenWithProgids", "Applications\\" + EXE_NAME, "REG_NONE", "");
            executeRegAdd("HKCU\\Software\\Classes\\.pdf\\OpenWithList\\" + EXE_NAME, null, "REG_SZ", "");

            executeRegAdd("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\OpenWithProgids", PROG_ID, "REG_NONE", "");
            executeRegAdd("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\OpenWithProgids", "Applications\\" + EXE_NAME, "REG_NONE", "");
            executeRegAdd("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\OpenWithList", "a", "REG_SZ", EXE_NAME);

            // 6. Register App Paths
            if (exePath != null) {
                File exeFile = new File(exePath);
                executeRegAdd("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + EXE_NAME, null, "REG_SZ", "\"" + exePath + "\"");
                if (exeFile.getParent() != null) {
                    executeRegAdd("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + EXE_NAME, "Path", "REG_SZ", "\"" + exeFile.getParent() + "\"");
                }
            }

            // 7. Register Capabilities for Default Programs
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities", "ApplicationDescription", "REG_SZ", APP_DESCRIPTION);
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities", "ApplicationName", "REG_SZ", APP_NAME);
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities\\FileAssociations", ".pdf", "REG_SZ", PROG_ID);
            executeRegAdd("HKCU\\Software\\Oqteris\\Capabilities\\MIMEAssociations", "application/pdf", "REG_SZ", PROG_ID);
            executeRegAdd("HKCU\\Software\\RegisteredApplications", APP_NAME, "REG_SZ", "Software\\Oqteris\\Capabilities");

            // 8. Notify shell of association / icon change
            notifyShellAssocChanged();
        } catch (Exception e) {
            System.err.println("[WindowsDefaultViewerService] Warning during app presence registration: " + e.getMessage());
        }
    }

    @Override
    public RegistrationResult registerAsDefault() {
        try {
            registerAppPresence();

            // Check if already default
            if (checkStatus() == DefaultViewerStatus.DEFAULT) {
                return RegistrationResult.success("Oqteris is registered and configured as your default PDF viewer.");
            }

            // On modern Windows (Windows 10 / 11), prompt user via Settings
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

    public String findExecutablePath() {
        // 1. ProcessHandle command if running native executable
        try {
            String cmd = ProcessHandle.current().info().command().orElse(null);
            if (cmd != null && !cmd.isBlank()) {
                File exeFile = new File(cmd);
                if (exeFile.isFile() && exeFile.getName().toLowerCase().endsWith(".exe")
                        && !exeFile.getName().toLowerCase().startsWith("java")) {
                    return exeFile.getAbsolutePath();
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Relative to java.home (jpackage layout: <InstallDir>\runtime\bin -> <InstallDir>\Oqteris.exe)
        try {
            String javaHome = System.getProperty("java.home");
            if (javaHome != null) {
                File jHomeFile = new File(javaHome);
                File installDir = jHomeFile.getParentFile();
                if (installDir != null) {
                    File exe = new File(installDir, EXE_NAME);
                    if (exe.isFile()) {
                        return exe.getAbsolutePath();
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Known Program Files installation paths
        String progFiles = System.getenv("ProgramFiles");
        if (progFiles != null) {
            File exe = new File(progFiles, "Oqteris" + File.separator + EXE_NAME);
            if (exe.isFile()) {
                return exe.getAbsolutePath();
            }
        }

        String localApp = System.getenv("LOCALAPPDATA");
        if (localApp != null) {
            File exe = new File(localApp, "Programs" + File.separator + "Oqteris" + File.separator + EXE_NAME);
            if (exe.isFile()) {
                return exe.getAbsolutePath();
            }
            File exe2 = new File(localApp, "Oqteris" + File.separator + EXE_NAME);
            if (exe2.isFile()) {
                return exe2.getAbsolutePath();
            }
        }

        // 4. Registry App Paths
        try {
            String regAppPath = runCommand("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + EXE_NAME, "/ve");
            if (regAppPath != null) {
                String p = extractRegDefaultValue(regAppPath);
                if (p != null) {
                    if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2) {
                        p = p.substring(1, p.length() - 1);
                    }
                    if (new File(p).isFile()) {
                        return p;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public String getEffectiveLaunchCommand(String exePath) {
        if (exePath != null && !exePath.isBlank()) {
            return "\"" + exePath + "\" \"%1\"";
        }
        return buildLaunchCommand();
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
            pb = new ProcessBuilder("reg", "add", key, "/ve", "/t", type, "/d", (data != null ? data : ""), "/f");
        } else if ("REG_NONE".equals(type) || data == null) {
            pb = new ProcessBuilder("reg", "add", key, "/v", valueName, "/t", type, "/f");
        } else {
            pb = new ProcessBuilder("reg", "add", key, "/v", valueName, "/t", type, "/d", data, "/f");
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    private void notifyShellAssocChanged() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "ie4uinit.exe", "-show");
            pb.start();
        } catch (Throwable ignored) {
        }
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
