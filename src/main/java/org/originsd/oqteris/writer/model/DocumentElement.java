package org.originsd.oqteris.writer.model;

/**
 * Common interface for all content elements within a word processor document.
 */
public interface DocumentElement {
    enum ElementType {
        PARAGRAPH,
        TABLE,
        IMAGE,
        PAGE_BREAK
    }

    ElementType getElementType();
}
