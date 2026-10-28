package org.oqteris.pdf.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Node in the PDF document outline / bookmarks tree.
 */
public class PdfBookmarkNode {
    private final String title;
    private final int pageIndex;
    private final List<PdfBookmarkNode> children = new ArrayList<>();

    public PdfBookmarkNode(String title, int pageIndex) {
        this.title = title != null ? title : "Untitled";
        this.pageIndex = Math.max(0, pageIndex);
    }

    public String getTitle() {
        return title;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public List<PdfBookmarkNode> getChildren() {
        return children;
    }

    public void addChild(PdfBookmarkNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    @Override
    public String toString() {
        return title + " (Page " + (pageIndex + 1) + ")";
    }
}
