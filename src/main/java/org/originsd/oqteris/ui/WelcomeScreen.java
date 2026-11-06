package org.originsd.oqteris.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.core.recovery.RecoveryManager;
import org.originsd.oqteris.platform.DefaultViewerService;
import org.originsd.oqteris.platform.DefaultViewerServiceFactory;
import org.originsd.oqteris.platform.DefaultViewerStatus;
import org.originsd.oqteris.platform.RegistrationResult;
import org.originsd.oqteris.ui.icon.AppResources;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern Start Center / Welcome Screen for Oqteris PDF Reader and Editor.
 * Focused exclusively on PDF workflows, recent files, security trust management, and OS integration.
 */
public class WelcomeScreen extends BorderPane {
    private final Runnable onOpenFile;
    private final Consumer<File> onOpenSpecificFile;
    private final Runnable onOpenSettings;
    private final Runnable onOpenCertManager;

    private final VBox recentFilesBox = new VBox(6);
    private final VBox recoveryAlertBox = new VBox(8);
    private final VBox defaultViewerAlertBox = new VBox(8);
    private final TextField recentSearchField = new TextField();

    public WelcomeScreen(Runnable onOpenFile, Consumer<File> onOpenSpecificFile,
                         Runnable onOpenSettings, Runnable onOpenCertManager) {
        this.onOpenFile = onOpenFile;
        this.onOpenSpecificFile = onOpenSpecificFile;
        this.onOpenSettings = onOpenSettings;
        this.onOpenCertManager = onOpenCertManager;

        getStyleClass().add("welcome-screen");
        setPadding(new Insets(28, 48, 28, 48));

        buildUI();
    }

    private void buildUI() {
        // Crash recovery check
        checkRecovery();

        // 1. Top Hero Section
        HBox heroHeader = new HBox(16);
        heroHeader.setAlignment(Pos.CENTER_LEFT);
        heroHeader.setPadding(new Insets(0, 0, 16, 0));

        ImageView logoView = new ImageView();
        Image logo = AppResources.getAppLogo(60, 60);
        if (logo != null) {
            logoView.setImage(logo);
        }

        VBox titleBox = new VBox(3);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Oqteris");
        title.getStyleClass().add("welcome-title");

        Label versionBadge = new Label("v1.0.0 LTS");
        versionBadge.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px;");

        titleRow.getChildren().addAll(title, versionBadge);

        Label subtitle = new Label("Dedicated PDF Reader and Editor – Viewing, Annotation & Digital Signatures");
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setStyle("-fx-text-fill: #64748b;");

        titleBox.getChildren().addAll(titleRow, subtitle);
        heroHeader.getChildren().addAll(logoView, titleBox);

        // Check default PDF viewer status banner
        checkDefaultViewerStatus();

        // 2. Main Two-Column Content Area
        HBox columnsContainer = new HBox(32);
        columnsContainer.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(columnsContainer, Priority.ALWAYS);

        // Left Column: PDF Workflow Entry Points
        VBox leftColumn = new VBox(14);
        leftColumn.setPrefWidth(320);

        Label startLabel = new Label("Actions");
        startLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        VBox cardsBox = new VBox(10);
        cardsBox.getChildren().addAll(
                createActionCard("Open PDF Document...", "Browse your system to view or edit a PDF", "📄", "#2563eb", onOpenFile),
                createActionCard("Security & Trust Store", "Manage certificates and digital signature trust levels", "🛡️", "#059669", onOpenCertManager),
                createActionCard("Application Settings", "Configure default author, theme, and default viewer", "⚙️", "#6366f1", onOpenSettings)
        );

        // Drag & drop drop-zone indicator
        VBox dropZone = new VBox(6);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPadding(new Insets(20, 16, 20, 16));
        dropZone.getStyleClass().add("welcome-drop-zone");
        dropZone.setCursor(javafx.scene.Cursor.HAND);
        dropZone.setOnMouseClicked(e -> {
            if (onOpenFile != null) onOpenFile.run();
        });

        Label dropIcon = new Label("📂");
        dropIcon.setStyle("-fx-font-size: 24px;");
        Label dropText = new Label("Drag & drop a PDF here");
        dropText.getStyleClass().add("welcome-drop-title");
        Label dropSub = new Label("or click to browse files");
        dropSub.getStyleClass().add("welcome-drop-sub");

        dropZone.getChildren().addAll(dropIcon, dropText, dropSub);

        leftColumn.getChildren().addAll(startLabel, cardsBox, dropZone);

        // Right Column: Recent PDF Documents
        VBox rightColumn = new VBox(10);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        HBox recentsHeader = new HBox(12);
        recentsHeader.setAlignment(Pos.CENTER_LEFT);

        Label recentLabel = new Label("Recent PDF Documents");
        recentLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        recentSearchField.setPromptText("Filter recent PDFs...");
        recentSearchField.setPrefWidth(200);
        recentSearchField.textProperty().addListener((obs, oldV, newV) -> filterRecentFiles(newV));

        Region recentSpacer = new Region();
        HBox.setHgrow(recentSpacer, Priority.ALWAYS);

        Button clearRecentBtn = new Button("Clear History");
        clearRecentBtn.setStyle("-fx-font-size: 11px;");
        clearRecentBtn.setOnAction(e -> {
            RecentFilesManager.getInstance().clear();
            refreshRecentFiles();
        });

        recentsHeader.getChildren().addAll(recentLabel, recentSpacer, recentSearchField, clearRecentBtn);

        recentFilesBox.getStyleClass().add("recent-files-box");
        ScrollPane recentScroll = new ScrollPane(recentFilesBox);
        recentScroll.getStyleClass().add("recent-scroll-pane");
        recentScroll.setFitToWidth(true);
        recentScroll.setPrefHeight(380);
        VBox.setVgrow(recentScroll, Priority.ALWAYS);

        refreshRecentFiles();

        rightColumn.getChildren().addAll(recentsHeader, recentScroll);

        columnsContainer.getChildren().addAll(leftColumn, rightColumn);

        VBox centerContainer = new VBox(14, defaultViewerAlertBox, recoveryAlertBox, columnsContainer);
        VBox.setVgrow(centerContainer, Priority.ALWAYS);

        setTop(heroHeader);
        setCenter(centerContainer);
    }

