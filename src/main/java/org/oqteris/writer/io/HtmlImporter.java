package org.oqteris.writer.io;

import javafx.scene.paint.Color;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.writer.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importer for HTML documents (.html, .htm).
 */
public class HtmlImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "HTML",
            "HTML Document (*.html, *.htm)",
            DocumentType.WRITER,
            List.of("html", "htm"),
            "text/html",
            true,
            true
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new HtmlImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        // Strip script and style tags
        content = content.replaceAll("(?s)<script.*?</script>", "");
        content = content.replaceAll("(?s)<style.*?</style>", "");

        // Match block level elements: h1-h6, p, li, table, or fallback lines
        Pattern blockPattern = Pattern.compile("(?i)<(h[1-6]|p|li|tr|div)[^>]*>(.*?)</\\1>", Pattern.DOTALL);
        Matcher matcher = blockPattern.matcher(content);

        boolean foundBlocks = false;
        while (matcher.find()) {
            foundBlocks = true;
            String tag = matcher.group(1).toLowerCase();
            String inner = matcher.group(2).trim();
            if (inner.isEmpty()) continue;

            Paragraph p = new Paragraph();
            if ("h1".equals(tag)) {
                p.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
            } else if ("h2".equals(tag)) {
                p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
            } else if ("h3".equals(tag)) {
                p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_2);
            } else if ("h4".equals(tag) || "h5".equals(tag) || "h6".equals(tag)) {
                p.setHeadingStyle(Paragraph.HeadingStyle.HEADING_3);
            } else if ("li".equals(tag)) {
                p.setListType(Paragraph.ListType.BULLET);
            }

            parseInlineRuns(inner, p);
            document.addElement(p);
        }

        if (!foundBlocks) {
            // Strip any remaining html tags and split by lines
            String plain = content.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">");
            String[] lines = plain.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    Paragraph p = new Paragraph();
                    p.addRun(new TextRun(line));
                    document.addElement(p);
                }
            }
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private void parseInlineRuns(String htmlSnippet, Paragraph target) {
        // Simple inline parser for <b>, <i>, <u>, <s>, <span>, <a>
        String text = htmlSnippet.replaceAll("<br\\s*/?>", "\n");
        // Decode common entities
        text = text.replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"");

        // Parse tags vs plain text
        Pattern tagPattern = Pattern.compile("<(/?[a-zA-Z0-9]+)([^>]*)>");
        Matcher m = tagPattern.matcher(text);

        int lastIdx = 0;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;

        while (m.find()) {
            if (m.start() > lastIdx) {
                String sub = text.substring(lastIdx, m.start());
                if (!sub.isEmpty()) {
                    target.addRun(new TextRun(sub, "Segoe UI", 11.0, bold, italic, underline, strike, Color.BLACK, null));
                }
            }
            String tagName = m.group(1).toLowerCase();
            switch (tagName) {
                case "b", "strong" -> bold = true;
                case "/b", "/strong" -> bold = false;
                case "i", "em" -> italic = true;
                case "/i", "/em" -> italic = false;
                case "u" -> underline = true;
                case "/u" -> underline = false;
                case "s", "strike", "del" -> strike = true;
                case "/s", "/strike", "/del" -> strike = false;
            }
            lastIdx = m.end();
        }

        if (lastIdx < text.length()) {
            String tail = text.substring(lastIdx);
            if (!tail.isEmpty()) {
                target.addRun(new TextRun(tail, "Segoe UI", 11.0, bold, italic, underline, strike, Color.BLACK, null));
            }
        }
    }
}
