package org.originsd.oqteris.core.io;

import org.originsd.oqteris.core.model.DocumentModel;
import org.originsd.oqteris.core.model.DocumentType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Central registry mapping file extensions and formats to importers and exporters.
 */
public class DocumentRegistry {
    private static final DocumentRegistry INSTANCE = new DocumentRegistry();

    private final List<DocumentImporter<?>> importers = new ArrayList<>();
    private final List<DocumentExporter<?>> exporters = new ArrayList<>();
    private final List<FormatDescriptor> formats = new ArrayList<>();

    private DocumentRegistry() {
    }

    public static DocumentRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void registerFormat(FormatDescriptor format) {
        if (format != null && !formats.contains(format)) {
            formats.add(format);
        }
    }

    public synchronized void registerImporter(DocumentImporter<?> importer) {
        if (importer != null && !importers.contains(importer)) {
            importers.add(importer);
            registerFormat(importer.getFormatDescriptor());
        }
    }

    public synchronized void registerExporter(DocumentExporter<?> exporter) {
        if (exporter != null && !exporters.contains(exporter)) {
            exporters.add(exporter);
            registerFormat(exporter.getFormatDescriptor());
        }
    }

    public List<FormatDescriptor> getSupportedFormats() {
        return new ArrayList<>(formats);
    }

    /**
     * Determines document type from file name / extension.
     */
    public Optional<DocumentType> detectDocumentType(File file) {
        if (file == null) return Optional.empty();
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        String ext = name.substring(dot + 1);

        for (FormatDescriptor desc : formats) {
            for (String supportedExt : desc.getExtensions()) {
                if (supportedExt.equalsIgnoreCase(ext)) {
                    return Optional.of(desc.getDocumentType());
                }
            }
        }

        // Standard fallbacks
        switch (ext) {
            case "docx":
            case "doc":
            case "odt":
            case "rtf":
            case "txt":
            case "html":
            case "htm":
                return Optional.of(DocumentType.WRITER);
            case "xlsx":
            case "xls":
            case "ods":
            case "csv":
            case "tsv":
                return Optional.of(DocumentType.CALC);
            case "pptx":
            case "ppt":
            case "odp":
                return Optional.of(DocumentType.IMPRESS);
            case "pdf":
                return Optional.of(DocumentType.PDF);
            default:
                return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends DocumentModel> Optional<DocumentImporter<T>> findImporter(File file) {
        if (file == null) return Optional.empty();
        for (DocumentImporter<?> importer : importers) {
            if (importer.canImport(file)) {
                return Optional.of((DocumentImporter<T>) importer);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public <T extends DocumentModel> Optional<DocumentExporter<T>> findExporter(File file, DocumentType type) {
        if (file == null) return Optional.empty();
        for (DocumentExporter<?> exporter : exporters) {
            if (exporter.getFormatDescriptor().getDocumentType() == type && exporter.canExport(file)) {
                return Optional.of((DocumentExporter<T>) exporter);
            }
        }
        return Optional.empty();
    }
}
