package app.vercel.originsd.oqteris.pdf.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import app.vercel.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import app.vercel.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import app.vercel.originsd.oqteris.crypto.trust.CertificateDetails;
import app.vercel.originsd.oqteris.crypto.trust.CertificateManagerDialog;
import app.vercel.originsd.oqteris.crypto.trust.TrustLevel;
import app.vercel.originsd.oqteris.crypto.trust.TrustStoreManager;
import app.vercel.originsd.oqteris.pdf.model.PdfDocumentModel;
import app.vercel.originsd.oqteris.ui.ThemeManager;
import app.vercel.originsd.oqteris.ui.icon.AppIcons;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Professional desktop Signature Properties dialog, displaying cryptographic verification results,
 * document integrity coverage, and X.509 certificate chain details for digital signatures.
 */
public class SignaturePropertiesDialog extends Stage {

    private final PdfSignatureInfo signatureInfo;
    private final PdfDocumentModel model;
    private final Runnable onTrustChanged;

    public SignaturePropertiesDialog(Stage owner, PdfSignatureInfo sig, PdfDocumentModel model, Runnable onTrustChanged) {
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.APPLICATION_MODAL);
        this.signatureInfo = sig;
        this.model = model;
        this.onTrustChanged = onTrustChanged;

        setTitle("Signature Properties - " + (sig != null ? sig.getSignerName() : "Digital Signature"));
        buildUI();
    }

    private void buildUI() {
        VBox root = new VBox(14);
        root.getStyleClass().add("signature-properties-dialog");
        root.setStyle("-fx-background-color: -color-surface;");
        root.setPadding(new Insets(18));
        root.setPrefWidth(620);
        root.setPrefHeight(560);

        if (signatureInfo == null) {
            Label noSig = new Label("No digital signature details available.");
            root.getChildren().add(noSig);
            Scene scene = new Scene(root);
            ThemeManager.getInstance().applyTheme(scene);
            setScene(scene);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

        // 1. Status Banner
        VBox banner = createStatusBanner(signatureInfo, sdf);

        // 2. Tab Pane
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("signature-tab-pane");
        tabPane.setStyle("-fx-background-color: -color-surface;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab: Summary
        Tab summaryTab = new Tab("Summary", createSummaryTab(signatureInfo, sdf));
        summaryTab.setGraphic(AppIcons.of(MaterialDesignI.INFORMATION_OUTLINE, 14));

        // Tab: Document Integrity & Revision
        Tab docTab = new Tab("Document Integrity", createDocumentTab(signatureInfo));
        docTab.setGraphic(AppIcons.of(MaterialDesignS.SHIELD_CHECK_OUTLINE, 14));

        // Tab: Signer Certificate
        Tab certTab = new Tab("Signer Certificate", createCertificateTab(signatureInfo));
        certTab.setGraphic(AppIcons.of(MaterialDesignC.CERTIFICATE_OUTLINE, 14));

        tabPane.getTabs().addAll(summaryTab, docTab, certTab);

        // 3. Action Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        if (signatureInfo.getStatus() == SignatureVerificationStatus.VALID_BUT_UNTRUSTED
                && signatureInfo.getSignerCertificate() != null) {
            Button trustBtn = new Button("Trust Signer...");
            trustBtn.setGraphic(AppIcons.of(MaterialDesignS.SHIELD_CHECK, 14, Color.WHITE));
            trustBtn.setStyle("-fx-background-color: #0078d4; -fx-text-fill: white; -fx-font-weight: bold;");
            trustBtn.setOnAction(e -> handleTrustCertificate());
            buttonBar.getChildren().add(trustBtn);
        }

        Button certMgrBtn = new Button("Trust Store...");
        certMgrBtn.setGraphic(AppIcons.of(MaterialDesignC.CERTIFICATE, 14));
        certMgrBtn.setOnAction(e -> {
            CertificateManagerDialog cmd = new CertificateManagerDialog(this);
            cmd.showAndWait();
            if (model != null) {
                model.reVerifySignatures();
                if (onTrustChanged != null) {
                    onTrustChanged.run();
                }
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> close());

        buttonBar.getChildren().addAll(certMgrBtn, spacer, closeBtn);

        root.getChildren().addAll(banner, tabPane, buttonBar);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);
    }

    private VBox createStatusBanner(PdfSignatureInfo sig, SimpleDateFormat sdf) {
        VBox banner = new VBox(6);
        banner.setPadding(new Insets(12, 14, 12, 14));

        SignatureVerificationStatus st = sig.getStatus();
        String borderCol;
        String bgCol;
        String titleText;
        Color iconColor;
        var iconCode = MaterialDesignS.SHIELD_CHECK;

        if (st == SignatureVerificationStatus.VALID_AND_TRUSTED) {
            borderCol = "#28a745";
            bgCol = "rgba(40, 167, 69, 0.08)";
            titleText = "Signature is VALID and TRUSTED";
            iconColor = Color.web("#28a745");
            iconCode = MaterialDesignS.SHIELD_CHECK;
        } else if (st == SignatureVerificationStatus.VALID_BUT_UNTRUSTED) {
            borderCol = "#ffc107";
            bgCol = "rgba(255, 193, 7, 0.10)";
            titleText = "Signature is VALID, but Signer is UNTRUSTED";
            iconColor = Color.web("#d97706");
            iconCode = MaterialDesignS.SHIELD_ALERT;
        } else {
            borderCol = "#dc3545";
            bgCol = "rgba(220, 53, 69, 0.08)";
            titleText = "Signature is INVALID or TAMPERED";
            iconColor = Color.web("#dc3545");
            iconCode = MaterialDesignS.SHIELD_OFF;
        }

        banner.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;", bgCol, borderCol));

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label("", AppIcons.of(iconCode, 22, iconColor));
        Label statusLbl = new Label(titleText);
        statusLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        titleRow.getChildren().addAll(iconLbl, statusLbl);

        Label signerSub = new Label("Signed by " + sig.getSignerName() + " on " + sdf.format(sig.getSigningTime()));
        signerSub.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -color-text-primary;");

        Label desc = new Label(sig.getStatusDetails());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-text-muted;");

        banner.getChildren().addAll(titleRow, signerSub, desc);
        return banner;
    }

    private Node createSummaryTab(PdfSignatureInfo sig, SimpleDateFormat sdf) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("signature-summary-grid");
        grid.setPadding(new Insets(14));
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setStyle("-fx-background-color: -color-surface;");

        int row = 0;
        addDetailRow(grid, row++, "Signer Common Name:", sig.getSignerName());
        addDetailRow(grid, row++, "Distinguished Name:", sig.getSignerDN() != null ? sig.getSignerDN() : "N/A");
        addDetailRow(grid, row++, "Signing Time:", sdf.format(sig.getSigningTime()));
        addDetailRow(grid, row++, "Timestamping:", sig.hasTimestampToken()
                ? "Embedded Timestamp (" + (sig.getTimestampTime() != null ? sdf.format(sig.getTimestampTime()) : "Verified") + ")"
                : "No embedded cryptographic timestamp");
        addDetailRow(grid, row++, "Reason:", (sig.getReason() != null && !sig.getReason().isBlank()) ? sig.getReason() : "Not specified");
        addDetailRow(grid, row++, "Location:", (sig.getLocation() != null && !sig.getLocation().isBlank()) ? sig.getLocation() : "Not specified");
        addDetailRow(grid, row++, "Field Name:", sig.getFieldName() != null ? sig.getFieldName() : "Signature");
        addDetailRow(grid, row++, "Digest Algorithm:", sig.getDigestAlgorithm() != null ? sig.getDigestAlgorithm() : "SHA-256");
        addDetailRow(grid, row++, "Signature SubFilter:", sig.getSubFilter() != null ? sig.getSubFilter() : "adbe.pkcs7.detached");

        ScrollPane sp = new ScrollPane(grid);
        sp.getStyleClass().add("signature-summary-scroll");
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private Node createDocumentTab(PdfSignatureInfo sig) {
        VBox box = new VBox(12);
        box.getStyleClass().add("signature-doc-tab");
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: -color-surface;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        int row = 0;
        String modText = sig.coversWholeDocument()
                ? "✓ Covers whole document (No alterations after signing)"
                : "⚠ Document modified or incremental update appended after signing";
        addDetailRow(grid, row++, "Document Coverage:", modText);

        long signedBytes = sig.getByteRangeLength();
        long totalBytes = sig.getTotalFileLength();
        double pct = totalBytes > 0 ? ((double) signedBytes / totalBytes) * 100.0 : 100.0;
        String byteInfo = String.format("%,d bytes of %,d bytes (%.1f%%)", signedBytes, totalBytes, pct);
        addDetailRow(grid, row++, "Byte Range Extent:", byteInfo);

        String hashText = sig.isCryptographicallyValid()
                ? "✓ Cryptographic hash matches byte range digest exactly"
                : "✕ Hash mismatch — signed content has been modified or corrupted";
        addDetailRow(grid, row++, "Hash Verification:", hashText);

        String certStatus = switch (sig.getStatus()) {
            case VALID_AND_TRUSTED -> "✓ Signer certificate chains to trusted certificate anchor";
            case VALID_BUT_UNTRUSTED -> "? Signer certificate is valid, but anchor is not in trust store";
            case INVALID_SIGNATURE -> "✕ Cryptographic signature verification failed";
            case DOCUMENT_MODIFIED -> "✕ Document altered after signing";
            case CERTIFICATE_EXPIRED -> "✕ Certificate expired";
            default -> "Status unverified";
        };
        addDetailRow(grid, row++, "Trust Status:", certStatus);

        box.getChildren().add(grid);
        return box;
    }

    private Node createCertificateTab(PdfSignatureInfo sig) {
        VBox box = new VBox(10);
        box.getStyleClass().add("signature-cert-tab");
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: -color-surface;");

        Label chainLbl = new Label("Certificate Chain:");
        chainLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -color-text-primary;");

        TreeView<String> chainTree = new TreeView<>();
        chainTree.getStyleClass().add("signature-chain-tree");
        TreeItem<String> root = new TreeItem<>("Certification Path");
        root.setExpanded(true);

        List<X509Certificate> chain = sig.getCertificateChain();
        TreeItem<String> currentItem = root;
        if (chain != null && !chain.isEmpty()) {
            // Leaf is at index 0, Root is at end of chain
            for (int i = chain.size() - 1; i >= 0; i--) {
                X509Certificate cert = chain.get(i);
                CertificateDetails cd = new CertificateDetails(cert);
                TreeItem<String> item = new TreeItem<>(cd.getCommonName() + (cd.isCertificateAuthority() ? " [CA]" : ""));
                item.setExpanded(true);
                currentItem.getChildren().add(item);
                currentItem = item;
            }
        } else if (sig.getSignerCertificate() != null) {
            CertificateDetails cd = new CertificateDetails(sig.getSignerCertificate());
            root.getChildren().add(new TreeItem<>(cd.getCommonName()));
        }
        chainTree.setRoot(root);
        chainTree.setPrefHeight(110);

        Label leafLbl = new Label("Signer Certificate Attributes:");
        leafLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -color-text-primary;");

        TextArea certText = new TextArea();
        certText.getStyleClass().add("signature-cert-area");
        certText.setEditable(false);
        certText.setWrapText(true);
        certText.setPrefRowCount(9);
        certText.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        if (sig.getSignerCertificateDetails() != null) {
            CertificateDetails d = sig.getSignerCertificateDetails();
            certText.setText(
                    "Subject:     " + d.getSubjectDN() + "\n" +
                    "Issuer:      " + d.getIssuerDN() + "\n" +
                    "Serial:      " + d.getSerialNumber() + "\n" +
                    "Valid From:  " + d.getNotBefore() + "\n" +
                    "Valid Until: " + d.getNotAfter() + "\n" +
                    "Algorithm:   " + d.getSigAlgName() + "\n" +
                    "Public Key:  " + d.getPublicKeyInfo() + "\n" +
                    "SHA-256:     " + d.getSha256Fingerprint() + "\n" +
                    "SHA-1:       " + d.getSha1Fingerprint() + "\n" +
                    "Key Usages:  " + String.join(", ", d.getKeyUsages())
            );
        } else {
            certText.setText("No X.509 certificate attributes available.");
        }
        VBox.setVgrow(certText, Priority.ALWAYS);

        box.getChildren().addAll(chainLbl, chainTree, leafLbl, certText);
        return box;
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -color-text-secondary;");
        Label val = new Label(value != null ? value : "");
        val.setWrapText(true);
        val.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-text-primary;");
        grid.addRow(row, lbl, val);
    }

    private void handleTrustCertificate() {
        X509Certificate cert = signatureInfo.getSignerCertificate();
        if (cert == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Do you want to trust '" + signatureInfo.getSignerName() + "' as a trusted certificate authority in Oqteris?\n\n" +
                "Only trust certificates from individuals or organizations you verify independently.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Trust Signer Certificate");
        confirm.initOwner(this);
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    String alias = signatureInfo.getSignerName().replaceAll("[^a-zA-Z0-9]", "_");
                    String docTitle = model != null ? model.getTitle() : "PDF Document";
                    TrustStoreManager.getInstance().importCertificate(alias, cert, TrustLevel.TRUSTED, "Imported from signature in " + docTitle);
                    if (model != null) {
                        model.reVerifySignatures();
                    }
                    if (onTrustChanged != null) {
                        onTrustChanged.run();
                    }
                    close();
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Failed to trust certificate: " + e.getMessage());
                    err.initOwner(this);
                    err.showAndWait();
                }
            }
        });
    }
}
