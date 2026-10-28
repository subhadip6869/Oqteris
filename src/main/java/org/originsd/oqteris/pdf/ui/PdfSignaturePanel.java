package org.originsd.oqteris.pdf.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import org.originsd.oqteris.crypto.trust.CertificateDetails;
import org.originsd.oqteris.crypto.trust.TrustLevel;
import org.originsd.oqteris.crypto.trust.TrustStoreManager;
import org.originsd.oqteris.pdf.model.PdfDocumentModel;
import org.originsd.oqteris.ui.icon.AppIcons;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Dedicated sidebar panel presenting digital signature verification details and trust controls.
 */
public class PdfSignaturePanel extends VBox {
    private final PdfDocumentModel model;
    private final VBox signatureCardsBox = new VBox(12);

    public PdfSignaturePanel(PdfDocumentModel model) {
        this.model = model;
        setPadding(new Insets(12));
        setSpacing(10);
        setPrefWidth(320);

        buildUI();
    }

    private void buildUI() {
        Label title = new Label("Digital Signatures");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        List<PdfSignatureInfo> signatures = model.getSignatures();
        Label countLabel = new Label(signatures.size() + " signature(s) detected");
        countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");

        ScrollPane scroll = new ScrollPane(signatureCardsBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(title, countLabel, new Separator(), scroll);

        refreshSignatures();
    }

    public void refreshSignatures() {
        signatureCardsBox.getChildren().clear();
        List<PdfSignatureInfo> signatures = model.getSignatures();

        if (signatures.isEmpty()) {
            Label noSig = new Label("This PDF does not contain any digital signatures.");
            noSig.setWrapText(true);
            noSig.setStyle("-fx-text-fill: #777777; -fx-padding: 8px;");
            signatureCardsBox.getChildren().add(noSig);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (PdfSignatureInfo sig : signatures) {
            VBox card = new VBox(8);
            card.setPadding(new Insets(12));
            card.setStyle("-fx-background-color: #fafafa; -fx-border-color: #e0e0e0; -fx-border-radius: 6px; -fx-background-radius: 6px;");

            // Status Badge
            Label badge = new Label();
            if (sig.getStatus() == SignatureVerificationStatus.VALID_AND_TRUSTED) {
                badge.setText(" ✓ " + sig.getStatus().getDisplayTitle());
                badge.setGraphic(AppIcons.of(org.kordamp.ikonli.materialdesign2.MaterialDesignS.SHIELD_CHECK, 14, javafx.scene.paint.Color.WHITE));
                badge.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 11px;");
            } else if (sig.getStatus() == SignatureVerificationStatus.VALID_BUT_UNTRUSTED) {
                badge.setText(" ? " + sig.getStatus().getDisplayTitle());
                badge.setGraphic(AppIcons.of(org.kordamp.ikonli.materialdesign2.MaterialDesignS.SHIELD_ALERT, 14, javafx.scene.paint.Color.rgb(30, 41, 59)));
                badge.setStyle("-fx-background-color: #ffc107; -fx-text-fill: #1e293b; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 11px;");
            } else {
                badge.setText(" ✕ " + sig.getStatus().getDisplayTitle());
                badge.setGraphic(AppIcons.of(org.kordamp.ikonli.materialdesign2.MaterialDesignS.SHIELD_OFF, 14, javafx.scene.paint.Color.WHITE));
                badge.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 11px;");
            }

            // Signer Name
            Label signer = new Label("Signer: " + sig.getSignerName());
            signer.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            // Status Explanation
            Label statusDesc = new Label(sig.getStatusDetails());
            statusDesc.setWrapText(true);
            statusDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444;");

            // Signing Date
            Label date = new Label("Signed: " + sdf.format(sig.getSigningTime()) + (sig.hasTimestampToken() ? " (Timestamped)" : ""));
            date.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

            // Document modification check
            Label coverage = new Label(sig.coversWholeDocument() ? "✓ Covers entire document" : "⚠ Document altered after signing");
            coverage.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (sig.coversWholeDocument() ? "#28a745" : "#dc3545") + "; -fx-font-weight: bold;");

            // Action Buttons
            HBox actions = new HBox(6);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button inspectBtn = new Button("Inspect Certificate...");
            inspectBtn.setStyle("-fx-font-size: 11px;");
            inspectBtn.setOnAction(e -> showCertificateDetails(sig));

            actions.getChildren().add(inspectBtn);

            // If valid but untrusted, offer "Trust Certificate" button
            if (sig.getStatus() == SignatureVerificationStatus.VALID_BUT_UNTRUSTED && sig.getSignerCertificate() != null) {
                Button trustBtn = new Button("Trust Signer...");
                trustBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #0078d4; -fx-text-fill: white;");
                trustBtn.setOnAction(e -> handleTrustCertificate(sig));
                actions.getChildren().add(trustBtn);
            }

            card.getChildren().addAll(badge, signer, statusDesc, date, coverage, actions);
            signatureCardsBox.getChildren().add(card);
        }
    }

    private void showCertificateDetails(PdfSignatureInfo sig) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Certificate Information - " + sig.getSignerName());
        dialog.setHeaderText("Digital Signature & X.509 Certificate Chain");

        VBox content = new VBox(10);
        content.setPadding(new Insets(14));
        content.setPrefWidth(540);

        // Certificate Chain Hierarchy
        Label chainLbl = new Label("Certificate Chain:");
        chainLbl.setStyle("-fx-font-weight: bold;");

        TreeView<String> chainTree = new TreeView<>();
        TreeItem<String> root = new TreeItem<>("Certification Path");
        root.setExpanded(true);

        List<X509Certificate> chain = sig.getCertificateChain();
        TreeItem<String> currentItem = root;
        // In chain, leaf is 0, root is at end
        for (int i = chain.size() - 1; i >= 0; i--) {
            X509Certificate cert = chain.get(i);
            CertificateDetails cd = new CertificateDetails(cert);
            TreeItem<String> item = new TreeItem<>(cd.getCommonName() + (cd.isCertificateAuthority() ? " [CA]" : ""));
            item.setExpanded(true);
            currentItem.getChildren().add(item);
            currentItem = item;
        }
        chainTree.setRoot(root);
        chainTree.setPrefHeight(120);

        // Details Text
        TextArea certText = new TextArea();
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
        }

        content.getChildren().addAll(chainLbl, chainTree, new Label("Leaf Certificate Attributes:"), certText);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void handleTrustCertificate(PdfSignatureInfo sig) {
        X509Certificate cert = sig.getSignerCertificate();
        if (cert == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Do you want to trust '" + sig.getSignerName() + "' as a trusted certificate authority in Oqteris?\n\n" +
                "Only trust certificates from individuals or organizations you verify independently.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Trust Certificate Authority");
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    String alias = sig.getSignerName().replaceAll("[^a-zA-Z0-9]", "_");
                    TrustStoreManager.getInstance().importCertificate(alias, cert, TrustLevel.TRUSTED, "Imported from PDF signature in " + model.getTitle());
                    model.reVerifySignatures();
                    refreshSignatures();
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Failed to trust certificate: " + e.getMessage());
                    err.showAndWait();
                }
            }
        });
    }
}
