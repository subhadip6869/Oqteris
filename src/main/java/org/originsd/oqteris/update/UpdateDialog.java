package org.originsd.oqteris.update;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignO;
import org.originsd.oqteris.ui.ThemeManager;
import org.originsd.oqteris.ui.icon.AppIcons;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/**
 * Modern modal dialog presenting release version details, changelog notes,
 * platform-specific package options, download progress, and automatic package deletion.
 * Fully wraps long text and URLs to prevent ellipsis truncation.
 */
public class UpdateDialog extends Stage {

    private final ReleaseInfo release;
    private final TargetPlatform platform;
    private final ReleaseAsset selectedAsset;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressLabel = new Label("");
    private final Label statusLabel = new Label("");
    private final Button downloadBtn;
    private final Button browserBtn;
    private final Button closeBtn;

    public UpdateDialog(Stage owner, ReleaseInfo release) {
        this.release = release;
        this.platform = TargetPlatform.current();
        this.selectedAsset = release.findBestAssetForPlatform(platform);

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Software Update Available - Oqteris");
        setResizable(false);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setPrefWidth(540);

        // Header
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label();
        iconLabel.setGraphic(AppIcons.of(MaterialDesignD.DOWNLOAD_BOX_OUTLINE, 36, javafx.scene.paint.Color.rgb(2, 132, 199)));

        VBox titleBox = new VBox(2);
        Label title = new Label("New Oqteris Version Available!");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0284c7;");

        Label subtitle = new Label("Current version: v" + AppVersion.CURRENT_VERSION + "  →  New version: " + release.tagName());
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #334155;");

        titleBox.getChildren().addAll(title, subtitle);
        header.getChildren().addAll(iconLabel, titleBox);

        // Platform & Asset Info Box
        VBox packageBox = new VBox(5);
        packageBox.setStyle("-fx-background-color: #f0f9ff; -fx-border-color: #bae6fd; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10px 12px;");

        Label platformLabel = new Label("Detected OS: " + platform.getDisplayName());
        platformLabel.setWrapText(true);
        platformLabel.setMaxWidth(Double.MAX_VALUE);
        platformLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0369a1;");

        String assetDetails = selectedAsset != null
                ? "Target Package: " + selectedAsset.name() + (selectedAsset.sizeBytes() > 0 ? " (" + selectedAsset.getFormattedSize() + ")" : "")
                : "No platform package attached directly. Full release bundle available on GitHub.";
        Label assetLabel = new Label(assetDetails);
        assetLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
        assetLabel.setWrapText(true);
        assetLabel.setMaxWidth(Double.MAX_VALUE);

        Label cleanupNotice = new Label("Automatic Clean-up: The downloaded update package will be automatically deleted after execution.");
        cleanupNotice.setStyle("-fx-font-size: 10.5px; -fx-font-style: italic; -fx-text-fill: #059669;");
        cleanupNotice.setWrapText(true);
        cleanupNotice.setMaxWidth(Double.MAX_VALUE);

        String visitUrl = release.htmlUrl() != null && !release.htmlUrl().isBlank()
                ? release.htmlUrl()
                : AppVersion.GITHUB_REPO_URL;

        Hyperlink visitLink = new Hyperlink("Release page: " + visitUrl);
        visitLink.setWrapText(true);
        visitLink.setMaxWidth(Double.MAX_VALUE);
        visitLink.setStyle("-fx-font-size: 11px; -fx-padding: 0;");
        visitLink.setOnAction(e -> openBrowser(visitUrl));

        packageBox.getChildren().addAll(platformLabel, assetLabel, cleanupNotice, visitLink);

        // Release Notes
        Label notesHeader = new Label("Release Notes & Changelog:");
        notesHeader.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600;");

        TextArea notesArea = new TextArea();
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(7);
        String body = release.body();
        notesArea.setText(body != null && !body.isBlank() ? body : "No release notes provided for this version.");

        // Progress Controls (hidden initially)
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        progressLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #0284c7; -fx-font-weight: bold;");
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
        progressLabel.setWrapText(true);

        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        VBox progressBox = new VBox(4, progressBar, new HBox(10, statusLabel, progressLabel));
        progressBox.setAlignment(Pos.CENTER_LEFT);

        // Buttons
        downloadBtn = new Button(selectedAsset != null ? "Download & Install" : "Download Update");
        downloadBtn.setGraphic(AppIcons.of(MaterialDesignD.DOWNLOAD, 16));
        downloadBtn.setDefaultButton(true);
        downloadBtn.setOnAction(e -> startDownload());

        browserBtn = new Button("View on GitHub");
        browserBtn.setGraphic(AppIcons.of(MaterialDesignO.OPEN_IN_NEW, 14));
        browserBtn.setOnAction(e -> openBrowser(visitUrl));

        closeBtn = new Button("Remind Me Later");
        closeBtn.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBar = new HBox(8, browserBtn, spacer, closeBtn, downloadBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
                header,
                packageBox,
                notesHeader,
                notesArea,
                progressBox,
                new Separator(),
                buttonBar
        );

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
        sizeToScene();
    }

    private void startDownload() {
        if (selectedAsset == null) {
            openBrowser(release.htmlUrl());
            close();
            return;
        }

        downloadBtn.setDisable(true);
        browserBtn.setDisable(true);
        closeBtn.setText("Cancel");

        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressLabel.setVisible(true);
        progressLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        statusLabel.setText("Connecting to GitHub asset CDN...");
        progressLabel.setText("0%");

        UpdateCheckerService.getInstance().downloadAsset(selectedAsset, progress -> {
            progressBar.setProgress(progress);
            int percent = (int) Math.round(progress * 100);
            progressLabel.setText(percent + "%");
            statusLabel.setText("Downloading " + selectedAsset.name() + "...");
        }).thenAccept(downloadedFile -> javafx.application.Platform.runLater(() -> {
            statusLabel.setText("Download completed. Starting installer...");
            progressBar.setProgress(1.0);
            progressLabel.setText("100%");
            try {
                UpdateCheckerService.getInstance().launchInstallerAndCleanup(downloadedFile, platform);
            } catch (Exception ex) {
                Alert errAlert = new Alert(Alert.AlertType.ERROR);
                errAlert.setTitle("Update Execution Error");
                errAlert.setHeaderText("Failed to launch updater");
                errAlert.setContentText("Could not launch package: " + ex.getMessage());
                errAlert.showAndWait();
            }
        })).exceptionally(ex -> {
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText("Download failed: " + ex.getMessage());
                downloadBtn.setDisable(false);
                browserBtn.setDisable(false);
                closeBtn.setText("Close");
            });
            return null;
        });
    }

    private void openBrowser(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {}
    }
}
