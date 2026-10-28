package org.originsd.oqteris.writer.model;

/**
 * Hard page break element in a document flow.
 */
public class PageBreakElement implements DocumentElement {
    @Override
    public ElementType getElementType() {
        return ElementType.PAGE_BREAK;
    }
}
