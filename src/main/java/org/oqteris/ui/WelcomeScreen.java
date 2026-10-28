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
 * Start Center / Welcome screen displayed when no documents are active.
 */
public class WelcomeScreen extends BorderPane {
    private final Consumer<DocumentType> onNewDocument;
    private final Runnable onOpenFile;
    private final Consumer<File> onOpenSpecificFile;
    private final VBox recentFilesBox = new VBox(6);
    private final VBox recoveryAlertBox = new VBox(8);

    public WelcomeScreen(Consumer<DocumentType> onNewDocument, Runnable onOpenFile, Consumer<File> onOpenSpecificFile) {
        this.onNewDocument = onNewDocument;
        this.onOpenFile = onOpenFile;
        this.onOpenSpecificFile = onOpenSpecificFile;

        getStyleClass().add("welcome-screen");
        setPadding(new Insets(30, 40, 30, 40));

        buildUI();
    }

    private void buildUI() {
        // Top Header
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        ImageView logoView = new ImageView();
        try (InputStream in = getClass().getResourceAsStream("/icons/oqteris-logo.png")) {
            if (in != null) {
                logoView.setImage(new Image(in, 56, 56, true, true));
            }
        } catch (Exception ignored) {
        }

        VBox titleBox = new VBox(2);
        Label title = new Label("Welcome to Oqteris");
        title.getStyleClass().add("welcome-title");
        Label subtitle = new Label("Independent Office Suite: Word Processor, Spreadsheet, Presentation & PDF");
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setStyle("-fx-text-fill: #777777;");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(logoView, titleBox);

        // Center Content
        VBox center = new VBox(24);
        center.setPadding(new Insets(20, 0, 0, 0));

        // Crash recovery banner if files exist
        checkRecovery();

        // Action Cards Grid
        Label startLabel = new Label("Create or Open");
        startLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        HBox cardsBox = new HBox(16);
        cardsBox.setAlignment(Pos.CENTER_LEFT);

        cardsBox.getChildren().addAll(
                createActionCard("Document", "Word Processor (DOCX)", "#0078d4", () -> onNewDocument.accept(DocumentType.WRITER)),
                createActionCard("Spreadsheet", "Workbook (XLSX)", "#107c41", () -> onNewDocument.accept(DocumentType.CALC)),
                createActionCard("Presentation", "Slide Deck (PPTX)", "#d83b01", () -> onNewDocument.accept(DocumentType.IMPRESS)),
                createActionCard("Open File", "DOCX, XLSX, PPTX, PDF", "#6a1b9a", onOpenFile)
        );

        // Recent Documents Section
        Label recentLabel = new Label("Recent Documents");
        recentLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        ScrollPane recentScroll = new ScrollPane(recentFilesBox);
        recentScroll.setFitToWidth(true);
        recentScroll.setPrefHeight(260);
        recentScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        refreshRecentFiles();

        center.getChildren().addAll(recoveryAlertBox, startLabel, cardsBox, recentLabel, recentScroll);

        setTop(header);
        setCenter(center);
    }

    private VBox createActionCard(String title, String desc, String accentColor, Runnable onClick) {
        VBox card = new VBox(6);
        card.getStyleClass().add("action-card");
        card.setPrefWidth(170);
        card.setPrefHeight(90);
        card.setStyle("-fx-border-left-color: " + accentColor + "; -fx-border-left-width: 4px;");

        Label cardTitle = new Label(title);
        cardTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        Label cardDesc = new Label(desc);
        cardDesc.setWrapText(true);
        cardDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #777777;");

        card.getChildren().addAll(cardTitle, cardDesc);
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
        banner.setPadding(new Insets(10, 14, 10, 14));
        banner.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #ffeeba; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        Label warning = new Label("Document Recovery: Oqteris detected " + records.size() + " unsaved document(s) from a previous session.");
        warning.setStyle("-fx-text-fill: #856404; -fx-font-weight: bold; -fx-font-size: 12px;");

        Button recoverBtn = new Button("Recover All");
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
        recentFilesBox.getChildren().clear();
        List<RecentFilesManager.RecentFileEntry> recents = RecentFilesManager.getInstance().getRecentFiles();

        if (recents.isEmpty()) {
            Label empty = new Label("No recent documents. Create a new document or open an existing file.");
            empty.setStyle("-fx-text-fill: #888888; -fx-padding: 8px;");
            recentFilesBox.getChildren().add(empty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm");
        for (RecentFilesManager.RecentFileEntry entry : recents) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setStyle("-fx-background-radius: 4px; -fx-cursor: hand;");
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: rgba(0,120,212,0.08); -fx-background-radius: 4px; -fx-cursor: hand;"));
            row.setOnMouseExited(e -> row.setStyle("-fx-background-radius: 4px; -fx-cursor: hand;"));

            Label badge = new Label(entry.getType().name());
            badge.setStyle("-fx-padding: 2px 6px; -fx-background-radius: 3px; -fx-font-size: 10px; -fx-font-weight: bold; " + getBadgeStyle(entry.getType()));

            VBox fileInfo = new VBox(2);
            Label fileName = new Label(entry.getFileName());
            fileName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            Label filePath = new Label(entry.getAbsolutePath());
            filePath.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
            fileInfo.getChildren().addAll(fileName, filePath);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label timeLbl = new Label(sdf.format(new Date(entry.getLastAccessed())));
            timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #999999;");

            row.getChildren().addAll(badge, fileInfo, spacer, timeLbl);
            row.setOnMouseClicked(e -> {
                if (onOpenSpecificFile != null) {
                    onOpenSpecificFile.accept(entry.getFile());
                }
            });

            recentFilesBox.getChildren().add(row);
        }
    }

    private String getBadgeStyle(DocumentType type) {
        return switch (type) {
            case WRITER -> "-fx-background-color: #0078d4; -fx-text-fill: white;";
            case CALC -> "-fx-background-color: #107c41; -fx-text-fill: white;";
            case IMPRESS -> "-fx-background-color: #d83b01; -fx-text-fill: white;";
            case PDF -> "-fx-background-color: #6a1b9a; -fx-text-fill: white;";
        };
    }
}
