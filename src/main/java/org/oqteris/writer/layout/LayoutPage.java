package org.oqteris.writer.layout;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.oqteris.writer.model.DocumentTableCell;
import org.oqteris.writer.model.PageSetup;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a fully paginated page containing text lines, tables, and images.
 */
public class LayoutPage {
    public static class TableCellBox {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final DocumentTableCell cell;
        public final List<LayoutLine> lines = new ArrayList<>();

        public TableCellBox(double x, double y, double width, double height, DocumentTableCell cell) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.cell = cell;
        }
    }

    public static class TableBox {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final Color borderColor;
        public final double borderWidth;
        public final List<TableCellBox> cells = new ArrayList<>();

        public TableBox(double x, double y, double width, double height, Color borderColor, double borderWidth) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.borderColor = borderColor;
            this.borderWidth = borderWidth;
        }
    }

    public static class ImageBox {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final Image image;

        public ImageBox(double x, double y, double width, double height, Image image) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.image = image;
        }
    }

    private final int pageIndex;
    private final PageSetup pageSetup;
    private final List<LayoutLine> lines = new ArrayList<>();
    private final List<TableBox> tableBoxes = new ArrayList<>();
    private final List<ImageBox> imageBoxes = new ArrayList<>();
    private LayoutLine headerLine = null;
    private LayoutLine footerLine = null;

    public LayoutPage(int pageIndex, PageSetup pageSetup) {
        this.pageIndex = pageIndex;
        this.pageSetup = pageSetup;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public PageSetup getPageSetup() {
        return pageSetup;
    }

    public double getWidth() {
        return pageSetup.getPageWidth();
    }

    public double getHeight() {
        return pageSetup.getPageHeight();
    }

    public List<LayoutLine> getLines() {
        return lines;
    }

    public void addLine(LayoutLine line) {
        if (line != null) {
            lines.add(line);
        }
    }

    public List<TableBox> getTableBoxes() {
        return tableBoxes;
    }

    public void addTableBox(TableBox box) {
        if (box != null) {
            tableBoxes.add(box);
        }
    }

    public List<ImageBox> getImageBoxes() {
        return imageBoxes;
    }

    public void addImageBox(ImageBox box) {
        if (box != null) {
            imageBoxes.add(box);
        }
    }

    public LayoutLine getHeaderLine() {
        return headerLine;
    }

    public void setHeaderLine(LayoutLine headerLine) {
        this.headerLine = headerLine;
    }

    public LayoutLine getFooterLine() {
        return footerLine;
    }

    public void setFooterLine(LayoutLine footerLine) {
        this.footerLine = footerLine;
    }
}
