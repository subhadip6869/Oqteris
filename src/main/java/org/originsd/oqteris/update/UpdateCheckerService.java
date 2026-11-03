package org.originsd.oqteris.update;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service managing Git release verification, platform-specific asset downloading,
 * progress tracking, and secure installer execution with guaranteed package deletion.
 */
public class UpdateCheckerService {

    private static final UpdateCheckerService INSTANCE = new UpdateCheckerService();

    private volatile HttpClient httpClient;

    private UpdateCheckerService() {
    }

    private HttpClient getClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return httpClient;
    }

    public static UpdateCheckerService getInstance() {
        return INSTANCE;
    }

    /**
     * Checks GitHub for the latest release asynchronously.
     */
    public CompletableFuture<UpdateCheckResult> checkLatestRelease() {
        return checkLatestRelease(AppVersion.GITHUB_RELEASES_API);
    }

    public CompletableFuture<UpdateCheckResult> checkLatestRelease(String apiUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Oqteris-PDF-Editor/" + AppVersion.CURRENT_VERSION)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, ex) -> {
                    if (ex != null) {
                        return new UpdateCheckResult(UpdateCheckResult.Status.NETWORK_ERROR, null, -1, ex.getMessage());
                    }
                    if (response == null) {
                        return new UpdateCheckResult(UpdateCheckResult.Status.NETWORK_ERROR, null, -1, "No response from server");
                    }
                    int code = response.statusCode();
                    if (code == 200) {
                        ReleaseInfo release = parseReleaseJson(response.body());
                        if (release != null && release.getSemVer().isNewerThan(AppVersion.current())) {
                            return new UpdateCheckResult(UpdateCheckResult.Status.UPDATE_AVAILABLE, release, code, null);
                        } else {
                            return new UpdateCheckResult(UpdateCheckResult.Status.UP_TO_DATE, release, code, null);
                        }
                    } else if (code == 404) {
                        // Expected when no releases have been published yet on GitHub
                        return new UpdateCheckResult(UpdateCheckResult.Status.NO_RELEASES_PUBLISHED, null, code, "No releases published yet on GitHub");
                    } else if (code == 403) {
                        return new UpdateCheckResult(UpdateCheckResult.Status.RATE_LIMITED, null, code, "GitHub API rate limit reached");
                    } else if (code >= 500) {
                        return new UpdateCheckResult(UpdateCheckResult.Status.SERVER_ERROR, null, code, "GitHub server error: HTTP " + code);
                    } else {
                        return new UpdateCheckResult(UpdateCheckResult.Status.NETWORK_ERROR, null, code, "HTTP " + code);
                    }
                });
    }

    /**
     * Downloads the release asset with live progress tracking, saving to a temporary file.
     */
    public CompletableFuture<Path> downloadAsset(ReleaseAsset asset, Consumer<Double> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempFile = null;
            try {
                String name = asset.name() != null ? asset.name() : "update.bin";
                String suffix = name.contains(".") ? name.substring(name.lastIndexOf(".")) : ".tmp";
                tempFile = Files.createTempFile("oqteris-update-", suffix);
                tempFile.toFile().deleteOnExit();

                final Path finalTempFile = tempFile;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        Files.deleteIfExists(finalTempFile);
                    } catch (Exception ignored) {}
                }));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(asset.downloadUrl()))
                        .header("Accept", "application/octet-stream")
                        .header("User-Agent", "Oqteris-PDF-Editor/" + AppVersion.CURRENT_VERSION)
                        .timeout(Duration.ofMinutes(10))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = getClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("HTTP " + response.statusCode() + " downloading update package");
                }

                long totalBytes = asset.sizeBytes();
                if (totalBytes <= 0) {
                    totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                }

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(tempFile)) {
                    byte[] buffer = new byte[16384];
                    long bytesRead = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        bytesRead += read;
                        if (totalBytes > 0 && progressCallback != null) {
                            double progress = (double) bytesRead / totalBytes;
                            Platform.runLater(() -> progressCallback.accept(progress));
                        }
                    }
                }

                if (progressCallback != null) {
                    Platform.runLater(() -> progressCallback.accept(1.0));
                }
                return tempFile;
            } catch (Exception e) {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
                }
                throw new RuntimeException("Failed to download update package: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Executes the platform-specific installer and ensures the downloaded package is deleted after execution.
     */
    public void launchInstallerAndCleanup(Path packagePath, TargetPlatform platform) throws Exception {
        if (packagePath == null || !Files.exists(packagePath)) {
            return;
        }

        String absPath = packagePath.toAbsolutePath().toString();
        String lowerName = packagePath.getFileName().toString().toLowerCase(Locale.ROOT);

        if (platform == TargetPlatform.WINDOWS) {
            if (lowerName.endsWith(".msi")) {
                // Launch MSI and delete package file when MSI finishes
                String cmd = String.format("start /wait msiexec /i \"%s\" & del /f /q \"%s\"", absPath, absPath);
                new ProcessBuilder("cmd.exe", "/c", cmd).start();
                Platform.exit();
            } else if (lowerName.endsWith(".exe")) {
                // Launch EXE and delete package file when installer process exits
                String cmd = String.format("start /wait \"\" \"%s\" & del /f /q \"%s\"", absPath, absPath);
                new ProcessBuilder("cmd.exe", "/c", cmd).start();
                Platform.exit();
            } else {
                // Zip/other: open file and delete on exit
                new ProcessBuilder("explorer.exe", "/select,", absPath).start();
            }
        } else if (platform == TargetPlatform.MACOS) {
            if (lowerName.endsWith(".dmg")) {
                new ProcessBuilder("open", absPath).start();
            } else if (lowerName.endsWith(".pkg")) {
                new ProcessBuilder("open", absPath).start();
            } else {
                new ProcessBuilder("open", packagePath.getParent().toAbsolutePath().toString()).start();
            }
        } else if (platform == TargetPlatform.LINUX) {
            if (lowerName.endsWith(".deb")) {
                String cmd = String.format("xdg-open \"%s\" || (sudo dpkg -i \"%s\" && rm -f \"%s\")", absPath, absPath, absPath);
                new ProcessBuilder("sh", "-c", cmd).start();
            } else if (lowerName.endsWith(".appimage")) {
                new ProcessBuilder("chmod", "+x", absPath).start().waitFor();
                new ProcessBuilder(absPath).start();
            } else {
                new ProcessBuilder("xdg-open", packagePath.getParent().toAbsolutePath().toString()).start();
            }
        }
    }

    /**
     * Triggers an asynchronous check and displays the appropriate dialog.
     */
    public void checkForUpdatesAsync(Stage ownerStage, boolean showUpToDateNotice) {
        checkLatestRelease().thenAccept(result -> Platform.runLater(() -> {
            switch (result.status()) {
                case UPDATE_AVAILABLE -> {
                    if (result.release() != null) {
                        UpdateDialog dialog = new UpdateDialog(ownerStage, result.release());
                        dialog.show();
                    }
                }
                case UP_TO_DATE, NO_RELEASES_PUBLISHED -> {
                    if (showUpToDateNotice) {
                        showWrappedAlert(Alert.AlertType.INFORMATION, ownerStage,
                                "Check for Updates - Oqteris",
                                "Oqteris is Up to Date",
                                "You are using the latest version of Oqteris (v" + AppVersion.CURRENT_VERSION + ").\n" +
                                        "No newer releases have been published yet on GitHub.");
                    }
                }
                case RATE_LIMITED -> {
                    showWrappedAlert(Alert.AlertType.WARNING, ownerStage,
                            "Check for Updates - Oqteris",
                            "GitHub Rate Limit Exceeded",
                            "The GitHub API request limit has been reached for anonymous requests.\n" +
                                    "Please try again later, or visit the repository directly:\n" +
                                    AppVersion.GITHUB_REPO_URL);
                }
                case SERVER_ERROR -> {
                    showWrappedAlert(Alert.AlertType.ERROR, ownerStage,
                            "Check for Updates - Oqteris",
                            "GitHub Service Unavailable",
                            "GitHub servers encountered an error (" + (result.errorMessage() != null ? result.errorMessage() : "HTTP 5xx") + ").\n" +
                                    "Please check back shortly or visit:\n" + AppVersion.GITHUB_REPO_URL);
                }
                case NETWORK_ERROR -> {
                    showWrappedAlert(Alert.AlertType.ERROR, ownerStage,
                            "Check for Updates - Oqteris",
                            "Unable to Check for Updates",
                            "Could not connect to GitHub to check for updates.\n" +
                                    "Please verify your internet connection or visit:\n" + AppVersion.GITHUB_REPO_URL);
                }
            }
        }));
    }

    private void showWrappedAlert(Alert.AlertType type, Stage ownerStage, String title, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);

        Label contentLabel = new Label(message);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(480);
        contentLabel.setStyle("-fx-font-size: 12px; -fx-line-spacing: 3px;");

        Hyperlink repoLink = new Hyperlink(AppVersion.GITHUB_REPO_URL);
        repoLink.setWrapText(true);
        repoLink.setMaxWidth(480);
        repoLink.setStyle("-fx-font-size: 11px;");
        repoLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(AppVersion.GITHUB_REPO_URL));
            } catch (Exception ignored) {}
        });

        VBox contentBox = new VBox(8, contentLabel, repoLink);
        contentBox.setPrefWidth(480);
        alert.getDialogPane().setContent(contentBox);

        if (ownerStage != null) {
            alert.initOwner(ownerStage);
        }
        alert.showAndWait();
    }

    /**
     * Parses GitHub Release JSON into a ReleaseInfo model without external JSON dependencies.
     */
    public static ReleaseInfo parseReleaseJson(String json) {
        if (json == null || json.isBlank()) return null;

        String tagName = extractJsonString(json, "tag_name");
        String name = extractJsonString(json, "name");
        String body = extractJsonString(json, "body");
        String htmlUrl = extractJsonString(json, "html_url");
        String publishedAt = extractJsonString(json, "published_at");

        List<ReleaseAsset> assets = new ArrayList<>();
        int assetsIndex = json.indexOf("\"assets\"");
        if (assetsIndex != -1) {
            int openBracket = json.indexOf('[', assetsIndex);
            int closeBracket = findMatchingBracket(json, openBracket);
            if (openBracket != -1 && closeBracket > openBracket) {
                String assetsArray = json.substring(openBracket + 1, closeBracket);
                List<String> assetObjects = splitJsonObjects(assetsArray);
                for (String obj : assetObjects) {
                    String aName = extractJsonString(obj, "name");
                    String downloadUrl = extractJsonString(obj, "browser_download_url");
                    long size = extractJsonLong(obj, "size");
                    String contentType = extractJsonString(obj, "content_type");
                    if (aName != null && downloadUrl != null) {
                        assets.add(new ReleaseAsset(aName, downloadUrl, size, contentType));
                    }
                }
            }
        }

        return new ReleaseInfo(tagName, name, body, htmlUrl, publishedAt, assets);
    }

    private static String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return "";
    }

    private static long extractJsonLong(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    private static int findMatchingBracket(String json, int openPos) {
        if (openPos < 0) return -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitJsonObjects(String arrayContent) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        result.add(arrayContent.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return result;
    }

    private static String unescapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 < input.length()) {
                            try {
                                String hex = input.substring(i + 1, i + 5);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (Exception e) {
                                sb.append("\\u");
                            }
                        } else {
                            sb.append("\\u");
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