    private void checkDefaultViewerStatus() {
        defaultViewerAlertBox.getChildren().clear();
        DefaultViewerService service = DefaultViewerServiceFactory.getInstance();
        DefaultViewerStatus status = service.checkStatus();

        if (status == DefaultViewerStatus.NOT_DEFAULT) {
            HBox banner = new HBox(12);
            banner.setAlignment(Pos.CENTER_LEFT);
            banner.setPadding(new Insets(8, 14, 8, 14));
            banner.getStyleClass().add("default-viewer-banner");

            Label msg = new Label("Oqteris is not currently your default PDF viewer.");
            msg.getStyleClass().add("default-viewer-msg");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button setDefaultBtn = new Button("Set as Default");
            setDefaultBtn.getStyleClass().add("default-viewer-btn");
            setDefaultBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            setDefaultBtn.setOnAction(e -> {
                RegistrationResult res = service.registerAsDefault();
                if (res.isGuidanceRequired()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, res.getMessage() + "\n\n" + res.getInstructions(), ButtonType.OK);
                    alert.setHeaderText("Set Default PDF Viewer");
                    alert.showAndWait();
                } else if (res.isSuccess()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, res.getMessage(), ButtonType.OK);
                    alert.setHeaderText("Default PDF Viewer Configured");
                    alert.showAndWait();
                }
                checkDefaultViewerStatus();
            });

            Button dismissBtn = new Button("Dismiss");
            dismissBtn.setStyle("-fx-font-size: 11px;");
            dismissBtn.setOnAction(e -> defaultViewerAlertBox.getChildren().clear());

