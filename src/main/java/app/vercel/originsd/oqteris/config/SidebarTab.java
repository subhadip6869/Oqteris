package app.vercel.originsd.oqteris.config;

/**
 * Navigation sidebar tabs in the PDF viewer.
 */
public enum SidebarTab {
    THUMBNAILS(0, "Thumbnails"),
    BOOKMARKS(1, "Bookmarks"),
    SIGNATURES(2, "Signatures");

    private final int index;
    private final String title;

    SidebarTab(int index, String title) {
        this.index = index;
        this.title = title;
    }

    public int getIndex() {
        return index;
    }

    public String getTitle() {
        return title;
    }

    public static SidebarTab fromIndex(int index) {
        for (SidebarTab tab : values()) {
            if (tab.index == index) {
                return tab;
            }
        }
        return THUMBNAILS;
    }

    @Override
    public String toString() {
        return title;
    }
}
