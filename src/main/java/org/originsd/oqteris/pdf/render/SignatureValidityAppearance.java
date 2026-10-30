package org.originsd.oqteris.pdf.render;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.crypto.signature.SignatureVerificationStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reproduces what Adobe Acrobat / Reader and Foxit do for "layered" signature appearances.
 *
 * <p>Many signed PDFs (iText / OpenPDF "Acro6 layers", Acrobat 5 style) store the signature
 * appearance as five form XObjects n0..n4. The file only contains ONE stored state (usually a yellow
 * question mark in n1 and the text "Signature Not Verified" in n4). Adobe ignores that stored state
 * at display time: after validating the signature it redraws
 * <ul>
 *   <li>n1 - validity icon for the valid / unknown state (green tick, or question mark)</li>
 *   <li>n3 - validity icon for the invalid state (red cross)</li>
 *   <li>n4 - caption ("Signature valid", ...)</li>
 * </ul>
 * PDFBox just draws the stored layers, which is why the viewer showed "?" for trusted signatures.
 *
 * <p>This class rewrites those layers IN MEMORY (the PDDocument held by the viewer) according to the
 * verification result. The file on disk is never touched, so the signature stays intact.
 * Do NOT save this PDDocument back over the signed file.
 */
public final class SignatureValidityAppearance {

    private enum State { VALID, UNKNOWN, INVALID }

    private static final String BLANK = "q Q\n";

    private SignatureValidityAppearance() {
    }