            banner.getChildren().addAll(msg, spacer, setDefaultBtn, dismissBtn);
            defaultViewerAlertBox.getChildren().add(banner);
        }
    }

    private HBox createActionCard(String title, String desc, String badgeChar, String accentColor, Runnable onClick) {
        HBox card = new HBox(14);
        card.getStyleClass().add("action-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setCursor(javafx.scene.Cursor.HAND);

        StackPane iconBadge = new StackPane();
        iconBadge.setPrefSize(40, 40);
        iconBadge.setMinSize(40, 40);
        iconBadge.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 8px;");

        Label badgeLabel = new Label(badgeChar);
        badgeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        iconBadge.getChildren().add(badgeLabel);

        VBox textCol = new VBox(2);
        Label cardTitle = new Label(title);
        cardTitle.getStyleClass().add("action-card-title");
        cardTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + accentColor + ";");

        Label cardDesc = new Label(desc);
        cardDesc.setWrapText(true);
        cardDesc.getStyleClass().add("action-card-desc");

        textCol.getChildren().addAll(cardTitle, cardDesc);
        card.getChildren().addAll(iconBadge, textCol);

        card.setOnMouseClicked(e -> {
            if (onClick != null) onClick.run();
        });

        return card;
    }

    public void checkRecovery() {
        recoveryAlertBox.getChildren().clear();
        List<RecoveryManager.RecoveryRecord> records = RecoveryManager.findRecoverableDocuments();
        if (records.isEmpty()) return;

        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10, 16, 10, 16));
        banner.getStyleClass().add("recovery-banner");

        Label warning = new Label("Document Recovery: Oqteris detected " + records.size() + " unsaved document(s) from a previous session.");
        warning.getStyleClass().add("recovery-msg");

        Button recoverBtn = new Button("Recover All");
        recoverBtn.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold;");
        recoverBtn.setOnAction(e -> {
            for (RecoveryManager.RecoveryRecord r : records) {
                if (!r.getOriginalPath().equals("none")) {
                    File f = new File(r.getOriginalPath());
                    if (f.exists() && onOpenSpecificFile != null) {
                        onOpenSpecificFile.accept(f);
                    }
                }
                r.discard();
            }
            recoveryAlertBox.getChildren().clear();
        });

        Button discardBtn = new Button("Discard");
        discardBtn.setOnAction(e -> {
            for (RecoveryManager.RecoveryRecord r : records) {
                r.discard();
            }
            recoveryAlertBox.getChildren().clear();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        banner.getChildren().addAll(warning, spacer, recoverBtn, discardBtn);
        recoveryAlertBox.getChildren().add(banner);
    }

    public void refreshRecentFiles() {
        filterRecentFiles(recentSearchField.getText());
    }

    private void filterRecentFiles(String query) {
        recentFilesBox.getChildren().clear();
        List<RecentFilesManager.RecentFileEntry> recents = RecentFilesManager.getInstance().getRecentFiles();

        if (recents.isEmpty()) {
            Label empty = new Label("No recent documents. Open an existing PDF to get started.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 16px; -fx-alignment: center;");
            recentFilesBox.getChildren().add(empty);
            return;
        }

        String filter = query != null ? query.trim().toLowerCase() : "";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm");
        int count = 0;

        for (RecentFilesManager.RecentFileEntry entry : recents) {
            if (!filter.isEmpty() && !entry.getFileName().toLowerCase().contains(filter) && !entry.getAbsolutePath().toLowerCase().contains(filter)) {
                continue;
            }
            count++;

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 14, 10, 14));
            row.getStyleClass().add("recent-file-row");
            row.setCursor(javafx.scene.Cursor.HAND);

            Label badge = new Label("PDF");
            badge.getStyleClass().add("recent-file-badge");
            badge.setStyle("-fx-padding: 2px 7px; -fx-background-radius: 4px; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-color: #dc2626; -fx-text-fill: white;");

            VBox fileInfo = new VBox(2);
            Label fileName = new Label(entry.getFileName());
            fileName.getStyleClass().add("recent-file-name");

            Label filePath = new Label(entry.getAbsolutePath());
            filePath.getStyleClass().add("recent-file-path");
            fileInfo.getChildren().addAll(fileName, filePath);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label timeLbl = new Label(sdf.format(new Date(entry.getLastAccessed())));
            timeLbl.getStyleClass().add("recent-file-time");

            row.getChildren().addAll(badge, fileInfo, spacer, timeLbl);
            row.setOnMouseClicked(e -> {
                if (onOpenSpecificFile != null) {
                    onOpenSpecificFile.accept(entry.getFile());
                }
            });

            recentFilesBox.getChildren().add(row);
        }

        if (count == 0) {
            Label noMatch = new Label("No recent documents matching '" + filter + "'");
            noMatch.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 16px;");
            recentFilesBox.getChildren().add(noMatch);
        }
    }
}
