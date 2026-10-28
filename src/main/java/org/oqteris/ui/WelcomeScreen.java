package org.oqteris.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.oqteris.core.model.DocumentType;
import org.oqteris.core.recovery.RecoveryManager;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern Start Center / Welcome Screen for Oqteris Office Suite.
 */
public class WelcomeScreen extends BorderPane {
    private final Consumer<DocumentType> onNewDocument;
    private final Runnable onOpenFile;
    private final Consumer<File> onOpenSpecificFile;
    private final VBox recentFilesBox = new VBox(6);
    private final VBox recoveryAlertBox = new VBox(8);
    private final TextField recentSearchField = new TextField();

    public WelcomeScreen(Consumer<DocumentType> onNewDocument, Runnable onOpenFile, Consumer<File> onOpenSpecificFile) {
        this.onNewDocument = onNewDocument;
        this.onOpenFile = onOpenFile;
        this.onOpenSpecificFile = onOpenSpecificFile;

        getStyleClass().add("welcome-screen");
        setPadding(new Insets(32, 48, 32, 48));

        buildUI();
    }

    private void buildUI() {
        // Crash recovery check
        checkRecovery();

        // 1. Top Hero Section
        HBox heroHeader = new HBox(16);
        heroHeader.setAlignment(Pos.CENTER_LEFT);
        heroHeader.setPadding(new Insets(0, 0, 20, 0));

        ImageView logoView = new ImageView();
        try (InputStream in = getClass().getResourceAsStream("/icons/oqteris-logo.png")) {
            if (in != null) {
                logoView.setImage(new Image(in, 60, 60, true, true));
            }
        } catch (Exception ignored) {
        }

        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Oqteris Office Suite");
        title.getStyleClass().add("welcome-title");

        Label versionBadge = new Label("v1.0.0 LTS");
        versionBadge.setStyle("-fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px;");

        titleRow.getChildren().addAll(title, versionBadge);

        Label subtitle = new Label("Independent Office Suite: Word Processor, Spreadsheet, Presentation & PDF Trust Center");
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setStyle("-fx-text-fill: #64748b;");

        titleBox.getChildren().addAll(titleRow, subtitle);
        heroHeader.getChildren().addAll(logoView, titleBox);

        // 2. Main Two-Column Content Area
        HBox columnsContainer = new HBox(32);
        columnsContainer.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(columnsContainer, Priority.ALWAYS);

        // Left Column: Create New & Launch Cards
        VBox leftColumn = new VBox(14);
        leftColumn.setPrefWidth(320);

        Label startLabel = new Label("New Document");
        startLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        VBox cardsBox = new VBox(10);
        cardsBox.getChildren().addAll(
                createActionCard("Text Document", "Word Processor (DOCX, RTF, ODT)", "W", "#2563eb", () -> onNewDocument.accept(DocumentType.WRITER)),
                createActionCard("Spreadsheet", "Workbook & Formulas (XLSX, XLS)", "S", "#059669", () -> onNewDocument.accept(DocumentType.CALC)),
                createActionCard("Presentation", "Slide Designer (PPTX, ODP)", "P", "#ea580c", () -> onNewDocument.accept(DocumentType.IMPRESS)),
                createActionCard("Open File...", "Browse DOCX, XLSX, PPTX, PDF", "📂", "#7c3aed", onOpenFile)
        );

        leftColumn.getChildren().addAll(startLabel, cardsBox);

        // Right Column: Recent Documents
        VBox rightColumn = new VBox(10);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        HBox recentsHeader = new HBox(12);
        recentsHeader.setAlignment(Pos.CENTER_LEFT);

        Label recentLabel = new Label("Recent Documents");
        recentLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        recentSearchField.setPromptText("Filter recent files...");
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

        ScrollPane recentScroll = new ScrollPane(recentFilesBox);
        recentScroll.setFitToWidth(true);
        recentScroll.setPrefHeight(380);
        recentScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(recentScroll, Priority.ALWAYS);

        refreshRecentFiles();

        rightColumn.getChildren().addAll(recentsHeader, recentScroll);

        columnsContainer.getChildren().addAll(leftColumn, rightColumn);

        VBox centerContainer = new VBox(16, recoveryAlertBox, columnsContainer);
        VBox.setVgrow(centerContainer, Priority.ALWAYS);

        setTop(heroHeader);
        setCenter(centerContainer);
    }

    private HBox createActionCard(String title, String desc, String badgeChar, String accentColor, Runnable onClick) {
        HBox card = new HBox(14);
        card.getStyleClass().add("action-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));

        // Circular or rounded icon badge
        StackPane iconBadge = new StackPane();
        iconBadge.setPrefSize(40, 40);
        iconBadge.setMinSize(40, 40);
        iconBadge.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 8px;");

        Label badgeLabel = new Label(badgeChar);
        badgeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        iconBadge.getChildren().add(badgeLabel);

        VBox textCol = new VBox(2);
        Label cardTitle = new Label(title);
        cardTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + accentColor + ";");

        Label cardDesc = new Label(desc);
        cardDesc.setWrapText(true);
        cardDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

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
        banner.setStyle("-fx-background-color: #fef3c7; -fx-border-color: #fde68a; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label warning = new Label("Document Recovery: Oqteris detected " + records.size() + " unsaved document(s) from a previous session.");
        warning.setStyle("-fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 12px;");

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

        HBox spacer = new HBox();
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
            Label empty = new Label("No recent documents. Create a new document or open an existing file.");
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
            row.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px; -fx-cursor: hand;");
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #eef2ff; -fx-background-radius: 8px; -fx-border-color: #c7d2fe; -fx-border-radius: 8px; -fx-cursor: hand;"));
            row.setOnMouseExited(e -> row.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px; -fx-cursor: hand;"));

            Label badge = new Label(entry.getType().name());
            badge.setStyle("-fx-padding: 2px 7px; -fx-background-radius: 4px; -fx-font-size: 10px; -fx-font-weight: bold; " + getBadgeStyle(entry.getType()));

            VBox fileInfo = new VBox(2);
            Label fileName = new Label(entry.getFileName());
            fileName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

            Label filePath = new Label(entry.getAbsolutePath());
            filePath.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            fileInfo.getChildren().addAll(fileName, filePath);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label timeLbl = new Label(sdf.format(new Date(entry.getLastAccessed())));
            timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

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

    private String getBadgeStyle(DocumentType type) {
        return switch (type) {
            case WRITER -> "-fx-background-color: #2563eb; -fx-text-fill: white;";
            case CALC -> "-fx-background-color: #059669; -fx-text-fill: white;";
            case IMPRESS -> "-fx-background-color: #ea580c; -fx-text-fill: white;";
            case PDF -> "-fx-background-color: #dc2626; -fx-text-fill: white;";
        };
    }
}
