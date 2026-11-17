package app.vercel.originsd.oqteris.pdf.render;

import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.DefaultResourceCache;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A memory-bounded resource cache for PDFBox documents that caps the number of cached
 * XObjects (embedded raster images and form XObjects) using an LRU eviction policy.
 *
 * <p>PDFBox's {@link DefaultResourceCache} retains all extracted XObjects via {@code SoftReference}s
 * in an unbounded map. In the HotSpot JVM, soft references are only collected when the heap is nearly
 * exhausted, causing memory to continuously grow as the user scrolls through multi-page documents.
 * This class ensures only a bounded working set of recent XObjects is retained, allowing unneeded
 * image raster data from scrolled-past pages to be garbage collected immediately.</p>
 */
public class BoundedResourceCache extends DefaultResourceCache {
    public static final int DEFAULT_MAX_XOBJECTS = 8;
    private final int maxXObjects;
    private final Map<COSObject, PDXObject> xobjects;

    public BoundedResourceCache() {
        this(DEFAULT_MAX_XOBJECTS);
    }

    public BoundedResourceCache(int maxXObjects) {
        this.maxXObjects = maxXObjects;
        this.xobjects = Collections.synchronizedMap(
                new LinkedHashMap<>(maxXObjects + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<COSObject, PDXObject> eldest) {
                        return size() > BoundedResourceCache.this.maxXObjects;
                    }
                }
        );
    }

    @Override
    public PDXObject getXObject(COSObject indirect) {
        if (indirect == null) return null;
        return xobjects.get(indirect);
    }

    @Override
    public void put(COSObject indirect, PDXObject xobject) {
        if (indirect != null && xobject != null) {
            xobjects.put(indirect, xobject);
        }
    }

    @Override
    public PDXObject removeXObject(COSObject indirect) {
        if (indirect == null) return null;
        return xobjects.remove(indirect);
    }

    public void clear() {
        xobjects.clear();
    }

    public int getCachedXObjectCount() {
        return xobjects.size();
    }
}
