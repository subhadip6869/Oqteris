package app.vercel.originsd.oqteris.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * User-configurable application preferences adhering to the OriginSD Settings Standard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettings {

    // Appearance & Typography
    private String theme = "SYSTEM"; // "LIGHT", "DARK", "SYSTEM"
    private int fontSizeDelta = 0;   // -4 to +12

    // App-specific preferences
    private String defaultAuthor = System.getProperty("user.name", "Oqteris User");
    private boolean autosaveEnabled = true;
    private int autosaveIntervalMinutes = 2;
    private boolean autoCheckUpdates = true;
    private boolean promptDefaultViewerOnStartup = true;
    private List<String> recentFilePaths = new ArrayList<>();

    // Window state & layout
    private boolean windowMaximized = false;
    private double windowWidth = 1200;
    private double windowHeight = 720;

    // Sidebar state
    private boolean sidebarOpen = true;
    private int sidebarActiveTab = 0; // 0 = Thumbnails, 1 = Bookmarks, 2 = Signatures

    // Document View & Zoom mode
    private ViewMode viewMode = ViewMode.FIT_WIDTH;
    private PageLayoutMode pageLayout = PageLayoutMode.CONTINUOUS;
    private double defaultZoom = 1.0;

    // Cursor / Interaction mode
    private CursorMode cursorMode = CursorMode.SELECT;

    public UserSettings() {}

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = (theme != null && !theme.isBlank()) ? theme.toUpperCase() : "SYSTEM";
    }

    public int getFontSizeDelta() {
        return fontSizeDelta;
    }

    public void setFontSizeDelta(int fontSizeDelta) {
        this.fontSizeDelta = Math.clamp(fontSizeDelta, -4, 12);
    }

    public String getDefaultAuthor() {
        return defaultAuthor;
    }

    public void setDefaultAuthor(String defaultAuthor) {
        this.defaultAuthor = defaultAuthor;
    }

    public boolean isAutosaveEnabled() {
        return autosaveEnabled;
    }

    public void setAutosaveEnabled(boolean autosaveEnabled) {
        this.autosaveEnabled = autosaveEnabled;
    }

    public int getAutosaveIntervalMinutes() {
        return autosaveIntervalMinutes;
    }

    public void setAutosaveIntervalMinutes(int autosaveIntervalMinutes) {
        this.autosaveIntervalMinutes = Math.max(1, autosaveIntervalMinutes);
    }

    public boolean isAutoCheckUpdates() {
        return autoCheckUpdates;
    }

    public void setAutoCheckUpdates(boolean autoCheckUpdates) {
        this.autoCheckUpdates = autoCheckUpdates;
    }

    public boolean isPromptDefaultViewerOnStartup() {
        return promptDefaultViewerOnStartup;
    }

    public void setPromptDefaultViewerOnStartup(boolean promptDefaultViewerOnStartup) {
        this.promptDefaultViewerOnStartup = promptDefaultViewerOnStartup;
    }

    public List<String> getRecentFilePaths() {
        if (recentFilePaths == null) {
            recentFilePaths = new ArrayList<>();
        }
        return recentFilePaths;
    }

    public void setRecentFilePaths(List<String> recentFilePaths) {
        this.recentFilePaths = recentFilePaths != null ? recentFilePaths : new ArrayList<>();
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double windowWidth) {
        if (windowWidth >= 400) {
            this.windowWidth = windowWidth;
        }
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double windowHeight) {
        if (windowHeight >= 300) {
            this.windowHeight = windowHeight;
        }
    }

    public boolean isSidebarOpen() {
        return sidebarOpen;
    }

    public void setSidebarOpen(boolean sidebarOpen) {
        this.sidebarOpen = sidebarOpen;
    }

    public int getSidebarActiveTab() {
        return sidebarActiveTab;
    }

    public void setSidebarActiveTab(int sidebarActiveTab) {
        this.sidebarActiveTab = Math.max(0, sidebarActiveTab);
    }

    public SidebarTab getSidebarTab() {
        return SidebarTab.fromIndex(sidebarActiveTab);
    }

    public void setSidebarTab(SidebarTab tab) {
        this.sidebarActiveTab = tab != null ? tab.getIndex() : 0;
    }

    public ViewMode getViewMode() {
        return viewMode != null ? viewMode : ViewMode.FIT_WIDTH;
    }

    public void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode != null ? viewMode : ViewMode.FIT_WIDTH;
    }

    public void setViewMode(String viewMode) {
        this.viewMode = ViewMode.fromString(viewMode);
    }

    public PageLayoutMode getPageLayout() {
        return pageLayout != null ? pageLayout : PageLayoutMode.CONTINUOUS;
    }

    public void setPageLayout(PageLayoutMode pageLayout) {
        this.pageLayout = pageLayout != null ? pageLayout : PageLayoutMode.CONTINUOUS;
    }

    public void setPageLayout(String pageLayout) {
        this.pageLayout = PageLayoutMode.fromString(pageLayout);
    }

    public double getDefaultZoom() {
        return defaultZoom;
    }

    public void setDefaultZoom(double defaultZoom) {
        this.defaultZoom = Math.clamp(defaultZoom, 0.25, 4.0);
    }

    public CursorMode getCursorMode() {
        return cursorMode != null ? cursorMode : CursorMode.SELECT;
    }

    public void setCursorMode(CursorMode cursorMode) {
        this.cursorMode = cursorMode != null ? cursorMode : CursorMode.SELECT;
    }

    public void setCursorMode(String cursorMode) {
        this.cursorMode = CursorMode.fromString(cursorMode);
    }
}
