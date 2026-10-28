package org.oqteris.pdf.model;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.oqteris.core.model.AbstractDocumentModel;
import org.oqteris.core.model.DocumentType;
import org.oqteris.crypto.signature.PdfSignatureInfo;
import org.oqteris.crypto.signature.PdfSignatureVerifier;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory document model representing an open PDF document.
 */
public class PdfDocumentModel extends AbstractDocumentModel implements Closeable {
    private final PDDocument pdDocument;
    private final List<PdfSignatureInfo> signatures = new ArrayList<>();
    private final List<PdfAnnotation> annotations = new ArrayList<>();
    private PdfBookmarkNode rootBookmark;

    public PdfDocumentModel(File file) throws IOException {
        super(DocumentType.PDF, file.getName());
        setFile(file);
        this.pdDocument = Loader.loadPDF(file);
        extractMetadata();
        extractBookmarks();
        extractSignatures();
    }

    public PDDocument getPdDocument() {
        return pdDocument;
    }

    public int getPageCount() {
        return pdDocument.getNumberOfPages();
    }

    public PDPage getPage(int index) {
        if (index >= 0 && index < pdDocument.getNumberOfPages()) {
            return pdDocument.getPage(index);
        }
        return null;
    }

    public List<PdfSignatureInfo> getSignatures() {
        return Collections.unmodifiableList(signatures);
    }

    public void reVerifySignatures() {
        signatures.clear();
        extractSignatures();
        notifyChanged();
    }

    public List<PdfAnnotation> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(PdfAnnotation annot) {
        if (annot != null) {
            annotations.add(annot);
            setModified(true);
            notifyChanged();
        }
    }

    public PdfBookmarkNode getRootBookmark() {
        return rootBookmark;
    }

    private void extractMetadata() {
        PDDocumentInformation info = pdDocument.getDocumentInformation();
        if (info != null) {
            if (info.getTitle() != null && !info.getTitle().isBlank()) {
                getMetadata().setTitle(info.getTitle());
            }
            if (info.getAuthor() != null) {
                getMetadata().setAuthor(info.getAuthor());
            }
            if (info.getSubject() != null) {
                getMetadata().setSubject(info.getSubject());
            }
            if (info.getKeywords() != null) {
                getMetadata().setKeywords(info.getKeywords());
            }
        }
    }

    private void extractBookmarks() {
        try {
            PDDocumentOutline outline = pdDocument.getDocumentCatalog().getDocumentOutline();
            if (outline != null) {
                rootBookmark = new PdfBookmarkNode("Root", 0);
                extractOutlineItems(outline.children(), rootBookmark);
            }
        } catch (Exception ignored) {
        }
    }

    private void extractOutlineItems(Iterable<PDOutlineItem> items, PdfBookmarkNode parentNode) {
        for (PDOutlineItem item : items) {
            try {
                int pageIndex = 0;
                PDPage page = item.findDestinationPage(pdDocument);
                if (page != null) {
                    pageIndex = pdDocument.getPages().indexOf(page);
                }
                PdfBookmarkNode node = new PdfBookmarkNode(item.getTitle(), Math.max(0, pageIndex));
                parentNode.addChild(node);
                if (item.hasChildren()) {
                    extractOutlineItems(item.children(), node);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void extractSignatures() {
        File file = getFile();
        if (file != null && file.exists()) {
            signatures.addAll(PdfSignatureVerifier.verifySignatures(file, pdDocument));
        }
    }

    @Override
    public void close() throws IOException {
        if (pdDocument != null) {
            pdDocument.close();
        }
    }
}
