package org.originsd.oqteris.crypto.trust;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.originsd.oqteris.ui.ThemeManager;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Dialog allowing users to view, import, remove, and manage trusted certificates.
 */
public class CertificateManagerDialog extends Stage {
    private final TrustStoreManager trustStoreManager = TrustStoreManager.getInstance();
    private final TableView<TrustedCertificateRecord> certTable = new TableView<>();
    private final TextArea detailsArea = new TextArea();

    public CertificateManagerDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Certificate Trust Manager - Oqteris");

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setPrefSize(720, 520);

        Label header = new Label("Trusted Certificate Authorities & Security Anchors");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label desc = new Label("Oqteris verifies digital signatures using this trust store. Signatures are marked as trusted ONLY when chaining to an explicitly trusted anchor.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #666666; -fx-font-size: 12px;");

        // Table setup
        TableColumn<TrustedCertificateRecord, String> aliasCol = new TableColumn<>("Alias");
        aliasCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getAlias()));
        aliasCol.setPrefWidth(140);

        TableColumn<TrustedCertificateRecord, String> subjectCol = new TableColumn<>("Issued To (CN)");
        subjectCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDetails().getCommonName()));
        subjectCol.setPrefWidth(180);

        TableColumn<TrustedCertificateRecord, String> issuerCol = new TableColumn<>("Issued By (CN)");
        issuerCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDetails().getIssuerCommonName()));
        issuerCol.setPrefWidth(180);

        TableColumn<TrustedCertificateRecord, String> trustCol = new TableColumn<>("Trust Policy");
        trustCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTrustLevel().getLabel()));
        trustCol.setPrefWidth(130);

        certTable.getColumns().addAll(aliasCol, subjectCol, issuerCol, trustCol);
        certTable.setPlaceholder(new Label("No custom certificates in application trust store."));
        VBox.setVgrow(certTable, Priority.ALWAYS);

        // Details area
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(7);
        detailsArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        certTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showCertificateDetails(newVal);
            } else {
                detailsArea.clear();
            }
        });

        // Buttons
        Button importBtn = new Button("Import Certificate...");
        importBtn.setOnAction(e -> handleImport());

        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(e -> handleRemove());
        removeBtn.disableProperty().bind(certTable.getSelectionModel().selectedItemProperty().isNull());

        CheckBox useSystemBox = new CheckBox("Use Operating System / JRE Root Certificates (" + trustStoreManager.getSystemAnchorCount() + " loaded)");
        useSystemBox.setSelected(trustStoreManager.isUseSystemTrustStore());
        useSystemBox.selectedProperty().addListener((obs, oldVal, newVal) -> trustStoreManager.setUseSystemTrustStore(newVal));

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> close());

        HBox btnBar = new HBox(10, importBtn, removeBtn, new Region(), closeBtn);
        HBox.setHgrow(btnBar.getChildren().get(2), Priority.ALWAYS);
        btnBar.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, desc, useSystemBox, certTable, new Label("Certificate Details:"), detailsArea, btnBar);

        Scene scene = new Scene(root);
        ThemeManager.getInstance().applyTheme(scene);
        setScene(scene);

        refreshTable();
    }

    private void refreshTable() {
        certTable.setItems(FXCollections.observableArrayList(trustStoreManager.getCustomRecords()));
    }

    private void showCertificateDetails(TrustedCertificateRecord record) {
        CertificateDetails d = record.getDetails();
        StringBuilder sb = new StringBuilder();
        sb.append("Subject DN:  ").append(d.getSubjectDN()).append("\n");
        sb.append("Issuer DN:   ").append(d.getIssuerDN()).append("\n");
        sb.append("Serial No:   ").append(d.getSerialNumber()).append("\n");
        sb.append("Validity:    ").append(d.getFormattedValidity()).append("\n");
        sb.append("Signature:   ").append(d.getSigAlgName()).append("\n");
        sb.append("Public Key:  ").append(d.getPublicKeyInfo()).append("\n");
        sb.append("SHA-256:     ").append(d.getSha256Fingerprint()).append("\n");
        sb.append("SHA-1:       ").append(d.getSha1Fingerprint()).append("\n");
        sb.append("Is CA:       ").append(d.isCertificateAuthority() ? "Yes" : "No").append("\n");
        sb.append("Key Usages:  ").append(String.join(", ", d.getKeyUsages())).append("\n");
        if (!record.getNotes().isEmpty()) {
            sb.append("Notes:       ").append(record.getNotes()).append("\n");
        }
        detailsArea.setText(sb.toString());
    }

    private void handleImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Trusted Certificate");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificate Files (*.cer, *.crt, *.pem)", "*.cer", "*.crt", "*.pem"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(this);
        if (file == null) return;

        try (FileInputStream in = new FileInputStream(file)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            if (cert != null) {
                String alias = file.getName().replaceFirst("[.][^.]+$", "");
                trustStoreManager.importCertificate(alias, cert, TrustLevel.TRUSTED, "Imported from " + file.getName());
                refreshTable();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Certificate '" + alias + "' successfully imported into trust store.", ButtonType.OK);
                alert.initOwner(this);
                alert.showAndWait();
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to import certificate: " + e.getMessage(), ButtonType.OK);
            alert.initOwner(this);
            alert.showAndWait();
        }
    }

    private void handleRemove() {
        TrustedCertificateRecord selected = certTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove '" + selected.getAlias() + "' from trusted store?", ButtonType.YES, ButtonType.NO);
        confirm.initOwner(this);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    trustStoreManager.removeCertificate(selected.getAlias());
                    refreshTable();
                    detailsArea.clear();
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Error removing certificate: " + e.getMessage());
                    err.initOwner(this);
                    err.showAndWait();
                }
            }
        });
    }
}