    /**
     * Redraws the validity layers of every signed widget.
     *
     * @param infos verification results in the same order as {@code doc.getSignatureDictionaries()}
     * @return the signatures whose on-page appearance now shows the validity state itself
     *         (identity set). The caller should not draw an extra overlay badge for these.
     */
    public static Set<PdfSignatureInfo> apply(PDDocument doc, List<PdfSignatureInfo> infos) {
        Set<PdfSignatureInfo> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        if (doc == null || infos == null || infos.isEmpty()) {
            return handled;
        }
        try {
            List<PDSignature> dicts = doc.getSignatureDictionaries();
            for (PDSignatureField field : doc.getSignatureFields()) {
                PDSignature fieldSig = field.getSignature();
                if (fieldSig == null) continue;
                int idx = indexOf(dicts, fieldSig);
                if (idx < 0 || idx >= infos.size()) continue;

                PdfSignatureInfo info = infos.get(idx);
                State state = stateOf(info.getStatus());

                for (PDAnnotationWidget widget : field.getWidgets()) {
                    PDAppearanceStream ap = widget.getNormalAppearanceStream();
                    if (ap == null) continue;
                    try {
                        if (redraw(ap, state)) {
                            handled.add(info);
                        }
                    } catch (Exception ignored) {
                        // leave this widget as stored
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return handled;
    }

    // ------------------------------------------------------------------ state mapping

    private static State stateOf(SignatureVerificationStatus s) {
        if (s == SignatureVerificationStatus.VALID_AND_TRUSTED) return State.VALID;
        if (s == SignatureVerificationStatus.VALID_BUT_UNTRUSTED) return State.UNKNOWN;
        return State.INVALID;
    }

    private static String captionOf(State s) {
        return switch (s) {
            case VALID -> "Signature valid";
            case UNKNOWN -> "Validity unknown";
            case INVALID -> "Signature invalid";
        };
    }

    // ------------------------------------------------------------------ layer lookup

    private static boolean redraw(PDFormXObject root, State state) throws IOException {
        PDResources layers = findLayerResources(root, 0);
        if (layers == null) return false;

        PDFormXObject n1 = layer(layers, "n1");
        PDFormXObject n2 = layer(layers, "n2");
        PDFormXObject n3 = layer(layers, "n3");
        PDFormXObject n4 = layer(layers, "n4");
        if (n1 == null || n3 == null) return false;   // not a layered appearance

        switch (state) {
            case VALID -> {
                write(n1, tick(box(n1)));
                write(n3, BLANK);
            }
            case UNKNOWN -> {
                write(n1, questionMark(n1));
                write(n3, BLANK);
            }
            case INVALID -> {
                write(n1, BLANK);
                write(n3, cross(box(n3)));
            }
        }
        if (n4 != null) {
            write(n4, caption(n4, n2, captionOf(state)));
        }
        return true;
    }

    /** The layers live either directly in the /N stream's resources or one/two forms deeper (e.g. /FRM). */
    private static PDResources findLayerResources(PDFormXObject form, int depth) throws IOException {
        PDResources r = form.getResources();
        if (r == null) return null;
        if (has(r, "n1") && has(r, "n3")) return r;
        if (depth >= 2) return null;
        for (COSName name : r.getXObjectNames()) {
            PDXObject x = r.getXObject(name);
            if (x instanceof PDFormXObject child) {
                PDResources found = findLayerResources(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean has(PDResources r, String name) {
        for (COSName n : r.getXObjectNames()) {
            if (name.equals(n.getName())) return true;
        }
        return false;
    }

    private static PDFormXObject layer(PDResources r, String name) throws IOException {
        if (!has(r, name)) return null;
        PDXObject x = r.getXObject(COSName.getPDFName(name));
        return x instanceof PDFormXObject f ? f : null;
    }

    private static int indexOf(List<PDSignature> dicts, PDSignature target) {
        for (int i = 0; i < dicts.size(); i++) {
            if (dicts.get(i).getCOSObject() == target.getCOSObject()) return i;
        }
        int[] tr = target.getByteRange();
        if (tr != null) {
            for (int i = 0; i < dicts.size(); i++) {
                if (Arrays.equals(dicts.get(i).getByteRange(), tr)) return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    /** Replaces the content stream of a layer. */
    private static void write(PDFormXObject form, String content) throws IOException {
        COSStream cs = form.getCOSObject();
        cs.removeItem(COSName.FILTER);
        cs.removeItem(COSName.DECODE_PARMS);
        try (OutputStream out = cs.createOutputStream()) {
            out.write(content.getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    private static PDResources resourcesOf(PDFormXObject form) {
        PDResources r = form.getResources();
        if (r == null) {
            r = new PDResources();
            form.setResources(r);
        }
        return r;
    }

    /** [x, y, width, height] of the layer's own bounding box (iText layers default to 100 x 100). */
    private static double[] box(PDFormXObject f) {
        PDRectangle b = f.getBBox();
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) {
            return new double[]{0, 0, 100, 100};
        }
        return new double[]{b.getLowerLeftX(), b.getLowerLeftY(), b.getWidth(), b.getHeight()};
    }

    /** Green tick with a dark drop shadow, like Acrobat's "Signature valid" mark. */
    private static String tick(double[] b) {
        double s = Math.min(b[2], b[3]);
        double ox = b[0] + (b[2] - s) / 2.0;
        double oy = b[1] + (b[3] - s) / 2.0;
        double d = 0.035 * s;
        double[][] pts = {{0.12, 0.50}, {0.36, 0.20}, {0.90, 0.88}};

        StringBuilder sb = new StringBuilder("q 1 J 1 j ").append(f(0.16 * s)).append(" w\n");
        polyline(sb, pts, ox + d, oy - d, s, "0 0 0 RG");              // shadow
        polyline(sb, pts, ox, oy, s, "0.04 0.67 0.27 RG");             // green
        return sb.append("Q\n").toString();
    }

    /** Red cross with a dark drop shadow, like Acrobat's "Signature invalid" mark. */
    private static String cross(double[] b) {
        double s = Math.min(b[2], b[3]);
        double ox = b[0] + (b[2] - s) / 2.0;
        double oy = b[1] + (b[3] - s) / 2.0;
        double d = 0.035 * s;
        double[][] a = {{0.16, 0.16}, {0.84, 0.84}};
        double[][] c = {{0.16, 0.84}, {0.84, 0.16}};

        StringBuilder sb = new StringBuilder("q 1 J 1 j ").append(f(0.15 * s)).append(" w\n");
        polyline(sb, a, ox + d, oy - d, s, "0 0 0 RG");
        polyline(sb, c, ox + d, oy - d, s, "0 0 0 RG");
        polyline(sb, a, ox, oy, s, "0.86 0.10 0.10 RG");
        polyline(sb, c, ox, oy, s, "0.86 0.10 0.10 RG");
        return sb.append("Q\n").toString();
    }

    /** Big yellow question mark (validity unknown). Drawn with Helvetica-Bold. */
    private static String questionMark(PDFormXObject n1) throws IOException {
        double[] b = box(n1);
        double s = Math.min(b[2], b[3]);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        COSName fn = resourcesOf(n1).add(bold);

        double size = s * 1.15;
        double w = 0.611 * size;
        double h = 0.73 * size;
        double x = b[0] + (b[2] - w) / 2.0;
        double y = b[1] + (b[3] - h) / 2.0;
        double d = 0.03 * s;

        return "q\n"
                + "BT /" + fn.getName() + " " + f(size) + " Tf 0 g " + f(x + d) + " " + f(y - d) + " Td (?) Tj ET\n"
                + "BT /" + fn.getName() + " " + f(size) + " Tf 1 0.80 0 rg " + f(x) + " " + f(y) + " Td (?) Tj ET\n"
                + "Q\n";
    }

    /**
     * Caption in the top section of the signature, e.g. "Signature valid".
     * iText creates n4 as the top 30 % of the field; if n4 spans the whole field, use its top 30 %.
     */
    private static String caption(PDFormXObject n4, PDFormXObject n2, String text) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        COSName fn = resourcesOf(n4).add(font);

        double[] b = box(n4);
        double zoneH = b[3];
        if (n2 != null && b[3] >= 0.75 * box(n2)[3]) {
            zoneH = b[3] * 0.3;
        }
        double zoneY = b[1] + b[3] - zoneH;

        double size = zoneH * 0.62;
        double textW = font.getStringWidth(text) / 1000.0 * size;
        double maxW = b[2] - 8;
        if (textW > maxW && textW > 0) {
            size *= maxW / textW;
        }
        double x = b[0] + 4;
        double y = zoneY + (zoneH - size * 0.72) / 2.0;

        return "q\nBT /" + fn.getName() + " " + f(size) + " Tf 0 g " + f(x) + " " + f(y) + " Td ("
                + text + ") Tj ET\nQ\n";
    }

    private static void polyline(StringBuilder sb, double[][] pts, double ox, double oy, double s, String color) {
        sb.append(color).append('\n');
        for (int i = 0; i < pts.length; i++) {
            sb.append(f(ox + pts[i][0] * s)).append(' ').append(f(oy + pts[i][1] * s))
                    .append(i == 0 ? " m\n" : " l\n");
        }
        sb.append("S\n");
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}