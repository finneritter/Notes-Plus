// FILE: src/main/java/com/igcalc/gui/NotesScreen.java
package com.igcalc.gui;

import com.igcalc.config.IgCalcWindowState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import com.igcalc.config.IgCalcConfig;
import com.igcalc.config.IgCalcHudState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class NotesScreen extends Screen {

    // -------------------------------------------------------------------------
    // Window geometry constants
    // -------------------------------------------------------------------------
    private static final int WIN_MIN_W        = 220;
    private static final int WIN_MIN_H        = 160;
    private static final int TITLE_BAR_HEIGHT = 18;
    private static final int SIDEBAR_W        = 100;
    private static final int PADDING          = 5;
    private static final int LINE_HEIGHT      = 11;
    private static final int ITEM_HEIGHT      = 13;
    private static final int RESIZE_GRIP      = 6;

    // -------------------------------------------------------------------------
    // Colour palette — static (not user-configurable)
    // -------------------------------------------------------------------------
    private static final int COLOR_TITLE_TEXT    = 0xFFE5E5E5;
    private static final int COLOR_DOT_RED       = 0xFFFF5F57;
    private static final int COLOR_LINE_HL       = 0xFF252A3E;
    private static final int COLOR_CURSOR        = 0xFFFFFFFF;
    private static final int COLOR_SIDEBAR_HOVER = 0xFF232738;
    private static final int COLOR_DIVIDER       = 0xFF3A3F52;

    // Configurable colours — defaults, overwritten from IgCalcConfig in init()
    private int COLOR_BG               = 0xEE1C1E2B;
    private int COLOR_TITLE_BAR        = 0xFF262A3C;
    private int COLOR_BORDER           = 0xFF3A3F52;
    private int COLOR_STATUS           = 0xFF0A84FF;
    private int COLOR_SIDEBAR_BG       = 0xFF171A27;
    private int COLOR_SIDEBAR_SELECTED = 0xFF2A4070;
    // Context menu
    private static final int COLOR_MENU_BG          = 0xFF1C1E2B;
    private static final int COLOR_MENU_BORDER      = 0xFF3A3F52;
    private static final int COLOR_MENU_HOVER       = 0xFF252A3E;
    private static final int COLOR_MENU_DELETE      = 0xFFFF5F57;
    // Pin indicator
    private static final int COLOR_PIN              = 0xFFFEBC2E;
    // Context menu dimensions
    private static final int MENU_W                 = 92;
    private static final int MENU_ITEM_H            = 13;

    // -------------------------------------------------------------------------
    // Resize edge enum
    // -------------------------------------------------------------------------
    private enum ResizeEdge { NONE, RIGHT, BOTTOM, CORNER }

    // -------------------------------------------------------------------------
    // Window geometry
    // -------------------------------------------------------------------------
    private int winX, winY, winW, winH;

    // -------------------------------------------------------------------------
    // Window drag state
    // -------------------------------------------------------------------------
    /** Set by IgCalcOverlay to enable active/inactive title bar. */
    public IgCalcOverlay overlay;

    private boolean dragging = false;
    private int     dragOffX, dragOffY;

    // -------------------------------------------------------------------------
    // Resize state
    // -------------------------------------------------------------------------
    private ResizeEdge resizing          = ResizeEdge.NONE;
    private int        resizeStartMouseX, resizeStartMouseY;
    private int        resizeStartW,      resizeStartH;

    // -------------------------------------------------------------------------
    // Sidebar state
    // -------------------------------------------------------------------------
    private boolean      sidebarVisible;
    private List<String> allNoteFiles  = new ArrayList<>();
    private List<String> filteredFiles = new ArrayList<>();
    private List<String> pinnedFiles   = new ArrayList<>();
    private String       currentFile   = null;
    private int          sidebarScroll = 0;

    // -------------------------------------------------------------------------
    // Sidebar drag-to-reorder
    // -------------------------------------------------------------------------
    private static final int SIDEBAR_DRAG_THRESHOLD = 8; // px before drag starts
    private boolean sidebarDragging     = false;
    private String  sidebarDragItem     = null;
    private RowKind sidebarDragKind     = null;
    private int     sidebarDragCurrentY = 0;
    private int     sidebarClickOriginX = 0;
    private int     sidebarClickOriginY = 0;
    private String  lastSidebarClickFolder = null;

    // -------------------------------------------------------------------------
    // Context menu
    // -------------------------------------------------------------------------
    private boolean contextMenuVisible   = false;
    private String  contextMenuFile      = null;
    private boolean contextMenuSidebarBg = false;  // right-click on empty sidebar area
    private int     contextMenuX, contextMenuY;

    // -------------------------------------------------------------------------
    // Editor context menu (right-click in text area)
    // -------------------------------------------------------------------------
    private static final int EDITOR_MENU_W = 120;
    private static final int SEPARATOR_H   = 7;
    private boolean editorContextMenuVisible = false;
    private int     editorContextMenuX, editorContextMenuY;

    // -------------------------------------------------------------------------
    // Keybind reference card
    // -------------------------------------------------------------------------
    private boolean keybindCardVisible = false;

    // -------------------------------------------------------------------------
    // Search state
    // -------------------------------------------------------------------------
    private boolean         searchActive = false;
    private String          searchQuery  = "";
    private TextFieldWidget searchField;

    // -------------------------------------------------------------------------
    // Rename state
    // -------------------------------------------------------------------------
    private boolean         renaming             = false;
    private String          renamingFile         = null;
    private TextFieldWidget renameField;
    private long            lastSidebarClickMs   = 0;
    private String          lastSidebarClickFile = null;

    /** When set, called instead of super.close() — used by IgCalcOverlay. */
    public Runnable closeCallback = null;

    // -------------------------------------------------------------------------
    // Editor state
    // -------------------------------------------------------------------------
    private final List<String> lines = new ArrayList<>();
    private int  cursorLine   = 0;
    private int  cursorCol    = 0;
    private int  scrollOffset = 0;
    private long lastBlink    = System.currentTimeMillis();

    // -------------------------------------------------------------------------
    // Selection state
    // -------------------------------------------------------------------------
    private int selAnchorLine = -1;
    private int selAnchorCol  = 0;

    // -------------------------------------------------------------------------
    // Visual-row cache
    // -------------------------------------------------------------------------
    private record VisualRow(int logLine, int startCol, int endCol,
                             float scale, int heightPx, boolean inCodeBlock, int wrapIndentPx) {}
    private final List<VisualRow> visualRows = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Status / autosave
    // -------------------------------------------------------------------------
    private String statusMessage = "";
    private long   statusTimer   = 0;
    private long   lastEditMs    = 0;
    private static final long AUTOSAVE_DELAY_MS = 2000;

    // -------------------------------------------------------------------------
    // Sidebar keyboard navigation
    // -------------------------------------------------------------------------
    private boolean sidebarFocused   = false;
    private int     sidebarCursorIdx = 0;

    // -------------------------------------------------------------------------
    // Global quick note: set before init() to create a new quick note on open
    // -------------------------------------------------------------------------
    public boolean pendingQuickNote = false;

    // -------------------------------------------------------------------------
    // Content search results (sidebar "in content" section)
    // -------------------------------------------------------------------------
    private List<String> contentMatchFiles = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Export menu state
    // -------------------------------------------------------------------------
    private boolean exportMenuVisible = false;
    private int     exportMenuX, exportMenuY;

    // -------------------------------------------------------------------------
    // Note link clickable regions — rebuilt each render
    // -------------------------------------------------------------------------
    private record ClickableRegion(int x1, int y1, int x2, int y2, String target) {}
    private final List<ClickableRegion> clickableRegions = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Performance caches
    // -------------------------------------------------------------------------
    /** Incremented on every lines mutation; rebuildVisualRows() is a no-op when unchanged. */
    private int  linesVersion          = 0;
    private int  lastBuiltLinesVersion = -1;
    private int  lastBuiltContentWidth = -1;
    /** Sidebar row list is rebuilt lazily; set to true whenever it may be stale. */
    private boolean            sidebarRowsDirty = true;
    private List<SidebarEntry> sidebarRowsCache = new ArrayList<>();
    /** Parsed inline-markdown text, keyed by raw display string. Cleared on note switch. */
    private final Map<String, MutableText> markdownCache = new HashMap<>();

    // -------------------------------------------------------------------------
    // Folder state
    // -------------------------------------------------------------------------
    private Set<String> expandedFolders = new LinkedHashSet<>();
    /** Folders that exist on disk even if they contain no notes (shown in sidebar). */
    private Set<String> knownFolders    = new LinkedHashSet<>();
    private String      contextMenuFolder = null;
    private String creatingSubfolderInFolder = null;

    // -------------------------------------------------------------------------
    // World context name (shown in sidebar header)
    // -------------------------------------------------------------------------
    private String worldContextName = "~/files";

    // -------------------------------------------------------------------------
    // Creating-folder state (inline TextFieldWidget)
    // -------------------------------------------------------------------------
    private boolean creatingFolder    = false;
    private String  creatingFolderFor = null;

    // -------------------------------------------------------------------------
    // Renaming-folder state
    // -------------------------------------------------------------------------
    private boolean renamingFolder    = false;
    private String  renamingFolderOld = null;

    // -------------------------------------------------------------------------
    // Sidebar virtual row types
    // -------------------------------------------------------------------------
    private enum RowKind { FILE, FOLDER, CONTENT_HEADER, NAME_HEADER, SPACER }
    private record SidebarEntry(RowKind kind, String value, boolean inFolder, int depth) {}

    // =========================================================================
    // Constructor
    // =========================================================================
    public NotesScreen() {
        super(Text.translatable("igcalc.title.notes"));
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================
    private int contentX()     { return PADDING + (sidebarVisible ? SIDEBAR_W + 1 : 0); }
    private int contentWidth() { return winW - contentX() - PADDING; }
    private int textAreaY()    { return TITLE_BAR_HEIGHT + PADDING; }
    private int textAreaH()    { return winH - textAreaY() - 8; }

    private static float getLineScale(String line) {
        if (line.startsWith("#### ")) return 1.25f;
        if (line.startsWith("### "))  return 1.5f;
        if (line.startsWith("## "))   return 1.75f;
        if (line.startsWith("# "))    return 2.0f;
        return 1.0f;
    }

    private int styledWidth(String text) {
        return textRenderer.getWidth(parseInlineMarkdown(text));
    }

    private static int rawContentStart(String line) {
        if (line.startsWith("  - [ ] ") || line.startsWith("  - [x] ")) return 8;
        if (line.startsWith("  - [ ]")  || line.startsWith("  - [x]"))  return 7;
        if (line.startsWith("  - ")     || line.startsWith("  * "))      return 4;
        if (line.startsWith("- [ ] ")   || line.startsWith("- [x] "))    return 6;
        if (line.startsWith("- [ ]")    || line.startsWith("- [x]"))     return 5;
        if (line.startsWith("* ")       || line.startsWith("- "))        return 2;
        if (line.startsWith("> "))                                        return 2;
        if (line.startsWith("#### "))   return 5;
        if (line.startsWith("### "))    return 4;
        if (line.startsWith("## "))     return 3;
        if (line.startsWith("# "))      return 2;
        return 0;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================
    @Override
    protected void init() {
        IgCalcWindowState.load();
        winW = Math.max(WIN_MIN_W, IgCalcWindowState.notesW);
        winH = Math.max(WIN_MIN_H, IgCalcWindowState.notesH);
        sidebarVisible = IgCalcWindowState.notesSidebar;

        if (IgCalcWindowState.notesX == -1) {
            winX = (this.width  - winW) / 2;
            winY = (this.height - winH) / 2;
        } else {
            winX = Math.max(0, Math.min(this.width  - winW, IgCalcWindowState.notesX));
            winY = Math.max(0, Math.min(this.height - winH, IgCalcWindowState.notesY));
        }

        IgCalcConfig cfg = IgCalcConfig.getInstance();
        cfg.load();
        COLOR_BG               = cfg.colorBg;
        COLOR_TITLE_BAR        = cfg.colorTitleBar;
        COLOR_STATUS           = cfg.colorAccent;
        COLOR_SIDEBAR_BG       = cfg.colorSidebarBg;
        COLOR_SIDEBAR_SELECTED = cfg.colorSidebarSelect;
        COLOR_BORDER           = cfg.colorBorder;

        // Compute world context name for sidebar header
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() != null) {
            try {
                String levelName = mc.getServer().getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString();
                worldContextName = levelName;
            } catch (Exception e) {
                worldContextName = "singleplayer";
            }
        } else if (mc.getCurrentServerEntry() != null) {
            worldContextName = "mp: " + mc.getCurrentServerEntry().address;
        } else {
            worldContextName = "multiplayer";
        }

        rebuildWidgets();
        loadNotesList();
        if (IgCalcWindowState.notesLastFile != null
                && allNoteFiles.contains(IgCalcWindowState.notesLastFile)) {
            currentFile = IgCalcWindowState.notesLastFile;
        } else if (currentFile == null || !allNoteFiles.contains(currentFile)) {
            currentFile = filteredFiles.isEmpty() ? null : filteredFiles.get(0);
        }
        loadCurrentNote();
        if (pendingQuickNote) { pendingQuickNote = false; triggerQuickNote(); }
        if (client != null) client.mouse.unlockCursor();
    }

    @Override
    public void tick() {
        super.tick();
        if (client != null) client.mouse.unlockCursor();
        if (lastEditMs > 0 && System.currentTimeMillis() - lastEditMs >= AUTOSAVE_DELAY_MS) {
            saveCurrentNote();
            lastEditMs = 0;
        }
    }

    // =========================================================================
    // Widget construction
    // =========================================================================
    private void rebuildWidgets() {
        clearChildren();

        if (sidebarVisible) {
            addDrawableChild(ButtonWidget.builder(Text.literal("+ New"), btn -> {
                newNote();
                rebuildWidgets();
            }).dimensions(winX + PADDING, winY + winH - 16, SIDEBAR_W - 2, 12).build());
        }

        if (searchActive && sidebarVisible) {
            searchField = new TextFieldWidget(
                    textRenderer, winX + PADDING, winY + TITLE_BAR_HEIGHT + 2,
                    SIDEBAR_W - 4, 12, Text.literal("Search"));
            searchField.setMaxLength(40);
            searchField.setText(searchQuery);
            searchField.setChangedListener(text -> { searchQuery = text; applySearch(); });
            addDrawableChild(searchField);
            setFocused(searchField);
        }

        if (renaming && renamingFile != null && sidebarVisible) {
            int idx = rowIndexForFile(renamingFile);
            if (idx >= sidebarScroll) {
                int listStartY = winY + TITLE_BAR_HEIGHT + 16;
                int itemY      = listStartY + (idx - sidebarScroll) * ITEM_HEIGHT;
                int listBottom = winY + winH - 20;
                if (itemY + ITEM_HEIGHT <= listBottom) {
                    renameField = new TextFieldWidget(
                            textRenderer, winX + PADDING + 1, itemY + 1,
                            SIDEBAR_W - 2, ITEM_HEIGHT - 1, Text.literal("Rename"));
                    renameField.setMaxLength(60);
                    renameField.setText(noteNameDisplay(renamingFile));
                    addDrawableChild(renameField);
                    setFocused(renameField);
                }
            }
        }

        if (renamingFolder && renamingFolderOld != null && sidebarVisible) {
            int idx = rowIndexForFolder(renamingFolderOld);
            if (idx >= sidebarScroll) {
                int listStartY = winY + TITLE_BAR_HEIGHT + 16;
                int itemY      = listStartY + (idx - sidebarScroll) * ITEM_HEIGHT;
                int listBottom = winY + winH - 20;
                if (itemY + ITEM_HEIGHT <= listBottom) {
                    renameField = new TextFieldWidget(
                            textRenderer, winX + PADDING + 1, itemY + 1,
                            SIDEBAR_W - 2, ITEM_HEIGHT - 1, Text.literal("Rename folder"));
                    renameField.setMaxLength(60);
                    renameField.setText(renamingFolderOld);
                    addDrawableChild(renameField);
                    setFocused(renameField);
                }
            }
        }

        if (creatingFolder && sidebarVisible) {
            if (creatingFolderFor != null) {
                int idx = rowIndexForFile(creatingFolderFor);
                if (idx >= sidebarScroll) {
                    int listStartY = winY + TITLE_BAR_HEIGHT + 16;
                    int itemY      = listStartY + (idx - sidebarScroll) * ITEM_HEIGHT;
                    int listBottom = winY + winH - 20;
                    if (itemY + ITEM_HEIGHT <= listBottom) {
                        renameField = new TextFieldWidget(
                                textRenderer, winX + PADDING + 1, itemY + 1,
                                SIDEBAR_W - 2, ITEM_HEIGHT - 1, Text.literal("Folder name"));
                        renameField.setMaxLength(60);
                        addDrawableChild(renameField);
                        setFocused(renameField);
                    }
                }
            } else {
                // Standalone folder creation: show at top of list
                int itemY = winY + TITLE_BAR_HEIGHT + 16;
                renameField = new TextFieldWidget(
                        textRenderer, winX + PADDING + 1, itemY + 1,
                        SIDEBAR_W - 2, ITEM_HEIGHT - 1, Text.literal("Folder name"));
                renameField.setMaxLength(60);
                addDrawableChild(renameField);
                setFocused(renameField);
            }
        }
    }

    /** Returns the virtual sidebar row index for a file path, or -1. */
    private int rowIndexForFile(String filePath) {
        List<SidebarEntry> rows = buildSidebarRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == RowKind.FILE && rows.get(i).value().equals(filePath)) return i;
        }
        return -1;
    }

    /** Returns the virtual sidebar row index for a folder name, or -1. */
    private int rowIndexForFolder(String folderName) {
        List<SidebarEntry> rows = buildSidebarRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == RowKind.FOLDER && rows.get(i).value().equals(folderName)) return i;
        }
        return -1;
    }

    // =========================================================================
    // Path helpers
    // =========================================================================
    private Path getNotesDir() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path base = (mc.getServer() != null)
                ? mc.getServer().getSavePath(WorldSavePath.ROOT)
                : mc.runDirectory.toPath();
        return base.resolve("notes+_notes");
    }

    private Path getNotePath(String filename) { return getNotesDir().resolve(filename); }
    private Path getOrderFile()               { return getNotesDir().resolve(".order");  }
    private Path getPinnedFile()              { return getNotesDir().resolve(".pinned"); }

    // =========================================================================
    // Note list management
    // =========================================================================
    private void loadNotesList() {
        try {
            Path newDir = getNotesDir();
            Path oldDir = newDir.resolveSibling("igcalc_notes");
            if (!Files.exists(newDir) && Files.exists(oldDir)) {
                try { Files.move(oldDir, newDir); } catch (IOException ignored) {}
            }
            Files.createDirectories(getNotesDir());
            List<String> diskFiles;
            try (var stream = Files.walk(getNotesDir(), 5)) {
                diskFiles = stream
                        .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".txt"))
                        .map(p -> getNotesDir().relativize(p).toString().replace('\\', '/'))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            // Restore saved order; append any new files at the end
            List<String> savedOrder = new ArrayList<>();
            if (Files.exists(getOrderFile())) {
                Files.readAllLines(getOrderFile(), StandardCharsets.UTF_8).stream()
                        .map(String::trim).filter(l -> !l.isEmpty())
                        .forEach(savedOrder::add);
            }
            allNoteFiles = new ArrayList<>();
            for (String f : savedOrder) if (diskFiles.contains(f)) allNoteFiles.add(f);
            for (String f : diskFiles)  if (!allNoteFiles.contains(f)) allNoteFiles.add(f);
            // Auto-expand all folders found via files
            expandedFolders.clear();
            for (String f : allNoteFiles) {
                String[] parts = f.split("/");
                String path = "";
                for (int pi = 0; pi < parts.length - 1; pi++) {
                    path = path.isEmpty() ? parts[pi] : path + "/" + parts[pi];
                    expandedFolders.add(path);
                }
            }
            // Discover all subdirectories (including empty ones), arbitrary depth
            knownFolders.clear();
            try (var walkStream = Files.walk(getNotesDir())) {
                walkStream.filter(p -> Files.isDirectory(p) && !p.equals(getNotesDir())
                                    && !p.getFileName().toString().startsWith("."))
                          .forEach(p -> {
                              String rel = getNotesDir().relativize(p).toString().replace('\\', '/');
                              knownFolders.add(rel);
                              expandedFolders.add(rel); // expand by default
                          });
            }
        } catch (IOException e) {
            allNoteFiles = new ArrayList<>();
        }
        loadPinnedFiles();
        if (allNoteFiles.isEmpty()) createNote("Note 1.txt");
        applySearch();
    }

    private void loadPinnedFiles() {
        pinnedFiles.clear();
        try {
            if (Files.exists(getPinnedFile())) {
                Files.readAllLines(getPinnedFile(), StandardCharsets.UTF_8).stream()
                        .map(String::trim).filter(l -> !l.isEmpty())
                        .forEach(pinnedFiles::add);
            }
        } catch (IOException ignored) {}
    }

    private void savePinnedFiles() {
        try {
            Files.writeString(getPinnedFile(), String.join("\n", pinnedFiles),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void saveOrder() {
        try {
            Files.writeString(getOrderFile(), String.join("\n", allNoteFiles),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void applySearch() {
        List<String> base;
        if (searchQuery.isEmpty()) {
            base = new ArrayList<>(allNoteFiles);
        } else {
            String q = searchQuery.toLowerCase();
            base = allNoteFiles.stream()
                    .filter(f -> noteNameDisplay(f).toLowerCase().contains(q))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        // Pinned files first (preserving allNoteFiles order within each group)
        filteredFiles = new ArrayList<>();
        for (String f : base) if ( pinnedFiles.contains(f)) filteredFiles.add(f);
        for (String f : base) if (!pinnedFiles.contains(f)) filteredFiles.add(f);

        // Content matches: files not already in filteredFiles that contain the query text
        contentMatchFiles.clear();
        if (!searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            for (String f : allNoteFiles) {
                if (filteredFiles.contains(f)) continue;
                try {
                    String text = Files.readString(getNotePath(f), StandardCharsets.UTF_8);
                    if (text.toLowerCase().contains(q)) contentMatchFiles.add(f);
                } catch (IOException ignored) {}
            }
        }

        sidebarRowsDirty = true;
        int totalRows = buildSidebarRows().size();
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, totalRows - 1)));
    }

    // =========================================================================
    // Sidebar virtual row builder
    // =========================================================================
    private List<SidebarEntry> buildSidebarRows() {
        if (!sidebarRowsDirty) return sidebarRowsCache;
        sidebarRowsDirty = false;
        List<SidebarEntry> rows = new ArrayList<>();
        if (!searchQuery.isEmpty()) {
            // Flat search results: name matches section, then content section
            if (!filteredFiles.isEmpty()) {
                rows.add(new SidebarEntry(RowKind.SPACER, "", false, 0));
                rows.add(new SidebarEntry(RowKind.NAME_HEADER, "", false, 0));
                for (String f : filteredFiles) rows.add(new SidebarEntry(RowKind.FILE, f, false, 0));
            }
            if (!contentMatchFiles.isEmpty()) {
                rows.add(new SidebarEntry(RowKind.SPACER, "", false, 0));
                rows.add(new SidebarEntry(RowKind.CONTENT_HEADER, "", false, 0));
                for (String f : contentMatchFiles) rows.add(new SidebarEntry(RowKind.FILE, f, false, 0));
            }
        } else {
            // Recursive tree
            addTreeRows(rows, "", 0);
            // Root files — pinned first
            List<String> rootFiles = allNoteFiles.stream()
                    .filter(f -> !f.contains("/"))
                    .collect(java.util.stream.Collectors.toList());
            for (String f : rootFiles) if ( pinnedFiles.contains(f)) rows.add(new SidebarEntry(RowKind.FILE, f, false, 0));
            for (String f : rootFiles) if (!pinnedFiles.contains(f)) rows.add(new SidebarEntry(RowKind.FILE, f, false, 0));
        }
        sidebarRowsCache = rows;
        return rows;
    }

    private void addTreeRows(List<SidebarEntry> rows, String parentPath, int depth) {
        String prefix = parentPath.isEmpty() ? "" : parentPath + "/";
        // Direct sub-folders that are immediate children of parentPath
        knownFolders.stream()
            .filter(f -> f.startsWith(prefix) && !f.substring(prefix.length()).contains("/"))
            .sorted()
            .forEach(folder -> {
                rows.add(new SidebarEntry(RowKind.FOLDER, folder, !parentPath.isEmpty(), depth));
                if (expandedFolders.contains(folder)) {
                    addTreeRows(rows, folder, depth + 1);
                    // Files directly in this folder (not in any subfolder of it)
                    for (String f : allNoteFiles) {
                        if (f.startsWith(folder + "/")) {
                            String rest = f.substring(folder.length() + 1);
                            if (!rest.contains("/"))
                                rows.add(new SidebarEntry(RowKind.FILE, f, true, depth + 1));
                        }
                    }
                }
            });
    }

    private String noteNameDisplay(String filename) {
        return filename.endsWith(".txt") ? filename.substring(0, filename.length() - 4) : filename;
    }

    private void createNote(String filename) {
        try {
            Files.createDirectories(getNotesDir());
            Path p = getNotePath(filename);
            if (!Files.exists(p)) Files.writeString(p, "", StandardCharsets.UTF_8);
            if (!allNoteFiles.contains(filename)) allNoteFiles.add(filename);
            saveOrder();
            applySearch();
        } catch (IOException ignored) {}
    }

    private void newNote() {
        saveCurrentNote();
        int n = 1;
        String name;
        do { name = "Note " + n + ".txt"; n++; } while (allNoteFiles.contains(name));
        createNote(name);
        currentFile = name;
        lines.clear();
        lines.add("");
        markLinesChanged();
        markdownCache.clear();
        cursorLine = 0; cursorCol = 0; scrollOffset = 0;
        setStatus("Created " + noteNameDisplay(name));
    }

    // =========================================================================
    // Public accessors (used by HudRenderer.pinNotes)
    // =========================================================================
    public String       getCurrentFile()  { return currentFile; }
    public List<String> getLines()        { return java.util.Collections.unmodifiableList(lines); }
    public int          getScrollOffset() { return scrollOffset; }
    public List<String> getAllFiles()      { return Collections.unmodifiableList(allNoteFiles); }
    public Path         getNotesDirPath() { return getNotesDir(); }
    public String       getWorldContextName() { return worldContextName; }

    public void triggerQuickNote() {
        saveCurrentNote();
        IgCalcWindowState.notesLastFile = currentFile;
        IgCalcWindowState.save();
        LocalDateTime now = LocalDateTime.now();
        String name = String.format("Quick Note %d-%02d-%02d %02d%02d.txt",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute());
        // Ensure unique if created multiple times in same minute
        if (allNoteFiles.contains(name)) {
            String base = name.substring(0, name.length() - 4);
            int n = 2;
            while (allNoteFiles.contains(base + " (" + n + ").txt")) n++;
            name = base + " (" + n + ").txt";
        }
        createNote(name);
        currentFile = name;
        lines.clear(); lines.add("");
        markLinesChanged();
        markdownCache.clear();
        cursorLine = 0; cursorCol = 0; scrollOffset = 0;
        sidebarVisible = true;
        rebuildWidgets();
        renaming = true;
        renamingFile = name;
        rebuildWidgets();
        if (renameField != null) renameField.setText("");
    }

    public void insertPlayerCoords() {
        if (client.player == null || client.world == null) return;
        int x = client.player.getBlockX();
        int y = client.player.getBlockY();
        int z = client.player.getBlockZ();
        String dim;
        var key = client.world.getRegistryKey();
        if (key == World.OVERWORLD)    dim = "Overworld";
        else if (key == World.NETHER)  dim = "Nether";
        else if (key == World.END)     dim = "The End";
        else                           dim = key.getValue().toString();
        String coords = x + ", " + y + ", " + z + " (" + dim + ")";
        if (hasSelection()) deleteSelection();
        String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorCol) + coords + line.substring(cursorCol));
        cursorCol += coords.length();
        markLinesChanged();
        lastEditMs = System.currentTimeMillis();
        scrollToCursor();
    }

    /** Resolves a [[note name]] to a file in allNoteFiles. Returns null if not found. */
    private String resolveNoteLink(String name) {
        if (name == null || name.isEmpty()) return null;
        String target = name.endsWith(".txt") ? name : name + ".txt";
        // Exact match (full path)
        if (allNoteFiles.contains(target)) return target;
        // Match by filename only (last component)
        for (String f : allNoteFiles) {
            String fn = Path.of(f).getFileName().toString();
            if (fn.equals(target)) return f;
        }
        // Case-insensitive match
        String lower = target.toLowerCase();
        for (String f : allNoteFiles) {
            String fn = Path.of(f).getFileName().toString().toLowerCase();
            if (fn.equals(lower)) return f;
        }
        return null;
    }

    /** Inserts the given text at the current cursor position. Used by calc→notes bridge. */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        if (hasSelection()) deleteSelection();
        String[] parts = text.split("\n", -1);
        if (parts.length == 1) {
            String line = lines.get(cursorLine);
            lines.set(cursorLine, line.substring(0, cursorCol) + parts[0] + line.substring(cursorCol));
            cursorCol += parts[0].length();
        } else {
            String before = lines.get(cursorLine).substring(0, cursorCol);
            String after  = lines.get(cursorLine).substring(cursorCol);
            lines.set(cursorLine, before + parts[0]);
            for (int i = 1; i < parts.length - 1; i++) lines.add(cursorLine + i, parts[i]);
            lines.add(cursorLine + parts.length - 1, parts[parts.length - 1] + after);
            cursorLine += parts.length - 1;
            cursorCol = parts[parts.length - 1].length();
        }
        markLinesChanged();
        lastEditMs = System.currentTimeMillis();
        scrollToCursor();
    }

    private void deleteNote(String filename) {
        try { Files.deleteIfExists(getNotePath(filename)); } catch (IOException ignored) {}
        allNoteFiles.remove(filename);
        pinnedFiles.remove(filename);
        applySearch();
        if (filename.equals(currentFile)) {
            currentFile = filteredFiles.isEmpty() ? null : filteredFiles.get(0);
            loadCurrentNote();
        }
        saveOrder();
        savePinnedFiles();
        setStatus("Deleted.");
    }

    private void renameNote(String oldFilename, String rawInput) {
        String newName = rawInput.trim();
        if (newName.isEmpty()) { cancelRename(); return; }
        if (newName.toLowerCase().endsWith(".txt"))
            newName = newName.substring(0, newName.length() - 4).trim();
        if (newName.isEmpty()) { cancelRename(); return; }
        newName = newName + ".txt";
        if (newName.equals(oldFilename)) { cancelRename(); return; }
        if (allNoteFiles.contains(newName)) { setStatus("Name already taken."); return; }
        try {
            Files.move(getNotePath(oldFilename), getNotePath(newName));
            int idx = allNoteFiles.indexOf(oldFilename);
            if (idx >= 0) allNoteFiles.set(idx, newName);
            int pidx = pinnedFiles.indexOf(oldFilename);
            if (pidx >= 0) pinnedFiles.set(pidx, newName);
            if (oldFilename.equals(currentFile)) currentFile = newName;
            saveOrder();
            savePinnedFiles();
            applySearch();
            setStatus("Renamed.");
        } catch (IOException e) {
            setStatus("Rename failed.");
        }
        cancelRename();
    }

    private void cancelRename() {
        renaming = false; renamingFile = null; renameField = null;
        rebuildWidgets();
    }

    // =========================================================================
    // Folder management
    // =========================================================================
    private void createFolderAndMoveFile(String rawFolderName, String filename) {
        String folderName = rawFolderName.trim().replace("/", "").replace("\\", "");
        if (folderName.isEmpty()) { cancelCreatingFolder(); return; }
        if (filename == null) {
            // Just create an empty folder (from the sidebar background menu or new subfolder)
            String fullFolderPath = (creatingSubfolderInFolder != null)
                    ? creatingSubfolderInFolder + "/" + folderName
                    : folderName;
            try {
                Files.createDirectories(getNotesDir().resolve(fullFolderPath));
                knownFolders.add(fullFolderPath);
                expandedFolders.add(fullFolderPath);
                if (creatingSubfolderInFolder != null) expandedFolders.add(creatingSubfolderInFolder);
                applySearch();
                setStatus("Created " + fullFolderPath + "/");
            } catch (IOException e) { setStatus("Failed to create folder."); }
            creatingSubfolderInFolder = null;
            cancelCreatingFolder();
            return;
        }
        try {
            Path dir = getNotesDir().resolve(folderName);
            Files.createDirectories(dir);
            String baseName   = Path.of(filename).getFileName().toString();
            String newRelPath = folderName + "/" + baseName;
            if (!allNoteFiles.contains(newRelPath)) {
                Files.move(getNotePath(filename), dir.resolve(baseName));
                int idx = allNoteFiles.indexOf(filename);
                if (idx >= 0) allNoteFiles.set(idx, newRelPath);
                if (filename.equals(currentFile)) currentFile = newRelPath;
                int pidx = pinnedFiles.indexOf(filename);
                if (pidx >= 0) { pinnedFiles.set(pidx, newRelPath); savePinnedFiles(); }
                expandedFolders.add(folderName);
                saveOrder();
                applySearch();
                setStatus("Moved to " + folderName + "/");
            }
        } catch (IOException e) {
            setStatus("Failed to create folder.");
        }
        cancelCreatingFolder();
    }

    private void cancelCreatingFolder() {
        creatingFolder = false; creatingFolderFor = null; creatingSubfolderInFolder = null; renameField = null;
        rebuildWidgets();
    }

    private void deleteFolder(String folderName) {
        List<String> toMove = allNoteFiles.stream()
                .filter(f -> f.startsWith(folderName + "/"))
                .collect(Collectors.toList());
        for (String f : toMove) {
            String baseName = Path.of(f).getFileName().toString();
            try {
                Files.move(getNotePath(f), getNotesDir().resolve(baseName),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
            int idx = allNoteFiles.indexOf(f);
            if (idx >= 0) allNoteFiles.set(idx, baseName);
            if (f.equals(currentFile)) currentFile = baseName;
            int pidx = pinnedFiles.indexOf(f);
            if (pidx >= 0) pinnedFiles.set(pidx, baseName);
        }
        try {
            Path dir = getNotesDir().resolve(folderName);
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (var s = Files.list(dir)) {
                    s.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException ignored) {}
        expandedFolders.remove(folderName);
        knownFolders.remove(folderName);
        savePinnedFiles();
        saveOrder();
        applySearch();
        setStatus("Folder deleted.");
    }

    private void renameFolder(String oldName, String rawInput) {
        String newName = rawInput.trim().replace("/", "").replace("\\", "");
        if (newName.isEmpty() || newName.equals(oldName)) { cancelRenameFolder(); return; }
        try {
            Files.move(getNotesDir().resolve(oldName), getNotesDir().resolve(newName));
            for (int i = 0; i < allNoteFiles.size(); i++) {
                String f = allNoteFiles.get(i);
                if (f.startsWith(oldName + "/")) {
                    String np = newName + "/" + f.substring(oldName.length() + 1);
                    allNoteFiles.set(i, np);
                    if (f.equals(currentFile)) currentFile = np;
                }
            }
            for (int i = 0; i < pinnedFiles.size(); i++) {
                String f = pinnedFiles.get(i);
                if (f.startsWith(oldName + "/"))
                    pinnedFiles.set(i, newName + "/" + f.substring(oldName.length() + 1));
            }
            if (expandedFolders.remove(oldName)) expandedFolders.add(newName);
            if (knownFolders.remove(oldName))    knownFolders.add(newName);
            savePinnedFiles();
            saveOrder();
            applySearch();
            setStatus("Renamed.");
        } catch (IOException e) {
            setStatus("Rename failed.");
        }
        cancelRenameFolder();
    }

    private void cancelRenameFolder() {
        renamingFolder = false; renamingFolderOld = null; renameField = null;
        rebuildWidgets();
    }

    /** Move an existing file into an existing folder (drag-and-drop target). */
    private void moveFileToFolder(String filename, String folderName) {
        try {
            Path dir = getNotesDir().resolve(folderName);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String baseName   = Path.of(filename).getFileName().toString();
            String newRelPath = folderName + "/" + baseName;
            if (allNoteFiles.contains(newRelPath)) { setStatus("Name conflict in " + folderName + "/"); return; }
            Files.move(getNotePath(filename), dir.resolve(baseName));
            int idx = allNoteFiles.indexOf(filename);
            if (idx >= 0) allNoteFiles.set(idx, newRelPath);
            if (filename.equals(currentFile)) currentFile = newRelPath;
            int pidx = pinnedFiles.indexOf(filename);
            if (pidx >= 0) { pinnedFiles.set(pidx, newRelPath); savePinnedFiles(); }
            expandedFolders.add(folderName);
            saveOrder();
            applySearch();
            setStatus("Moved to " + folderName + "/");
        } catch (IOException e) {
            setStatus("Move failed.");
        }
    }

    /** Get the parent folder of a path, or null if at root. */
    private String getParentFolder(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? null : path.substring(0, idx);
    }

    /** Move a file from a folder back to root. */
    private void moveFileToRoot(String filename) {
        try {
            String baseName = Path.of(filename).getFileName().toString();
            if (allNoteFiles.stream().anyMatch(f -> f.equals(baseName))) {
                setStatus("Name conflict at root"); return;
            }
            Files.move(getNotePath(filename), getNotesDir().resolve(baseName));
            int idx = allNoteFiles.indexOf(filename);
            if (idx >= 0) allNoteFiles.set(idx, baseName);
            if (filename.equals(currentFile)) currentFile = baseName;
            int pidx = pinnedFiles.indexOf(filename);
            if (pidx >= 0) { pinnedFiles.set(pidx, baseName); savePinnedFiles(); }
            saveOrder();
            applySearch();
            setStatus("Moved to root");
        } catch (IOException e) {
            setStatus("Move failed.");
        }
    }

    /** Move an entire folder into another folder. */
    private void moveFolderInto(String sourceFolder, String targetFolder) {
        try {
            Path srcDir = getNotesDir().resolve(sourceFolder);
            String folderLeaf = Path.of(sourceFolder).getFileName().toString();
            String newFolderPath = targetFolder + "/" + folderLeaf;
            Path destDir = getNotesDir().resolve(newFolderPath);
            if (Files.exists(destDir)) { setStatus("Folder conflict in " + targetFolder + "/"); return; }
            Files.createDirectories(destDir.getParent());
            Files.move(srcDir, destDir);
            updateFolderReferences(sourceFolder, newFolderPath);
            expandedFolders.add(targetFolder);
            savePinnedFiles();
            saveOrder();
            applySearch();
            setStatus("Moved " + folderLeaf + " \u2192 " + targetFolder + "/");
        } catch (IOException e) {
            setStatus("Move failed.");
        }
    }

    /** Move a nested folder to root level. */
    private void moveFolderToRoot(String nestedFolder) {
        try {
            Path srcDir = getNotesDir().resolve(nestedFolder);
            String folderLeaf = Path.of(nestedFolder).getFileName().toString();
            if (knownFolders.contains(folderLeaf)) { setStatus("Folder '" + folderLeaf + "' exists at root"); return; }
            Path destDir = getNotesDir().resolve(folderLeaf);
            Files.move(srcDir, destDir);
            updateFolderReferences(nestedFolder, folderLeaf);
            savePinnedFiles();
            saveOrder();
            applySearch();
            setStatus("Moved " + folderLeaf + " to root");
        } catch (IOException e) {
            setStatus("Move failed.");
        }
    }

    /** Update allNoteFiles, pinnedFiles, knownFolders, expandedFolders when a folder path changes. */
    private void updateFolderReferences(String oldFolder, String newFolder) {
        String oldPrefix = oldFolder + "/";
        String newPrefix = newFolder + "/";
        for (int i = 0; i < allNoteFiles.size(); i++) {
            String f = allNoteFiles.get(i);
            if (f.startsWith(oldPrefix)) {
                String updated = newPrefix + f.substring(oldPrefix.length());
                allNoteFiles.set(i, updated);
                if (f.equals(currentFile)) currentFile = updated;
            }
        }
        for (int i = 0; i < pinnedFiles.size(); i++) {
            if (pinnedFiles.get(i).startsWith(oldPrefix))
                pinnedFiles.set(i, newPrefix + pinnedFiles.get(i).substring(oldPrefix.length()));
        }
        Set<String> newKnown = new LinkedHashSet<>();
        for (String kf : knownFolders) {
            if (kf.equals(oldFolder)) newKnown.add(newFolder);
            else if (kf.startsWith(oldPrefix)) newKnown.add(newPrefix + kf.substring(oldPrefix.length()));
            else newKnown.add(kf);
        }
        knownFolders = newKnown;
        Set<String> newExpanded = new LinkedHashSet<>();
        for (String ef : expandedFolders) {
            if (ef.equals(oldFolder)) newExpanded.add(newFolder);
            else if (ef.startsWith(oldPrefix)) newExpanded.add(newPrefix + ef.substring(oldPrefix.length()));
            else newExpanded.add(ef);
        }
        expandedFolders = newExpanded;
    }

    // =========================================================================
    // Context menu helpers
    // =========================================================================
    private int menuHeight() {
        if (contextMenuSidebarBg) return 2 * MENU_ITEM_H + 4;
        if (contextMenuFolder   != null) return 3 * MENU_ITEM_H + 4;
        return 4 * MENU_ITEM_H + 4;
    }
    private int menuX()      { return Math.min(contextMenuX, this.width  - MENU_W      - 2); }
    private int menuY()      { return Math.min(contextMenuY, this.height - menuHeight() - 2); }

    // -------------------------------------------------------------------------
    // Editor context menu helpers
    // -------------------------------------------------------------------------
    private record EditorMenuItem(String label, String actionKey, boolean isSeparator) {
        static EditorMenuItem action(String label, String key) { return new EditorMenuItem(label, key, false); }
        static EditorMenuItem separator() { return new EditorMenuItem("", "", true); }
    }

    private List<EditorMenuItem> buildEditorMenuItems() {
        IgCalcConfig cfg = IgCalcConfig.getInstance();
        List<List<EditorMenuItem>> groups = new ArrayList<>();
        // Group 1: clipboard
        List<EditorMenuItem> g1 = new ArrayList<>();
        boolean sel = hasSelection();
        if (sel && cfg.isContextActionEnabled("cut"))   g1.add(EditorMenuItem.action("Cut", "cut"));
        if (sel && cfg.isContextActionEnabled("copy"))  g1.add(EditorMenuItem.action("Copy", "copy"));
        if (cfg.isContextActionEnabled("paste"))        g1.add(EditorMenuItem.action("Paste", "paste"));
        if (cfg.isContextActionEnabled("selectAll"))    g1.add(EditorMenuItem.action("Select All", "selectAll"));
        if (!g1.isEmpty()) groups.add(g1);
        // Group 2: tools
        List<EditorMenuItem> g2 = new ArrayList<>();
        if (cfg.isContextActionEnabled("insertCoords"))    g2.add(EditorMenuItem.action("Insert Coords", "insertCoords"));
        if (cfg.isContextActionEnabled("exportClipboard")) g2.add(EditorMenuItem.action("Export to Clipboard", "exportClipboard"));
        if (!g2.isEmpty()) groups.add(g2);
        // Group 3: notes
        List<EditorMenuItem> g3 = new ArrayList<>();
        if (cfg.isContextActionEnabled("newNote"))   g3.add(EditorMenuItem.action("New Note", "newNote"));
        if (cfg.isContextActionEnabled("quickNote")) g3.add(EditorMenuItem.action("Quick Note", "quickNote"));
        if (cfg.isContextActionEnabled("find"))      g3.add(EditorMenuItem.action("Find", "find"));
        if (!g3.isEmpty()) groups.add(g3);
        // Merge with separators between groups
        List<EditorMenuItem> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) result.add(EditorMenuItem.separator());
            result.addAll(groups.get(i));
        }
        return result;
    }

    private int editorMenuHeight(List<EditorMenuItem> items) {
        int h = 4;
        for (EditorMenuItem item : items) h += item.isSeparator() ? SEPARATOR_H : MENU_ITEM_H;
        return h;
    }

    private void pasteFromClipboard() {
        String clip = client.keyboard.getClipboard();
        if (clip != null && !clip.isEmpty()) {
            if (hasSelection()) deleteSelection();
            String[] parts = clip.split("\n", -1);
            if (parts.length == 1) {
                String ln = lines.get(cursorLine);
                lines.set(cursorLine, ln.substring(0, cursorCol) + parts[0] + ln.substring(cursorCol));
                cursorCol += parts[0].length();
            } else {
                String before = lines.get(cursorLine).substring(0, cursorCol);
                String after  = lines.get(cursorLine).substring(cursorCol);
                lines.set(cursorLine, before + parts[0]);
                for (int i = 1; i < parts.length - 1; i++) lines.add(cursorLine + i, parts[i]);
                lines.add(cursorLine + parts.length - 1, parts[parts.length - 1] + after);
                cursorLine += parts.length - 1;
                cursorCol = parts[parts.length - 1].length();
            }
            markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor();
        }
    }

    private void executeEditorAction(String key) {
        switch (key) {
            case "cut" -> {
                if (hasSelection()) {
                    client.keyboard.setClipboard(getSelectedText());
                    deleteSelection(); lastEditMs = System.currentTimeMillis(); scrollToCursor();
                }
            }
            case "copy" -> {
                if (hasSelection()) client.keyboard.setClipboard(getSelectedText());
            }
            case "paste" -> pasteFromClipboard();
            case "selectAll" -> {
                selAnchorLine = 0; selAnchorCol = 0;
                cursorLine = lines.size() - 1;
                cursorCol = lines.get(cursorLine).length();
            }
            case "insertCoords" -> insertPlayerCoords();
            case "exportClipboard" -> {
                client.keyboard.setClipboard(String.join("\n", lines));
                setStatus("Copied to clipboard!");
            }
            case "newNote" -> { newNote(); rebuildWidgets(); }
            case "quickNote" -> triggerQuickNote();
            case "find" -> {
                if (!sidebarVisible) {
                    sidebarVisible = true;
                    IgCalcWindowState.notesSidebar = true;
                }
                searchActive = true; rebuildWidgets();
                if (searchField != null) setFocused(searchField);
            }
        }
    }

    // =========================================================================
    // Note I/O
    // =========================================================================
    private void loadCurrentNote() {
        lines.clear();
        markdownCache.clear();
        if (currentFile == null) { lines.add(""); markLinesChanged(); cursorLine = 0; cursorCol = 0; scrollOffset = 0; return; }
        Path path = getNotePath(currentFile);
        if (!Files.exists(path)) { lines.add(""); markLinesChanged(); return; }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String part : content.split("\n", -1)) lines.add(part);
            if (lines.isEmpty()) lines.add("");
            markLinesChanged();
            cursorLine = 0; cursorCol = 0; scrollOffset = 0;
        } catch (IOException e) {
            lines.add(""); markLinesChanged(); setStatus("Error loading.");
        }
    }

    private void saveCurrentNote() {
        if (currentFile == null) return;
        try {
            Files.createDirectories(getNotesDir());
            Files.writeString(getNotePath(currentFile), String.join("\n", lines),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) { setStatus("Error saving."); }
        // Keep pinned HUD in sync if it's showing the same note
        if (HudRenderer.INSTANCE.pinnedNotes
                && currentFile != null
                && currentFile.equals(HudRenderer.INSTANCE.notesCurrentFile)) {
            HudRenderer.INSTANCE.notesLines = new ArrayList<>(lines);
        }
    }

    private void setStatus(String msg) { statusMessage = msg; statusTimer = System.currentTimeMillis(); }

    // =========================================================================
    // Markdown helpers
    // =========================================================================
    private int getLineColor(String line) {
        if (line.startsWith("```"))   return 0xFF3A3F52;
        if (line.startsWith("#### ")) return 0xFFB48EAD;
        if (line.startsWith("### "))  return 0xFF6EB5D4;
        if (line.startsWith("## "))   return 0xFF7EC8A0;
        if (line.startsWith("# "))    return 0xFFE8C170;
        if (line.startsWith("> "))    return 0xFF6B7599;
        // Checked checkboxes render dimmed
        if (line.startsWith("- [x]") || line.startsWith("  - [x]")) return 0xFF666666;
        if (line.startsWith("* ")  || line.startsWith("- ")
         || line.startsWith("  * ")|| line.startsWith("  - ")) return 0xFFAAAAAA;
        if (isNumberedList(line))     return 0xFFAAAAAA;
        if (line.startsWith("---"))   return 0xFFFFFFFF;
        return 0xFFD0D0D0;
    }

    private static boolean isNumberedList(String line) {
        int dot = line.indexOf(". ");
        if (dot < 1 || dot > 3) return false;
        for (int i = 0; i < dot; i++) if (!Character.isDigit(line.charAt(i))) return false;
        return true;
    }

    private String getLinePrefix(String line) {
        if (line.startsWith("#### ")) return line.substring(5);
        if (line.startsWith("### "))  return line.substring(4);
        if (line.startsWith("## "))   return line.substring(3);
        if (line.startsWith("# "))    return line.substring(2);
        // Sub-bullet checkboxes
        if (line.startsWith("  - [ ] ")) return "    \u25AA " + line.substring(8);
        if (line.startsWith("  - [x] ")) return "    \u2713 " + line.substring(8);
        if (line.startsWith("  - [ ]"))  return "    \u25AA";
        if (line.startsWith("  - [x]"))  return "    \u2713";
        // Sub-bullets
        if (line.startsWith("  - "))    return "    \u25AA " + line.substring(4);
        if (line.startsWith("  * "))    return "    \u25AA " + line.substring(4);
        // Checkboxes
        if (line.startsWith("- [ ] ")) return "  \u2610 " + line.substring(6);
        if (line.startsWith("- [x] ")) return "  \u2713 " + line.substring(6);
        if (line.startsWith("- [ ]"))  return "  \u2610";
        if (line.startsWith("- [x]"))  return "  \u2713";
        // Bullets
        if (line.startsWith("* "))    return "  \u2022 " + line.substring(2);
        if (line.startsWith("- "))    return "  \u2022 " + line.substring(2);
        if (line.startsWith("> "))    return "\u2502 "   + line.substring(2);
        return line;
    }

    MutableText parseInlineMarkdown(String text) {
        MutableText result = Text.empty();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > i + 2) { flush(result, plain); result.append(Text.literal(text.substring(i + 2, end)).styled(s -> s.withBold(true))); i = end + 2; continue; }
            }
            if (c == '*' && (i + 1 >= text.length() || text.charAt(i + 1) != '*')) {
                int end = text.indexOf('*', i + 1);
                if (end > i + 1) { flush(result, plain); result.append(Text.literal(text.substring(i + 1, end)).styled(s -> s.withItalic(true))); i = end + 1; continue; }
            }
            if (c == '~' && i + 1 < text.length() && text.charAt(i + 1) == '~') {
                int end = text.indexOf("~~", i + 2);
                if (end > i + 2) { flush(result, plain); result.append(Text.literal(text.substring(i + 2, end)).styled(s -> s.withStrikethrough(true))); i = end + 2; continue; }
            }
            if (c == '_' && i + 1 < text.length() && text.charAt(i + 1) == '_') {
                int end = text.indexOf("__", i + 2);
                if (end > i + 2) { flush(result, plain); result.append(Text.literal(text.substring(i + 2, end)).styled(s -> s.withUnderline(true))); i = end + 2; continue; }
            }
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) { flush(result, plain); result.append(Text.literal(text.substring(i + 1, end)).styled(s -> s.withColor(TextColor.fromRgb(0x98C379)))); i = end + 1; continue; }
            }
            // Note link [[...]]
            if (c == '[' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int end = text.indexOf("]]", i + 2);
                if (end > i + 2) {
                    flush(result, plain);
                    String noteName = text.substring(i + 2, end);
                    boolean exists = resolveNoteLink(noteName) != null;
                    int linkColor = exists ? 0xFF589DEF : 0xFFCC6644;
                    result.append(Text.literal(noteName).styled(s -> s.withColor(TextColor.fromRgb(linkColor)).withUnderline(true)));
                    i = end + 2; continue;
                }
            }
            if (c == '[') {
                int textEnd = text.indexOf(']', i + 1);
                if (textEnd > i + 1 && textEnd + 1 < text.length() && text.charAt(textEnd + 1) == '(') {
                    int urlEnd = text.indexOf(')', textEnd + 2);
                    if (urlEnd > textEnd + 1) {
                        flush(result, plain);
                        String linkText = text.substring(i + 1, textEnd);
                        result.append(Text.literal(linkText).styled(s -> s.withColor(TextColor.fromRgb(0x589DEF)).withUnderline(true)));
                        i = urlEnd + 1; continue;
                    }
                }
            }
            plain.append(c);
            i++;
        }
        flush(result, plain);
        return result;
    }

    private static void flush(MutableText target, StringBuilder buf) {
        if (buf.length() > 0) { target.append(Text.literal(buf.toString())); buf.setLength(0); }
    }

    // =========================================================================
    // Visual-row cache
    // =========================================================================
    private void rebuildVisualRows() {
        int curW = (textRenderer != null && contentWidth() > 0) ? contentWidth() : 0;
        if (linesVersion == lastBuiltLinesVersion && curW == lastBuiltContentWidth) return;
        lastBuiltLinesVersion = linesVersion;
        lastBuiltContentWidth = curW;
        visualRows.clear();
        boolean codeBlock = false;
        for (int li = 0; li < lines.size(); li++) {
            String text = lines.get(li);
            if (text.startsWith("```")) {
                codeBlock = !codeBlock;
                visualRows.add(new VisualRow(li, 0, text.length(), 1.0f, LINE_HEIGHT, false, 0));
                continue;
            }
            float scale    = codeBlock ? 1.0f : getLineScale(text);
            int   heightPx = (int) Math.ceil(LINE_HEIGHT * scale);
            // Compute wrap indent for bullet/heading lines
            int wrapIndentPx = 0;
            if (!codeBlock) {
                int rawStart = rawContentStart(text);
                if (rawStart > 0 && rawStart <= text.length()) {
                    String fullDisplay = getLinePrefix(text);
                    String rawContent = text.substring(rawStart);
                    int displayContentStart = fullDisplay.length() - rawContent.length();
                    if (displayContentStart > 0 && textRenderer != null)
                        wrapIndentPx = (int)(textRenderer.getWidth(fullDisplay.substring(0, displayContentStart)) * scale);
                }
            }
            int   maxW     = (textRenderer != null && contentWidth() > 4)
                    ? (int) ((contentWidth() - 4) / scale) : Integer.MAX_VALUE;
            if (text.isEmpty() || maxW <= 0 || textRenderer == null || textRenderer.getWidth(text) <= maxW) {
                visualRows.add(new VisualRow(li, 0, text.length(), scale, heightPx, codeBlock, wrapIndentPx));
                continue;
            }
            int start = 0;
            while (start < text.length()) {
                // For continuation rows, available width is reduced by indent
                int availW = (start > 0) ? (int)((contentWidth() - 4 - wrapIndentPx) / scale) : maxW;
                if (availW <= 0) availW = maxW;
                int end = start;
                for (int c = start + 1; c <= text.length(); c++) {
                    if (textRenderer.getWidth(text.substring(start, c)) > availW) break;
                    end = c;
                }
                // Snap to word boundary
                if (end > start && end < text.length()) {
                    int lastSpace = text.lastIndexOf(' ', end - 1);
                    if (lastSpace > start) end = lastSpace + 1;
                }
                if (end == start) end = start + 1;
                visualRows.add(new VisualRow(li, start, end, scale, heightPx, codeBlock, wrapIndentPx));
                start = end;
            }
        }
        if (visualRows.isEmpty()) visualRows.add(new VisualRow(0, 0, 0, 1.0f, LINE_HEIGHT, false, 0));
    }

    private int cursorVisualRow() {
        for (int vr = 0; vr < visualRows.size(); vr++) {
            VisualRow row = visualRows.get(vr);
            if (row.logLine() != cursorLine) continue;
            boolean isLast = (vr + 1 >= visualRows.size() || visualRows.get(vr + 1).logLine() != cursorLine);
            if (cursorCol >= row.startCol() && (cursorCol < row.endCol() || isLast)) return vr;
        }
        return 0;
    }

    private void scrollToCursor() {
        rebuildVisualRows();
        int vr = cursorVisualRow();
        if (vr < scrollOffset) { scrollOffset = vr; return; }
        int px = 0;
        for (int i = scrollOffset; i <= vr && i < visualRows.size(); i++)
            px += visualRows.get(i).heightPx();
        while (px > textAreaH() && scrollOffset < vr) {
            px -= visualRows.get(scrollOffset).heightPx();
            scrollOffset++;
        }
        scrollOffset = Math.max(0, scrollOffset);
    }

    private int computeCursorX(int textX, String rawLine, VisualRow row, int col) {
        int baseX = textX + (row.startCol() > 0 ? row.wrapIndentPx() : 0);
        String rawToCursor;
        if (row.startCol() == 0) {
            rawToCursor = getLinePrefix(rawLine.substring(0, Math.min(rawLine.length(), col)));
        } else {
            int colInRow = Math.max(0, Math.min(rawLine.length() - row.startCol(), col - row.startCol()));
            rawToCursor = rawLine.substring(row.startCol(), row.startCol() + colInRow);
        }
        return baseX + (int) (styledWidth(rawToCursor) * row.scale());
    }

    private record SelectionRange(int startLine, int startCol, int endLine, int endCol) {}

    private boolean hasSelection() {
        return selAnchorLine >= 0
            && (selAnchorLine != cursorLine || selAnchorCol != cursorCol);
    }

    private SelectionRange getSelection() {
        if (!hasSelection()) return null;
        boolean cursorFirst = cursorLine < selAnchorLine
                || (cursorLine == selAnchorLine && cursorCol <= selAnchorCol);
        return cursorFirst
            ? new SelectionRange(cursorLine, cursorCol, selAnchorLine, selAnchorCol)
            : new SelectionRange(selAnchorLine, selAnchorCol, cursorLine, cursorCol);
    }

    private String getSelectedText() {
        SelectionRange sel = getSelection();
        if (sel == null) return "";
        if (sel.startLine() == sel.endLine())
            return lines.get(sel.startLine()).substring(sel.startCol(), sel.endCol());
        StringBuilder sb = new StringBuilder();
        sb.append(lines.get(sel.startLine()).substring(sel.startCol())).append('\n');
        for (int i = sel.startLine() + 1; i < sel.endLine(); i++) sb.append(lines.get(i)).append('\n');
        sb.append(lines.get(sel.endLine()).substring(0, sel.endCol()));
        return sb.toString();
    }

    private void deleteSelection() {
        SelectionRange sel = getSelection();
        if (sel == null) return;
        if (sel.startLine() == sel.endLine()) {
            String line = lines.get(sel.startLine());
            lines.set(sel.startLine(), line.substring(0, sel.startCol()) + line.substring(sel.endCol()));
        } else {
            String head = lines.get(sel.startLine()).substring(0, sel.startCol());
            String tail = lines.get(sel.endLine()).substring(sel.endCol());
            lines.set(sel.startLine(), head + tail);
            for (int i = sel.endLine(); i > sel.startLine(); i--) lines.remove(i);
        }
        cursorLine = sel.startLine(); cursorCol = sel.startCol();
        selAnchorLine = -1;
        markLinesChanged();
    }

    private int[] pixelToLineCol(int px, int py) {
        rebuildVisualRows();
        int taX  = winX + contentX() + 2;
        int taY  = winY + textAreaY();
        int relY = py - taY;
        int cumY = 0;
        int bestVR = scrollOffset;
        for (int vr = scrollOffset; vr < visualRows.size(); vr++) {
            int rowH = visualRows.get(vr).heightPx();
            if (relY < cumY + rowH) { bestVR = vr; break; }
            cumY += rowH;
            if (cumY >= textAreaH()) { bestVR = vr; break; }
        }
        bestVR = Math.max(0, Math.min(visualRows.size() - 1, bestVR));
        VisualRow row = visualRows.get(bestVR);
        int logLine = row.logLine();
        String rawLine = lines.get(logLine);
        int baseX = taX + (row.startCol() > 0 ? row.wrapIndentPx() : 0);
        int clickX = (int)((px - baseX) / row.scale());
        int col;
        if (row.startCol() == 0) {
            String display = getLinePrefix(rawLine.substring(0, row.endCol()));
            String fullPrefix = getLinePrefix(rawLine);
            int diff = fullPrefix.length() - rawLine.length();
            int bestCol2 = 0;
            for (int c = 0; c <= display.length(); c++) {
                if (styledWidth(display.substring(0, c)) <= clickX) bestCol2 = c; else break;
            }
            col = Math.max(0, Math.min(rawLine.length(), bestCol2 - diff));
        } else {
            String seg = rawLine.substring(row.startCol(), row.endCol());
            int bestCol2 = 0;
            for (int c = 0; c <= seg.length(); c++) {
                if (styledWidth(seg.substring(0, c)) <= clickX) bestCol2 = c; else break;
            }
            col = Math.max(row.startCol(), Math.min(rawLine.length(), row.startCol() + bestCol2));
        }
        return new int[]{ logLine, col };
    }

    // =========================================================================
    // Rendering helpers
    // =========================================================================
    private static void fillRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y,     x + w - r, y + h,     color);
        ctx.fill(x,     y + r, x + r,     y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy   = r - i - 0.5;
            int    xOff = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + xOff, y + i,         x + w - xOff, y + i + 1, color);
            ctx.fill(x + xOff, y + h - 1 - i, x + w - xOff, y + h - i, color);
        }
    }

    private static void fillRoundedRectTopOnly(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x, y + r, x + w, y + h, color);
        for (int i = 0; i < r; i++) {
            double dy   = r - i - 0.5;
            int    xOff = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + xOff, y + i, x + w - xOff, y + i + 1, color);
        }
    }

    // =========================================================================
    // Rendering
    // =========================================================================
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // --- Window shell ---
        float winOpacity = IgCalcHudState.notesWindowOpacity;
        int bgAlpha = (int)(((COLOR_BG >>> 24) & 0xFF) * winOpacity);
        int effectiveBg = (bgAlpha << 24) | (COLOR_BG & 0x00FFFFFF);
        fillRoundedRect(context, winX, winY, winW, winH, 6, effectiveBg);
        boolean isFocused = (overlay != null && overlay.getFocusedWindow() == this);
        int titleBarColor = isFocused ? COLOR_TITLE_BAR : 0xFF1E2030;
        fillRoundedRectTopOnly(context, winX, winY, winW, TITLE_BAR_HEIGHT, 6, titleBarColor);
        int iconDefault = isFocused ? 0xFF6B7099 : 0xFF4A5068;

        // ── Title bar buttons ─────────────────────────────────────────────
        // Uniform button sizing: each button is TB_W × TB_H with TB_GAP spacing
        int TB_W = 14, TB_H = 12, TB_GAP = 2;
        int tbY = winY + (TITLE_BAR_HEIGHT - TB_H) / 2;
        int tbTextY = tbY + (TB_H - textRenderer.fontHeight) / 2;

        // Close [×] — leftmost
        int closeX = winX + 4;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + TB_W
                          && mouseY >= tbY && mouseY < tbY + TB_H;
        String closeIcon = "\u00D7";
        context.drawText(textRenderer, closeIcon,
                closeX + (TB_W - textRenderer.getWidth(closeIcon)) / 2, tbTextY,
                hoverClose ? COLOR_DOT_RED : iconDefault, false);

        // Sidebar toggle [≡] — next to close
        int toggleX = closeX + TB_W + TB_GAP;
        boolean hoverToggle = mouseX >= toggleX && mouseX < toggleX + TB_W
                           && mouseY >= tbY && mouseY < tbY + TB_H;
        String toggleIcon = "\u2261";
        context.drawText(textRenderer, toggleIcon,
                toggleX + (TB_W - textRenderer.getWidth(toggleIcon)) / 2, tbTextY,
                sidebarVisible ? COLOR_TITLE_TEXT : (hoverToggle ? COLOR_TITLE_TEXT : iconDefault), false);

        // Right-side buttons (right to left): Pin, Export, Help
        boolean noteHudPinned = HudRenderer.INSTANCE.pinnedNotes;

        int pinX = winX + winW - 4 - TB_W;
        boolean hoverPin = mouseX >= pinX && mouseX < pinX + TB_W
                        && mouseY >= tbY && mouseY < tbY + TB_H;
        String pinIcon = "\u2691";
        context.drawText(textRenderer, pinIcon,
                pinX + (TB_W - textRenderer.getWidth(pinIcon)) / 2, tbTextY,
                (noteHudPinned || hoverPin) ? COLOR_PIN : iconDefault, false);

        int exportX = pinX - TB_GAP - TB_W;
        boolean hoverExport = mouseX >= exportX && mouseX < exportX + TB_W
                           && mouseY >= tbY && mouseY < tbY + TB_H;
        int arrowColor = hoverExport ? 0xFF0A84FF : iconDefault;
        // iOS-style share icon: rounded open box with upward arrow
        int eCX = exportX + TB_W / 2;       // center x of icon
        int eTop = tbY;                      // top of button area
        int eBot = tbY + TB_H;              // bottom of button area
        // Arrow shaft (vertical line, center)
        context.fill(eCX, eTop + 1, eCX + 1, eTop + 7, arrowColor);
        // Arrowhead: 3 pixel-art chevron rows
        context.fill(eCX - 1, eTop + 2, eCX, eTop + 3, arrowColor);
        context.fill(eCX + 1, eTop + 2, eCX + 2, eTop + 3, arrowColor);
        context.fill(eCX - 2, eTop + 3, eCX - 1, eTop + 4, arrowColor);
        context.fill(eCX + 2, eTop + 3, eCX + 3, eTop + 4, arrowColor);
        // Open box: U-shape with gap at top for arrow
        int boxL = eCX - 4, boxR = eCX + 5;
        int boxTop = eTop + 5, boxBot = eBot - 1;
        // Left wall
        context.fill(boxL, boxTop, boxL + 1, boxBot, arrowColor);
        // Right wall
        context.fill(boxR - 1, boxTop, boxR, boxBot, arrowColor);
        // Bottom
        context.fill(boxL, boxBot - 1, boxR, boxBot, arrowColor);

        int helpX = exportX - TB_GAP - TB_W;
        boolean hoverHelp = mouseX >= helpX && mouseX < helpX + TB_W
                         && mouseY >= tbY && mouseY < tbY + TB_H;
        String helpIcon = "?";
        context.drawText(textRenderer, helpIcon,
                helpX + (TB_W - textRenderer.getWidth(helpIcon)) / 2, tbTextY,
                hoverHelp ? 0xFF0A84FF : iconDefault, false);

        // Title — centered between left buttons and right buttons
        int titleLeft = toggleX + TB_W + 4;
        int titleRight = helpX - 4;
        String titleStr = currentFile != null ? noteNameDisplay(currentFile) : "Notes";
        titleStr = textRenderer.trimToWidth(titleStr, titleRight - titleLeft);
        int titleW = textRenderer.getWidth(titleStr);
        context.drawText(textRenderer, titleStr,
                titleLeft + (titleRight - titleLeft - titleW) / 2,
                winY + (TITLE_BAR_HEIGHT - textRenderer.fontHeight) / 2,
                COLOR_TITLE_TEXT, false);

        // =====================================================================
        // SIDEBAR
        // =====================================================================
        if (sidebarVisible) {
            int sbRight = winX + PADDING + SIDEBAR_W;
            context.fill(winX + 1, winY + TITLE_BAR_HEIGHT, sbRight, winY + winH - 1, COLOR_SIDEBAR_BG);
            context.fill(sbRight,  winY + TITLE_BAR_HEIGHT, sbRight + 1, winY + winH - 1, COLOR_DIVIDER);

            int hdrY = winY + TITLE_BAR_HEIGHT + 2;
            if (!searchActive) {
                String hdrLabel = textRenderer.trimToWidth(worldContextName, SIDEBAR_W - 20);
                context.drawText(textRenderer, hdrLabel, winX + PADDING + 2, hdrY, 0xFF5C6380, false);
                int searchBtnX = sbRight - 14;
                context.fill(searchBtnX, hdrY, searchBtnX + 12, hdrY + 11, 0xFF1E2235);
                context.drawText(textRenderer, "?", searchBtnX + 3, hdrY + 1, 0xFF5C6380, false);
            }

            int listStartY = winY + TITLE_BAR_HEIGHT + 16;
            int listBottom = winY + winH - 20;

            List<SidebarEntry> sidebarRows = buildSidebarRows();

            // Drag state: compute insert index and whether we're hovering a folder
            int dragInsertIdx    = -1;
            String dragTargetFolder = null;
            if (sidebarDragging && sidebarDragItem != null) {
                dragInsertIdx = sidebarScroll + (sidebarDragCurrentY - listStartY) / ITEM_HEIGHT;
                dragInsertIdx = Math.max(0, Math.min(sidebarRows.size(), dragInsertIdx));
                if (dragInsertIdx < sidebarRows.size()
                        && sidebarRows.get(dragInsertIdx).kind() == RowKind.FOLDER) {
                    String candidate = sidebarRows.get(dragInsertIdx).value();
                    // Don't highlight if dragging a folder onto itself or its children
                    if (sidebarDragKind == RowKind.FOLDER
                            && (candidate.equals(sidebarDragItem) || candidate.startsWith(sidebarDragItem + "/"))) {
                        dragTargetFolder = null;
                    } else {
                        dragTargetFolder = candidate;
                    }
                }
            }

            for (int i = sidebarScroll; i < sidebarRows.size(); i++) {
                int itemY = listStartY + (i - sidebarScroll) * ITEM_HEIGHT;
                if (itemY + ITEM_HEIGHT > listBottom) break;

                SidebarEntry entry = sidebarRows.get(i);

                // ── SPACER row ─────────────────────────────────────────────
                if (entry.kind() == RowKind.SPACER) {
                    continue;
                }

                // ── NAME_HEADER row ─────────────────────────────────────────
                if (entry.kind() == RowKind.NAME_HEADER) {
                    context.drawText(textRenderer,
                            Text.literal("File Names").formatted(Formatting.BOLD),
                            winX + PADDING + 2, itemY + 4, 0xFFD0D6F0, false);
                    continue;
                }

                // ── CONTENT_HEADER row ─────────────────────────────────────
                if (entry.kind() == RowKind.CONTENT_HEADER) {
                    context.drawText(textRenderer,
                            Text.literal("In Content").formatted(Formatting.BOLD),
                            winX + PADDING + 2, itemY + 4, 0xFFD0D6F0, false);
                    continue;
                }

                // ── FOLDER row ─────────────────────────────────────────────
                if (entry.kind() == RowKind.FOLDER) {
                    String  folder      = entry.value();
                    boolean expanded    = expandedFolders.contains(folder);
                    boolean isDragTarget = folder.equals(dragTargetFolder);
                    boolean isFolderDragging = sidebarDragging && sidebarDragKind == RowKind.FOLDER && folder.equals(sidebarDragItem);
                    boolean isHov       = !contextMenuVisible && !isDragTarget && !isFolderDragging
                            && mouseX >= winX + PADDING && mouseX < sbRight
                            && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
                    boolean isCursor    = sidebarFocused && i == sidebarCursorIdx;

                    if (isFolderDragging) {
                        context.fill(winX + PADDING, itemY, sbRight, itemY + ITEM_HEIGHT, 0xFF1E2235);
                    } else if (isDragTarget) {
                        context.fill(winX + PADDING, itemY, sbRight, itemY + ITEM_HEIGHT, 0xFF2A4070);
                    } else if (isHov) {
                        context.fill(winX + PADDING, itemY, sbRight, itemY + ITEM_HEIGHT, COLOR_SIDEBAR_HOVER);
                    }
                    if (isCursor) {
                        context.fill(winX + PADDING,     itemY,                 winX + PADDING + 1, itemY + ITEM_HEIGHT, COLOR_STATUS);
                        context.fill(sbRight - 1,         itemY,                 sbRight,            itemY + ITEM_HEIGHT, COLOR_STATUS);
                        context.fill(winX + PADDING + 1, itemY,                 sbRight - 1,        itemY + 1,           COLOR_STATUS);
                        context.fill(winX + PADDING + 1, itemY + ITEM_HEIGHT-1, sbRight - 1,        itemY + ITEM_HEIGHT, COLOR_STATUS);
                    }

                    if (!(renamingFolder && folder.equals(renamingFolderOld))) {
                        int depthIndent = entry.depth() * 8;
                        String arrow = expanded ? "\u25BC" : "\u25B6";
                        int folderColor = isFolderDragging ? 0xFF3A3F52 : 0xFF5C8AFF;
                        context.drawText(textRenderer, arrow, winX + PADDING + 2 + depthIndent, itemY + 2, folderColor, false);
                        String folderDisplayName = folder.contains("/") ? folder.substring(folder.lastIndexOf('/') + 1) : folder;
                        String lbl = textRenderer.trimToWidth(folderDisplayName, SIDEBAR_W - 14 - depthIndent);
                        context.drawText(textRenderer, lbl, winX + PADDING + 10 + depthIndent, itemY + 2, folderColor, false);
                    }
                    continue;
                }

                // ── FILE row ───────────────────────────────────────────────
                String  filename   = entry.value();
                boolean inFolder   = entry.inFolder();
                boolean isPinned   = !inFolder && pinnedFiles.contains(filename);
                boolean isDragging = sidebarDragging && filename.equals(sidebarDragItem);
                boolean isSelected = filename.equals(currentFile);
                boolean isHovered  = !contextMenuVisible && !isDragging
                        && mouseX >= winX + PADDING && mouseX < sbRight
                        && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
                int textIndent = entry.depth() * 8 + (entry.inFolder() ? 2 : 0);

                if (sidebarDragging && dragInsertIdx == i && dragTargetFolder == null)
                    context.fill(winX + PADDING, itemY, sbRight, itemY + 1, COLOR_STATUS);

                boolean isSidebarCursor = sidebarFocused && i == sidebarCursorIdx;
                if (isDragging) {
                    context.fill(winX + PADDING, itemY, sbRight, itemY + ITEM_HEIGHT, 0xFF1E2235);
                } else if (isSelected) {
                    context.fill(winX + PADDING, itemY, sbRight, itemY + ITEM_HEIGHT, COLOR_SIDEBAR_SELECTED);
                } else if (isHovered) {
                    context.fill(winX + PADDING, itemY, sbRight, itemY + ITEM_HEIGHT, COLOR_SIDEBAR_HOVER);
                }
                if (isSidebarCursor) {
                    context.fill(winX + PADDING,     itemY,                winX + PADDING + 1, itemY + ITEM_HEIGHT, COLOR_STATUS);
                    context.fill(sbRight - 1,         itemY,                sbRight,            itemY + ITEM_HEIGHT, COLOR_STATUS);
                    context.fill(winX + PADDING + 1, itemY,                sbRight - 1,        itemY + 1,           COLOR_STATUS);
                    context.fill(winX + PADDING + 1, itemY + ITEM_HEIGHT-1, sbRight - 1,       itemY + ITEM_HEIGHT, COLOR_STATUS);
                }

                if (isPinned)
                    context.fill(winX + PADDING + 1, itemY + 4, winX + PADDING + 3, itemY + 7, COLOR_PIN);

                boolean suppressLabel = (renaming && filename.equals(renamingFile))
                        || (creatingFolder && filename.equals(creatingFolderFor));
                if (!suppressLabel) {
                    int    maxW  = SIDEBAR_W - textIndent - (isPinned ? 10 : 6);
                    String dispName = Path.of(filename).getFileName().toString();
                    if (dispName.endsWith(".txt")) dispName = dispName.substring(0, dispName.length() - 4);
                    String lbl   = textRenderer.trimToWidth(dispName, maxW);
                    int    txtX  = winX + PADDING + textIndent + (isPinned ? 5 : 2);
                    int    col   = isDragging ? 0xFF3A3F52 : (isSelected ? 0xFFE5E5E5 : 0xFF7A8099);
                    context.drawText(textRenderer, lbl, txtX, itemY + 2, col, false);
                }
            }

            // Drop indicator at end of list (only when not hovering a folder)
            if (sidebarDragging && dragTargetFolder == null && dragInsertIdx == sidebarRows.size()) {
                int endY = listStartY + (sidebarRows.size() - sidebarScroll) * ITEM_HEIGHT;
                if (endY < listBottom) context.fill(winX + PADDING, endY, sbRight, endY + 1, COLOR_STATUS);
            }

            // Ghost of dragged item following cursor
            if (sidebarDragging && sidebarDragItem != null) {
                int ghostY = Math.max(listStartY, Math.min(listBottom - ITEM_HEIGHT,
                        sidebarDragCurrentY - ITEM_HEIGHT / 2));
                context.fill(winX + PADDING, ghostY, sbRight, ghostY + ITEM_HEIGHT, 0xFF2A4070);
                String ghostLabel;
                if (sidebarDragKind == RowKind.FOLDER) {
                    String leaf = sidebarDragItem.contains("/") ? sidebarDragItem.substring(sidebarDragItem.lastIndexOf('/') + 1) : sidebarDragItem;
                    ghostLabel = textRenderer.trimToWidth("\u25B6 " + leaf, SIDEBAR_W - 6);
                } else {
                    String ghostName = Path.of(sidebarDragItem).getFileName().toString();
                    if (ghostName.endsWith(".txt")) ghostName = ghostName.substring(0, ghostName.length() - 4);
                    ghostLabel = textRenderer.trimToWidth(ghostName, SIDEBAR_W - 6);
                }
                context.drawText(textRenderer, ghostLabel, winX + PADDING + 2, ghostY + 2, 0xFFE5E5E5, false);
            }

            // "+ New" button bg
            if (winY + winH - 16 > listStartY) {
                context.fill(winX + PADDING, winY + winH - 16,
                        winX + PADDING + SIDEBAR_W - 2, winY + winH - 4, 0xFF1E2235);
            }
        }

        // =====================================================================
        // TEXT AREA
        // =====================================================================
        int taX  = winX + contentX();
        int taY  = winY + textAreaY();
        int taX2 = winX + winW - PADDING;

        rebuildVisualRows();
        clickableRegions.clear();
        int textX    = taX + 2;
        int cursorVR = cursorVisualRow();
        int py       = 0;

        for (int vr = scrollOffset; vr < visualRows.size() && py < textAreaH(); vr++) {
            VisualRow row     = visualRows.get(vr);
            int       rowH    = row.heightPx();
            float     scale   = row.scale();
            int       lineY   = taY + py;
            String    rawLine = lines.get(row.logLine());
            String    segment = rawLine.substring(row.startCol(), row.endCol());
            boolean   isCursorRow = (vr == cursorVR);

            if (isCursorRow && !row.inCodeBlock() && !rawLine.startsWith("```"))
                context.fill(taX, lineY, taX2, lineY + rowH, COLOR_LINE_HL);

            // Selection highlight
            SelectionRange sel = getSelection();
            if (sel != null) {
                int rLogLine = row.logLine();
                boolean rowOverlaps =
                    (rLogLine > sel.startLine() && rLogLine < sel.endLine())
                    || (rLogLine == sel.startLine() && row.endCol() > sel.startCol() && (sel.endLine() > rLogLine || sel.endCol() > row.startCol()))
                    || (rLogLine == sel.endLine()   && row.startCol() < sel.endCol() && (sel.startLine() < rLogLine || sel.startCol() < row.endCol()))
                    || (sel.startLine() == sel.endLine() && rLogLine == sel.startLine()
                        && row.startCol() < sel.endCol() && row.endCol() > sel.startCol());
                if (rowOverlaps) {
                    int colStart = Math.max(row.startCol(),
                        rLogLine == sel.startLine() ? sel.startCol() : 0);
                    int colEnd   = Math.min(row.endCol(),
                        rLogLine == sel.endLine() ? sel.endCol() : row.endCol());
                    int px0 = computeCursorX(textX, rawLine, row, colStart);
                    int px1 = (rLogLine < sel.endLine() && row.endCol() >= rawLine.length())
                        ? taX2 : computeCursorX(textX, rawLine, row, colEnd);
                    if (px1 > px0) context.fill(px0, lineY, px1, lineY + rowH, 0x663A5A90);
                }
            }

            String displayRaw = (row.startCol() == 0) ? getLinePrefix(segment) : segment;
            int    lineColor  = row.inCodeBlock() ? 0xFFABB2BF : getLineColor(rawLine);
            int renderTextX = textX + (row.startCol() > 0 ? row.wrapIndentPx() : 0);

            if (rawLine.startsWith("```")) {
                int midY = lineY + rowH / 2;
                context.fill(taX + 1, midY, taX2 - 1, midY + 1, 0xFF3A3F52);
            } else if (row.inCodeBlock()) {
                context.fill(taX + 1, lineY, taX2 - 1, lineY + rowH, 0xFF171A27);
                context.drawText(textRenderer,
                        Text.literal(displayRaw).styled(s -> s.withColor(TextColor.fromRgb(0xABB2BF))),
                        renderTextX, lineY, 0xFFABB2BF, false);
            } else if (rawLine.startsWith("---") && row.startCol() == 0) {
                int midY = lineY + rowH / 2;
                context.fill(renderTextX, midY, taX2 - 2, midY + 1, 0xFF484848);
            } else if (scale != 1.0f) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(renderTextX, lineY);
                context.getMatrices().scale(scale, scale);
                context.drawText(textRenderer, markdownCache.computeIfAbsent(displayRaw, this::parseInlineMarkdown), 0, 0, lineColor, false);
                context.getMatrices().popMatrix();
            } else {
                context.drawText(textRenderer, markdownCache.computeIfAbsent(displayRaw, this::parseInlineMarkdown), renderTextX, lineY, lineColor, false);
            }

            // Track [[note link]] clickable regions for this row
            if (row.startCol() == 0 && !row.inCodeBlock() && !rawLine.startsWith("```")) {
                int searchPos = 0;
                while (searchPos < rawLine.length()) {
                    int linkStart = rawLine.indexOf("[[", searchPos);
                    if (linkStart < 0 || linkStart >= row.endCol()) break;
                    int linkEnd = rawLine.indexOf("]]", linkStart + 2);
                    if (linkEnd < 0) break;
                    String noteName = rawLine.substring(linkStart + 2, linkEnd);
                    // Compute pixel X positions for the link text (inside [[ and ]])
                    int px0 = computeCursorX(textX, rawLine, row, linkStart + 2);
                    int px1 = computeCursorX(textX, rawLine, row, linkEnd);
                    clickableRegions.add(new ClickableRegion(px0, lineY, px1, lineY + rowH, noteName));
                    searchPos = linkEnd + 2;
                }
            }

            if (isCursorRow) {
                int     cursorPixelX  = computeCursorX(textX, rawLine, row, cursorCol);
                int     cursorW       = Math.max(1, (int) scale);
                boolean cursorVisible = (System.currentTimeMillis() - lastBlink) % 1000 < 500;
                if (cursorVisible)
                    context.fill(cursorPixelX, lineY, cursorPixelX + cursorW, lineY + rowH - 1, COLOR_CURSOR);
            }
            py += rowH;
        }

        // Status message
        if (!statusMessage.isEmpty()) {
            if (System.currentTimeMillis() - statusTimer < 3000) {
                context.drawText(textRenderer, statusMessage,
                        winX + (winW - textRenderer.getWidth(statusMessage)) / 2,
                        winY + winH - 22, COLOR_STATUS, false);
            } else {
                statusMessage = "";
            }
        }

        // Resize grip
        context.fill(winX + winW - RESIZE_GRIP, winY + winH - RESIZE_GRIP,
                winX + winW, winY + winH, 0xFF2E3347);

        // Child widgets (search field, rename field, buttons)
        super.render(context, mouseX, mouseY, delta);

        // =====================================================================
        // CONTEXT MENU — drawn last, on top of everything
        // =====================================================================
        if (contextMenuVisible && (contextMenuFile != null || contextMenuFolder != null || contextMenuSidebarBg)) {
            int mx2 = menuX();
            int my2 = menuY();
            int mh  = menuHeight();

            fillRoundedRect(context, mx2 - 1, my2 - 1, MENU_W + 2, mh + 2, 4, COLOR_MENU_BORDER);
            fillRoundedRect(context, mx2,      my2,     MENU_W,     mh,     3, COLOR_MENU_BG);

            String[] labels;
            if (contextMenuSidebarBg) {
                labels = new String[]{ "New note", "New folder" };
            } else if (contextMenuFolder != null) {
                labels = new String[]{ "Rename folder", "New subfolder", "Delete folder" };
            } else {
                boolean isPinned = pinnedFiles.contains(contextMenuFile);
                labels = new String[]{ "Rename", "Delete", isPinned ? "Unpin" : "Pin", "New folder" };
            }
            for (int k = 0; k < labels.length; k++) {
                int iy  = my2 + 2 + k * MENU_ITEM_H;
                boolean hov = mouseX >= mx2 && mouseX < mx2 + MENU_W
                           && mouseY >= iy  && mouseY < iy + MENU_ITEM_H;
                if (hov) fillRoundedRect(context, mx2 + 1, iy, MENU_W - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
                boolean isDel = labels[k].equals("Delete") || labels[k].equals("Delete folder");
                int col = isDel ? COLOR_MENU_DELETE : COLOR_TITLE_TEXT;
                context.drawText(textRenderer, labels[k], mx2 + 6, iy + 3, col, false);
            }
        }

        // =====================================================================
        // EXPORT MENU — drawn on top
        // =====================================================================
        if (exportMenuVisible) {
            int emW = 110;
            int emH = 3 * MENU_ITEM_H + 4;
            int emx = Math.min(exportMenuX, this.width  - emW - 2);
            int emy = Math.min(exportMenuY, this.height - emH - 2);
            fillRoundedRect(context, emx - 1, emy - 1, emW + 2, emH + 2, 4, COLOR_MENU_BORDER);
            fillRoundedRect(context, emx,      emy,     emW,     emH,     3, COLOR_MENU_BG);
            String[] exportLabels = { "Copy to Clipboard", "Export to Desktop", "Open in Files" };
            for (int k = 0; k < exportLabels.length; k++) {
                int iy = emy + 2 + k * MENU_ITEM_H;
                boolean hov = mouseX >= emx && mouseX < emx + emW
                           && mouseY >= iy  && mouseY < iy + MENU_ITEM_H;
                if (hov) fillRoundedRect(context, emx + 1, iy, emW - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
                context.drawText(textRenderer, exportLabels[k], emx + 6, iy + 3, COLOR_TITLE_TEXT, false);
            }
        }

        // =====================================================================
        // EDITOR CONTEXT MENU — right-click in text area
        // =====================================================================
        if (editorContextMenuVisible) {
            List<EditorMenuItem> items = buildEditorMenuItems();
            int emH = editorMenuHeight(items);
            int emx = Math.min(editorContextMenuX, this.width  - EDITOR_MENU_W - 2);
            int emy = Math.min(editorContextMenuY, this.height - emH - 2);
            fillRoundedRect(context, emx - 1, emy - 1, EDITOR_MENU_W + 2, emH + 2, 4, COLOR_MENU_BORDER);
            fillRoundedRect(context, emx, emy, EDITOR_MENU_W, emH, 3, COLOR_MENU_BG);
            int iy = emy + 2;
            for (EditorMenuItem item : items) {
                if (item.isSeparator()) {
                    context.fill(emx + 6, iy + 3, emx + EDITOR_MENU_W - 6, iy + 4, COLOR_MENU_BORDER);
                    iy += SEPARATOR_H;
                } else {
                    boolean hov = mouseX >= emx && mouseX < emx + EDITOR_MENU_W
                               && mouseY >= iy  && mouseY < iy + MENU_ITEM_H;
                    if (hov) fillRoundedRect(context, emx + 1, iy, EDITOR_MENU_W - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
                    context.drawText(textRenderer, item.label(), emx + 6, iy + 3, COLOR_TITLE_TEXT, false);
                    iy += MENU_ITEM_H;
                }
            }
        }

        // =====================================================================
        // KEYBIND REFERENCE CARD
        // =====================================================================
        if (keybindCardVisible) {
            IgCalcConfig cfg = IgCalcConfig.getInstance();
            int cardW = 170;
            int TB_W2 = 14, TB_GAP2 = 2;
            int pinBtnX2 = winX + winW - 4 - TB_W2;
            int exportBtnX2 = pinBtnX2 - TB_GAP2 - TB_W2;
            int helpBtnX2 = exportBtnX2 - TB_GAP2 - TB_W2;
            int cardX = Math.max(winX, helpBtnX2 + TB_W2 - cardW);
            int cardY2 = winY + TITLE_BAR_HEIGHT + 2;
            String[][] rows = {
                {"New Note",             cfg.keyNewNote.display()},
                {"Quick Note",           cfg.keyQuickNote.display()},
                {"Find",                 cfg.keyFindSidebar.display()},
                {"Insert Coords",        cfg.keyInsertCoords.display()},
                {"Export to Clipboard",   cfg.keyCopyClipboard.display()},
                {"Save",                 "Ctrl+S"},
                {"Select All",           "Ctrl+A"},
            };
            int cardH = rows.length * MENU_ITEM_H + SEPARATOR_H + MENU_ITEM_H + 4;
            fillRoundedRect(context, cardX - 1, cardY2 - 1, cardW + 2, cardH + 2, 4, COLOR_MENU_BORDER);
            fillRoundedRect(context, cardX, cardY2, cardW, cardH, 3, COLOR_MENU_BG);
            int iy = cardY2 + 2;
            for (String[] row : rows) {
                boolean hov = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
                if (hov) fillRoundedRect(context, cardX + 1, iy, cardW - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
                context.drawText(textRenderer, row[0], cardX + 6, iy + 3, 0xFFE5E5E5, false);
                int kbW = textRenderer.getWidth(row[1]);
                context.drawText(textRenderer, row[1], cardX + cardW - 6 - kbW, iy + 3, 0xFF6B7099, false);
                iy += MENU_ITEM_H;
            }
            // Separator
            context.fill(cardX + 6, iy + 3, cardX + cardW - 6, iy + 4, COLOR_MENU_BORDER);
            iy += SEPARATOR_H;
            // Tour link
            boolean hovTour = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
            if (hovTour) fillRoundedRect(context, cardX + 1, iy, cardW - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
            context.drawText(textRenderer, "Start guided tour \u2192", cardX + 6, iy + 3, 0xFF0A84FF, false);
        }
    }

    // =========================================================================
    // Mouse input
    // =========================================================================
    @Override
    public boolean mouseClicked(Click click, boolean propagated) {
        int mx  = (int) click.x();
        int my  = (int) click.y();
        int btn = click.button();

        // --- Dismiss / handle editor context menu ---
        if (editorContextMenuVisible) {
            List<EditorMenuItem> items = buildEditorMenuItems();
            int emH = editorMenuHeight(items);
            int emx = Math.min(editorContextMenuX, this.width  - EDITOR_MENU_W - 2);
            int emy = Math.min(editorContextMenuY, this.height - emH - 2);
            editorContextMenuVisible = false;
            if (mx >= emx && mx < emx + EDITOR_MENU_W && my >= emy && my < emy + emH) {
                int iy = emy + 2;
                for (EditorMenuItem item : items) {
                    if (item.isSeparator()) { iy += SEPARATOR_H; continue; }
                    if (my >= iy && my < iy + MENU_ITEM_H) {
                        executeEditorAction(item.actionKey());
                        break;
                    }
                    iy += MENU_ITEM_H;
                }
            }
            return true;
        }

        // --- Dismiss / handle keybind card ---
        if (keybindCardVisible) {
            keybindCardVisible = false;
            int cardW = 170;
            int TB_W2 = 14, TB_GAP2 = 2;
            int pinBtnX2 = winX + winW - 4 - TB_W2;
            int exportBtnX2 = pinBtnX2 - TB_GAP2 - TB_W2;
            int helpBtnX2 = exportBtnX2 - TB_GAP2 - TB_W2;
            int cardX = Math.max(winX, helpBtnX2 + TB_W2 - cardW);
            int cardY2 = winY + TITLE_BAR_HEIGHT + 2;
            String[][] rows = new String[7][];
            int cardH = 7 * MENU_ITEM_H + SEPARATOR_H + MENU_ITEM_H + 4;
            int tourY = cardY2 + 2 + 7 * MENU_ITEM_H + SEPARATOR_H;
            if (mx >= cardX && mx < cardX + cardW && my >= tourY && my < tourY + MENU_ITEM_H) {
                openTutorial();
            }
            return true;
        }

        // --- Dismiss / handle export menu ---
        if (exportMenuVisible) {
            int emW = 110;
            int emH = 3 * MENU_ITEM_H + 4;
            int emx = Math.min(exportMenuX, this.width  - emW - 2);
            int emy = Math.min(exportMenuY, this.height - emH - 2);
            exportMenuVisible = false;
            if (mx >= emx && mx < emx + emW && my >= emy && my < emy + emH) {
                int item = (my - emy - 2) / MENU_ITEM_H;
                if (item == 0) {
                    // Copy to Clipboard
                    client.keyboard.setClipboard(String.join("\n", lines));
                    setStatus("Copied to clipboard!");
                } else if (item == 1) {
                    // Export to Desktop
                    exportToDesktop();
                } else if (item == 2) {
                    // Open in Files
                    openNotesInFileManager();
                }
            }
            return true;
        }

        // --- Dismiss / handle context menu ---
        if (contextMenuVisible) {
            int mx2 = menuX(), my2 = menuY(), mh = menuHeight();
            boolean inside = mx >= mx2 && mx < mx2 + MENU_W && my >= my2 && my < my2 + mh;
            contextMenuVisible = false;
            if (inside) {
                int item = (my - my2 - 2) / MENU_ITEM_H;
                if (contextMenuSidebarBg) {
                    contextMenuSidebarBg = false;
                    if (item == 0) { newNote(); rebuildWidgets(); }
                    else if (item == 1) { creatingFolder = true; creatingFolderFor = null; rebuildWidgets(); }
                } else if (contextMenuFolder != null) {
                    String target = contextMenuFolder;
                    contextMenuFolder = null;
                    if (item == 0) { renamingFolder = true; renamingFolderOld = target; rebuildWidgets(); }
                    else if (item == 1) { creatingSubfolderInFolder = target; creatingFolder = true; creatingFolderFor = null; rebuildWidgets(); }
                    else if (item == 2) { deleteFolder(target); }
                } else if (contextMenuFile != null) {
                    boolean wasPinned = pinnedFiles.contains(contextMenuFile);
                    String target = contextMenuFile;
                    contextMenuFile = null;
                    if (item == 0) { renaming = true; renamingFile = target; rebuildWidgets(); }
                    else if (item == 1) { deleteNote(target); }
                    else if (item == 2) {
                        if (wasPinned) pinnedFiles.remove(target);
                        else if (!pinnedFiles.contains(target)) pinnedFiles.add(target);
                        savePinnedFiles();
                        applySearch();
                    } else if (item == 3) {
                        creatingFolder = true; creatingFolderFor = target; rebuildWidgets();
                    }
                }
            } else {
                contextMenuSidebarBg = false;
                contextMenuFolder    = null;
                contextMenuFile      = null;
            }
            return true;
        }

        // ── Title bar button hit testing (same layout as render) ──────────
        int TB_W = 14, TB_H = 12, TB_GAP = 2;
        int tbY = winY + (TITLE_BAR_HEIGHT - TB_H) / 2;

        int closeX = winX + 4;
        int toggleBtnX = closeX + TB_W + TB_GAP;
        int pinBtnX = winX + winW - 4 - TB_W;
        int exportBtnX = pinBtnX - TB_GAP - TB_W;
        int helpBtnX = exportBtnX - TB_GAP - TB_W;

        boolean inTbRow = my >= tbY && my < tbY + TB_H;

        // 1. Close [×]
        if (inTbRow && mx >= closeX && mx < closeX + TB_W) {
            this.close(); return true;
        }
        // 2. Sidebar toggle [≡]
        if (inTbRow && mx >= toggleBtnX && mx < toggleBtnX + TB_W) {
            sidebarVisible = !sidebarVisible;
            sidebarFocused = false;
            IgCalcWindowState.notesSidebar = sidebarVisible;
            rebuildWidgets(); return true;
        }
        // 3. Help [?]
        if (inTbRow && mx >= helpBtnX && mx < helpBtnX + TB_W) {
            keybindCardVisible = !keybindCardVisible;
            return true;
        }
        // 4. Export [↗]
        if (inTbRow && mx >= exportBtnX && mx < exportBtnX + TB_W) {
            exportMenuVisible = true;
            exportMenuX = mx;
            exportMenuY = my + 10;
            return true;
        }
        // 5. Pin [⚑]
        if (inTbRow && mx >= pinBtnX && mx < pinBtnX + TB_W) {
            if (HudRenderer.INSTANCE.pinnedNotes) {
                HudRenderer.INSTANCE.pinnedNotes = false;
                IgCalcHudState.notesHudPinned = false;
                IgCalcHudState.save();
            } else {
                HudRenderer.INSTANCE.pinNotes(this);
            }
            return true;
        }

        // 3. Search button [?]
        if (sidebarVisible && !searchActive) {
            int sbRight = winX + PADDING + SIDEBAR_W;
            int searchBtnX = sbRight - 14;
            int hdrY = winY + TITLE_BAR_HEIGHT + 2;
            if (mx >= searchBtnX && mx <= searchBtnX + 12 && my >= hdrY && my <= hdrY + 11) {
                searchActive = true; rebuildWidgets();
                if (searchField != null) setFocused(searchField);
                return true;
            }
        }

        // 4. Sidebar
        if (sidebarVisible) {
            int sbRight    = winX + PADDING + SIDEBAR_W;
            int listStartY = winY + TITLE_BAR_HEIGHT + 16;
            int listBottom = winY + winH - 20;
            // Check if click is anywhere in the interactive sidebar area
            boolean inSidebar = mx >= winX + PADDING && mx < sbRight
                    && my > winY + TITLE_BAR_HEIGHT && my < winY + winH - 16;
            if (inSidebar) {
                boolean hitRow = false;
                if (my >= listStartY && my < listBottom) {
                    List<SidebarEntry> rows = buildSidebarRows();
                    int idx = sidebarScroll + (my - listStartY) / ITEM_HEIGHT;
                    if (idx >= 0 && idx < rows.size()) {
                        SidebarEntry entry = rows.get(idx);
                        hitRow = true;

                        if (entry.kind() == RowKind.FOLDER) {
                            String folder = entry.value();
                            if (btn == 1) {
                                contextMenuVisible = true;
                                contextMenuFolder  = folder;
                                contextMenuX = mx; contextMenuY = my;
                            } else {
                                // Save for potential drag; toggle happens on mouseReleased
                                lastSidebarClickFile   = null;
                                lastSidebarClickFolder = folder;
                                sidebarClickOriginX    = mx;
                                sidebarClickOriginY    = my;
                            }
                            return true;
                        }

                        if (entry.kind() == RowKind.CONTENT_HEADER
                                || entry.kind() == RowKind.NAME_HEADER
                                || entry.kind() == RowKind.SPACER) return true;

                        // FILE row
                        String clicked = entry.value();
                        if (btn == 1) {
                            contextMenuVisible = true;
                            contextMenuFile    = clicked;
                            contextMenuX = mx; contextMenuY = my;
                            return true;
                        }

                        long now = System.currentTimeMillis();
                        boolean isDouble = clicked.equals(lastSidebarClickFile)
                                       && (now - lastSidebarClickMs) < 400;
                        lastSidebarClickMs   = now;
                        lastSidebarClickFile = clicked;
                        lastSidebarClickFolder = null;
                        sidebarClickOriginX  = mx;
                        sidebarClickOriginY  = my;

                        if (isDouble) {
                            renaming = true; renamingFile = clicked; rebuildWidgets();
                        } else if (!clicked.equals(currentFile)) {
                            if (renaming) cancelRename();
                            saveCurrentNote();
                            currentFile = clicked;
                            IgCalcWindowState.notesLastFile = currentFile;
                            IgCalcWindowState.save();
                            loadCurrentNote();
                        }
                        return true;
                    }
                }

                // Right-click on empty sidebar space (no row hit, or above/below list)
                if (!hitRow && btn == 1) {
                    contextMenuVisible   = true;
                    contextMenuSidebarBg = true;
                    contextMenuFile      = null;
                    contextMenuFolder    = null;
                    contextMenuX = mx; contextMenuY = my;
                    return true;
                }
            }
        }

        // Title bar drag — between left buttons and right buttons
        if (mx >= winX + 36 && mx <= winX + winW - 52
                && my >= winY && my <= winY + TITLE_BAR_HEIGHT) {
            dragging = true; dragOffX = mx - winX; dragOffY = my - winY; return true;
        }

        // 6. Resize
        boolean onRight  = mx >= winX + winW - RESIZE_GRIP && mx <= winX + winW
                        && my > winY + TITLE_BAR_HEIGHT && my < winY + winH;
        boolean onBottom = my >= winY + winH - RESIZE_GRIP && my <= winY + winH
                        && mx > winX && mx < winX + winW;
        if (onRight && onBottom) {
            resizing = ResizeEdge.CORNER;
        } else if (onRight) {
            resizing = ResizeEdge.RIGHT;
        } else if (onBottom) {
            resizing = ResizeEdge.BOTTOM;
        }
        if (resizing != ResizeEdge.NONE) {
            resizeStartMouseX = mx; resizeStartMouseY = my;
            resizeStartW = winW;   resizeStartH = winH;
            return true;
        }

        // 7. Text area — check note link clicks first
        int taX  = winX + contentX() + 2;
        int taY  = winY + textAreaY();
        int taX2 = winX + winW - PADDING;
        int taY2 = taY + textAreaH();
        // Right-click in text area → editor context menu
        if (btn == 1 && mx >= taX && mx <= taX2 && my >= taY && my <= taY2) {
            editorContextMenuVisible = true;
            editorContextMenuX = mx;
            editorContextMenuY = my;
            return true;
        }
        if (mx >= taX && mx <= taX2 && my >= taY && my <= taY2) {
            // Check clickable note links
            for (ClickableRegion region : clickableRegions) {
                if (mx >= region.x1() && mx <= region.x2()
                 && my >= region.y1() && my <= region.y2()) {
                    String target = resolveNoteLink(region.target());
                    if (target != null) {
                        saveCurrentNote();
                        currentFile = target;
                        IgCalcWindowState.notesLastFile = currentFile;
                        IgCalcWindowState.save();
                        loadCurrentNote();
                        markdownCache.clear();
                        setStatus("Opened " + noteNameDisplay(target));
                    } else {
                        setStatus("Note not found: " + region.target());
                    }
                    return true;
                }
            }
            selAnchorLine = -1;
            rebuildVisualRows();
            int relY = my - taY, py2 = 0;
            int clickedVR = Math.max(0, scrollOffset);
            for (int vr = scrollOffset; vr < visualRows.size(); vr++) {
                int rowH = visualRows.get(vr).heightPx();
                if (relY < py2 + rowH) { clickedVR = vr; break; }
                py2 += rowH;
                if (py2 >= textAreaH()) { clickedVR = vr; break; }
            }
            clickedVR = Math.max(0, Math.min(visualRows.size() - 1, clickedVR));
            VisualRow clickRow = visualRows.get(clickedVR);
            cursorLine = clickRow.logLine();
            String clickRaw = lines.get(cursorLine);

            // Checkbox toggle: clicking within first ~12px of the line toggles [ ] / [x]
            if ((clickRaw.startsWith("- [ ]") || clickRaw.startsWith("- [x]")
              || clickRaw.startsWith("  - [ ]") || clickRaw.startsWith("  - [x]"))
                    && mx < taX + 12) {
                if (clickRaw.contains("- [ ]")) {
                    lines.set(cursorLine, clickRaw.replaceFirst("- \\[ \\]", "- [x]"));
                } else {
                    lines.set(cursorLine, clickRaw.replaceFirst("- \\[x\\]", "- [ ]"));
                }
                lastEditMs = System.currentTimeMillis();
                return true;
            }
            int clickX = (int) ((mx - (taX + 2)) / clickRow.scale());
            if (clickRow.startCol() == 0) {
                String display    = getLinePrefix(clickRaw.substring(0, clickRow.endCol()));
                String fullPrefix = getLinePrefix(clickRaw);
                int    diff       = fullPrefix.length() - clickRaw.length();
                int bestCol = 0;
                for (int c = 0; c <= display.length(); c++) {
                    if (styledWidth(display.substring(0, c)) <= clickX) bestCol = c; else break;
                }
                cursorCol = Math.max(0, Math.min(clickRaw.length(), bestCol - diff));
            } else {
                String seg = clickRaw.substring(clickRow.startCol(), clickRow.endCol());
                int bestCol = 0;
                for (int c = 0; c <= seg.length(); c++) {
                    if (styledWidth(seg.substring(0, c)) <= clickX) bestCol = c; else break;
                }
                cursorCol = Math.max(clickRow.startCol(),
                        Math.min(clickRaw.length(), clickRow.startCol() + bestCol));
            }
            selAnchorLine = cursorLine;
            selAnchorCol  = cursorCol;
            return true;
        }

        return super.mouseClicked(click, propagated);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        int mx = (int) click.x();
        int my = (int) click.y();

        // Start sidebar drag when left-button moves in the sidebar list area
        if (!sidebarDragging && click.button() == 0 && sidebarVisible
                && (lastSidebarClickFile != null || lastSidebarClickFolder != null)) {
            int sbRight    = winX + PADDING + SIDEBAR_W;
            int listStartY = winY + TITLE_BAR_HEIGHT + 16;
            int listBottom = winY + winH - 20;
            boolean pastThreshold = Math.max(Math.abs(mx - sidebarClickOriginX),
                                             Math.abs(my - sidebarClickOriginY))
                                    >= SIDEBAR_DRAG_THRESHOLD;
            if (pastThreshold && mx >= winX + PADDING && mx < sbRight
                    && my >= listStartY && my < listBottom) {
                sidebarDragging = true;
                if (lastSidebarClickFile != null) {
                    sidebarDragItem = lastSidebarClickFile;
                    sidebarDragKind = RowKind.FILE;
                } else {
                    sidebarDragItem = lastSidebarClickFolder;
                    sidebarDragKind = RowKind.FOLDER;
                }
            }
        }

        if (sidebarDragging) {
            sidebarDragCurrentY = my;
            return true;
        }

        if (resizing != ResizeEdge.NONE) {
            int dx = mx - resizeStartMouseX, dy = my - resizeStartMouseY;
            if (resizing == ResizeEdge.RIGHT  || resizing == ResizeEdge.CORNER)
                winW = Math.max(WIN_MIN_W, Math.min(this.width  - winX, resizeStartW + dx));
            if (resizing == ResizeEdge.BOTTOM || resizing == ResizeEdge.CORNER)
                winH = Math.max(WIN_MIN_H, Math.min(this.height - winY, resizeStartH + dy));
            rebuildWidgets(); return true;
        }

        if (dragging) {
            winX = Math.max(0, Math.min(this.width  - winW, mx - dragOffX));
            winY = Math.max(0, Math.min(this.height - winH, my - dragOffY));
            rebuildWidgets(); return true;
        }

        // Text area drag selection
        if (!sidebarDragging && !dragging && resizing == ResizeEdge.NONE) {
            int taX2c = winX + contentX() + 2;
            int taYc  = winY + textAreaY();
            int taX2r = winX + winW - PADDING;
            int taY2c = taYc + textAreaH();
            if (mx >= taX2c && mx <= taX2r && my >= taYc && my <= taY2c) {
                int[] lc = pixelToLineCol(mx, my);
                cursorLine = lc[0]; cursorCol = lc[1];
                scrollToCursor();
                return true;
            }
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // Finalize sidebar drag
        if (sidebarDragging && sidebarDragItem != null) {
            int listStartY = winY + TITLE_BAR_HEIGHT + 16;
            List<SidebarEntry> rows = buildSidebarRows();
            int virtualIdx = sidebarScroll + (sidebarDragCurrentY - listStartY) / ITEM_HEIGHT;
            virtualIdx = Math.max(0, Math.min(rows.size(), virtualIdx));

            // Determine drop context
            String dropOnFolder = null;
            String dropInFolder = null; // folder that contains the drop position
            if (virtualIdx < rows.size()) {
                SidebarEntry target = rows.get(virtualIdx);
                if (target.kind() == RowKind.FOLDER) {
                    dropOnFolder = target.value();
                } else if (target.kind() == RowKind.FILE && target.inFolder()) {
                    dropInFolder = getParentFolder(target.value());
                }
            }

            if (sidebarDragKind == RowKind.FILE) {
                String currentParent = getParentFolder(sidebarDragItem);
                if (dropOnFolder != null) {
                    // Drop on a folder -> move file into it (unless already there)
                    if (!dropOnFolder.equals(currentParent)) {
                        moveFileToFolder(sidebarDragItem, dropOnFolder);
                    }
                } else if (dropInFolder != null) {
                    // Drop between files in a folder -> move there if different folder
                    if (!dropInFolder.equals(currentParent)) {
                        moveFileToFolder(sidebarDragItem, dropInFolder);
                    }
                    // else: same folder, could reorder but tree view controls order
                } else {
                    // Drop at root level
                    if (currentParent != null) {
                        // File is in a folder -> move to root
                        moveFileToRoot(sidebarDragItem);
                    } else {
                        // Already at root -> reorder
                        String anchorFile = null;
                        for (int i = virtualIdx; i < rows.size(); i++) {
                            if (rows.get(i).kind() == RowKind.FILE && !rows.get(i).inFolder()) {
                                anchorFile = rows.get(i).value(); break;
                            }
                        }
                        int fromIdx = allNoteFiles.indexOf(sidebarDragItem);
                        if (fromIdx >= 0) {
                            allNoteFiles.remove(sidebarDragItem);
                            if (anchorFile != null) {
                                int anchorPos = allNoteFiles.indexOf(anchorFile);
                                allNoteFiles.add(anchorPos >= 0 ? anchorPos : allNoteFiles.size(), sidebarDragItem);
                            } else {
                                allNoteFiles.add(sidebarDragItem);
                            }
                            saveOrder();
                            applySearch();
                        }
                    }
                }
            } else if (sidebarDragKind == RowKind.FOLDER) {
                String sourceFolder = sidebarDragItem;
                String sourceParent = getParentFolder(sourceFolder);
                if (dropOnFolder != null) {
                    // Don't drop into self or children
                    if (!dropOnFolder.equals(sourceFolder)
                            && !dropOnFolder.startsWith(sourceFolder + "/")) {
                        moveFolderInto(sourceFolder, dropOnFolder);
                    }
                } else if (dropInFolder != null) {
                    // Drop between files inside a folder
                    if (!dropInFolder.equals(sourceFolder)
                            && !dropInFolder.startsWith(sourceFolder + "/")
                            && !dropInFolder.equals(sourceParent)) {
                        moveFolderInto(sourceFolder, dropInFolder);
                    }
                } else {
                    // Drop at root level
                    if (sourceParent != null) {
                        moveFolderToRoot(sourceFolder);
                    }
                }
            }

            sidebarDragging      = false;
            sidebarDragItem      = null;
            sidebarDragKind      = null;
            lastSidebarClickMs   = 0;
            lastSidebarClickFile = null;
            lastSidebarClickFolder = null;
        }

        // Folder click toggle (deferred from mouseClicked to support drag)
        if (!sidebarDragging && lastSidebarClickFolder != null) {
            String folder = lastSidebarClickFolder;
            if (expandedFolders.contains(folder)) expandedFolders.remove(folder);
            else expandedFolders.add(folder);
            sidebarRowsDirty = true;
            lastSidebarClickFolder = null;
        }

        dragging = false;
        resizing = ResizeEdge.NONE;
        IgCalcWindowState.notesX       = winX;
        IgCalcWindowState.notesY       = winY;
        IgCalcWindowState.notesW       = winW;
        IgCalcWindowState.notesH       = winH;
        IgCalcWindowState.notesSidebar = sidebarVisible;
        IgCalcWindowState.save();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (sidebarVisible) {
            int sbRight = winX + PADDING + SIDEBAR_W;
            if (mx >= winX + PADDING && mx < sbRight
                    && my >= winY + TITLE_BAR_HEIGHT && my < winY + winH) {
                int totalRows = buildSidebarRows().size();
                sidebarScroll = Math.max(0, Math.min(
                        Math.max(0, totalRows - 1),
                        sidebarScroll - (int) vAmount));
                return true;
            }
        }
        rebuildVisualRows();
        scrollOffset = Math.max(0, Math.min(
                Math.max(0, visualRows.size() - 1),
                scrollOffset - (int) vAmount));
        return true;
    }

    // =========================================================================
    // Export helpers
    // =========================================================================
    private void exportToDesktop() {
        if (currentFile == null) { setStatus("No note to export."); return; }
        try {
            Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
            if (!Files.exists(desktop)) desktop = Path.of(System.getProperty("user.home"));
            String baseName = Path.of(currentFile).getFileName().toString();
            Path target = desktop.resolve(baseName);
            Files.writeString(target, String.join("\n", lines), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            setStatus("Exported to Desktop!");
        } catch (IOException e) {
            setStatus("Export failed.");
        }
    }

    private void openNotesInFileManager() {
        try {
            Path dir = getNotesDir();
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{ "open", dir.toString() });
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{ "explorer", dir.toString() });
            } else {
                Runtime.getRuntime().exec(new String[]{ "xdg-open", dir.toString() });
            }
            setStatus("Opened in file manager.");
        } catch (IOException e) {
            setStatus("Could not open folder.");
        }
    }

    // =========================================================================
    // Close
    // =========================================================================
    public void save() {
        saveCurrentNote();
        IgCalcWindowState.notesX        = winX;
        IgCalcWindowState.notesY        = winY;
        IgCalcWindowState.notesW        = winW;
        IgCalcWindowState.notesH        = winH;
        IgCalcWindowState.notesSidebar  = sidebarVisible;
        IgCalcWindowState.notesLastFile = currentFile;
        IgCalcWindowState.save();
    }

    private void openTutorial() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof IgCalcOverlay overlay) {
            com.igcalc.config.IgCalcTutorialState.reset();
            overlay.startTutorialFiltered(TutorialScreen.NOTES_STEPS);
        }
    }

    @Override
    public void close() {
        renaming = false; renamingFile = null;
        save();
        if (closeCallback != null) closeCallback.run();
        else super.close();
    }

    // =========================================================================
    // Keyboard input
    // =========================================================================
    @Override
    public boolean charTyped(CharInput input) {
        if (renaming       && renameField != null && renameField.isFocused()) return super.charTyped(input);
        if (renamingFolder && renameField != null && renameField.isFocused()) return super.charTyped(input);
        if (creatingFolder && renameField != null && renameField.isFocused()) return super.charTyped(input);
        if (searchActive   && searchField != null && searchField.isFocused()) return super.charTyped(input);
        if (hasSelection()) deleteSelection();
        char chr = (char) input.codepoint();
        String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorCol) + chr + line.substring(cursorCol));
        cursorCol++;
        markLinesChanged();
        lastEditMs = System.currentTimeMillis();
        scrollToCursor();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (renaming && renameField != null && renameField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { renameNote(renamingFile, renameField.getText()); return true; }
            if (keyCode == 256) { cancelRename(); return true; }
            return super.keyPressed(input);
        }
        if (renamingFolder && renameField != null && renameField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { renameFolder(renamingFolderOld, renameField.getText()); return true; }
            if (keyCode == 256) { cancelRenameFolder(); return true; }
            return super.keyPressed(input);
        }
        if (creatingFolder && renameField != null && renameField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { createFolderAndMoveFile(renameField.getText(), creatingFolderFor); return true; }
            if (keyCode == 256) { cancelCreatingFolder(); return true; }
            return super.keyPressed(input);
        }
        if (searchActive && searchField != null && searchField.isFocused()) {
            if (keyCode == 256) { searchActive = false; searchQuery = ""; applySearch(); rebuildWidgets(); return true; }
            return super.keyPressed(input);
        }

        boolean ctrl  = (input.modifiers() & 2) != 0;
        boolean shift = (input.modifiers() & 1) != 0;

        // ── Config-based hotkeys (checked before switch) ─────────────────────
        IgCalcConfig cfg = IgCalcConfig.getInstance();
        if (cfg.keyQuickNote.matches(keyCode, input.modifiers())) {
            triggerQuickNote(); return true;
        }
        if (cfg.keyNewNote.matches(keyCode, input.modifiers())) {
            newNote(); rebuildWidgets(); return true;
        }
        if (cfg.keyFindSidebar.matches(keyCode, input.modifiers())) {
            if (sidebarVisible) {
                searchActive = true; rebuildWidgets();
                if (searchField != null) setFocused(searchField);
            }
            return true;
        }
        if (cfg.keyInsertCoords.matches(keyCode, input.modifiers())) {
            insertPlayerCoords(); return true;
        }
        // Copy to clipboard (Ctrl+Shift+E default)
        if (cfg.keyCopyClipboard.matches(keyCode, input.modifiers())) {
            client.keyboard.setClipboard(String.join("\n", lines));
            setStatus("Copied to clipboard!");
            return true;
        }

        // ── Sidebar keyboard navigation ──────────────────────────────────────
        if (sidebarFocused && sidebarVisible) {
            List<SidebarEntry> navRows = buildSidebarRows();
            switch (keyCode) {
                case 265 -> { // Up
                    sidebarCursorIdx = Math.max(0, sidebarCursorIdx - 1);
                    return true;
                }
                case 264 -> { // Down
                    sidebarCursorIdx = Math.min(navRows.size() - 1, sidebarCursorIdx + 1);
                    return true;
                }
                case 257, 335 -> { // Enter — activate selected row
                    if (sidebarCursorIdx >= 0 && sidebarCursorIdx < navRows.size()) {
                        SidebarEntry sel = navRows.get(sidebarCursorIdx);
                        if (sel.kind() == RowKind.FOLDER) {
                            String f = sel.value();
                            if (expandedFolders.contains(f)) expandedFolders.remove(f);
                            else expandedFolders.add(f);
                            sidebarRowsDirty = true;
                        } else if (sel.kind() == RowKind.FILE) {
                            String selFile = sel.value();
                            if (!selFile.equals(currentFile)) {
                                saveCurrentNote();
                                currentFile = selFile;
                                IgCalcWindowState.notesLastFile = currentFile;
                                IgCalcWindowState.save();
                                loadCurrentNote();
                            }
                        }
                    }
                    sidebarFocused = false;
                    return true;
                }
                case 256, 258 -> { // Escape or Tab — return focus to editor
                    sidebarFocused = false;
                    return true;
                }
            }
        }

        switch (keyCode) {
            case 256 -> {
                // Dismiss menus before closing
                if (editorContextMenuVisible) { editorContextMenuVisible = false; return true; }
                if (keybindCardVisible) { keybindCardVisible = false; return true; }
                if (exportMenuVisible) { exportMenuVisible = false; return true; }
                if (contextMenuVisible) { contextMenuVisible = false; contextMenuFile = null; contextMenuFolder = null; contextMenuSidebarBg = false; return true; }
                this.close(); return true;
            }
            case 257, 335 -> {
                if (hasSelection()) deleteSelection();
                selAnchorLine = -1;
                markLinesChanged();
                String cur  = lines.get(cursorLine);
                String beforeCursor = cur.substring(0, cursorCol);
                String tail = cur.substring(cursorCol);

                // ── List continuation ─────────────────────────────────────
                String newPrefix = null;
                if (cur.startsWith("- [ ] ") || cur.startsWith("- [x] ")) {
                    String content = cur.substring(6);
                    if (content.trim().isEmpty()) { lines.set(cursorLine, ""); cursorCol = 0; lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                    newPrefix = "- [ ] ";
                } else if (cur.startsWith("  - [ ] ") || cur.startsWith("  - [x] ")) {
                    String content = cur.substring(8);
                    if (content.trim().isEmpty()) { lines.set(cursorLine, ""); cursorCol = 0; lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                    newPrefix = "  - [ ] ";
                } else if (cur.startsWith("  - ") || cur.startsWith("  * ")) {
                    String pfx = cur.substring(0, 4);
                    String content = cur.substring(4);
                    if (content.trim().isEmpty()) { lines.set(cursorLine, ""); cursorCol = 0; lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                    newPrefix = pfx;
                } else if (cur.startsWith("- ") || cur.startsWith("* ")) {
                    String pfx = cur.substring(0, 2);
                    String content = cur.substring(2);
                    if (content.trim().isEmpty()) { lines.set(cursorLine, ""); cursorCol = 0; lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                    newPrefix = pfx;
                } else if (cur.startsWith("> ")) {
                    String content = cur.substring(2);
                    if (content.trim().isEmpty()) { lines.set(cursorLine, ""); cursorCol = 0; lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                    newPrefix = "> ";
                } else {
                    int dot = cur.indexOf(". ");
                    if (dot >= 1 && dot <= 3) {
                        boolean numbered = true;
                        for (int ii = 0; ii < dot; ii++) if (!Character.isDigit(cur.charAt(ii))) { numbered = false; break; }
                        if (numbered) {
                            String content = cur.substring(dot + 2);
                            if (content.trim().isEmpty()) { lines.set(cursorLine, ""); cursorCol = 0; lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                            int num = Integer.parseInt(cur.substring(0, dot));
                            newPrefix = (num + 1) + ". ";
                        }
                    }
                }

                lines.set(cursorLine, beforeCursor);
                if (newPrefix != null) {
                    lines.add(cursorLine + 1, newPrefix + tail);
                    cursorLine++; cursorCol = newPrefix.length();
                } else {
                    lines.add(cursorLine + 1, tail);
                    cursorLine++; cursorCol = 0;
                }
                // "___" + Enter → convert to horizontal rule
                if (cursorLine > 0 && lines.get(cursorLine - 1).trim().equals("___")) {
                    lines.set(cursorLine - 1, "---");
                }
                lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
            }
            case 258 -> {
                // Tab key: Shift+Tab → sidebar focus (if visible) or de-indent
                //           Tab     → indent bullet or insert 2 spaces
                String line = lines.get(cursorLine);
                boolean isBulletLine = line.startsWith("- ") || line.startsWith("* ")
                        || line.startsWith("- [ ]") || line.startsWith("- [x]");
                boolean isSubBullet  = line.startsWith("  - ") || line.startsWith("  * ")
                        || line.startsWith("  - [ ]") || line.startsWith("  - [x]");
                if (shift) {
                    if (sidebarVisible) {
                        sidebarFocused = true;
                        if (sidebarCursorIdx < 0 || sidebarCursorIdx >= buildSidebarRows().size())
                            sidebarCursorIdx = 0;
                        return true;
                    } else if (isSubBullet || line.startsWith("  ")) {
                        lines.set(cursorLine, line.substring(Math.min(2, line.length())));
                        cursorCol = Math.max(0, cursorCol - 2);
                        markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor();
                    }
                } else if (isSubBullet) {
                    // Tab on an already-indented bullet → unindent
                    lines.set(cursorLine, line.substring(2));
                    cursorCol = Math.max(0, cursorCol - 2);
                    markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
                } else if (isBulletLine) {
                    lines.set(cursorLine, "  " + line);
                    cursorCol += 2; markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
                } else {
                    lines.set(cursorLine, line.substring(0, cursorCol) + "  " + line.substring(cursorCol));
                    cursorCol += 2; markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
                }
                return true;
            }
            case 259 -> {
                if (hasSelection()) { deleteSelection(); markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                if (cursorCol > 0) {
                    String line = lines.get(cursorLine);
                    lines.set(cursorLine, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
                    cursorCol--;
                } else if (cursorLine > 0) {
                    String prev = lines.get(cursorLine - 1);
                    String curr = lines.get(cursorLine);
                    int joinCol = prev.length();
                    lines.set(cursorLine - 1, prev + curr);
                    lines.remove(cursorLine);
                    cursorLine--; cursorCol = joinCol;
                }
                markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
            }
            case 261 -> {
                if (hasSelection()) { deleteSelection(); markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true; }
                String line = lines.get(cursorLine);
                if (cursorCol < line.length()) {
                    lines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
                } else if (cursorLine < lines.size() - 1) {
                    lines.set(cursorLine, line + lines.get(cursorLine + 1));
                    lines.remove(cursorLine + 1);
                }
                markLinesChanged(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
            }
            case 263 -> {
                if (!shift && hasSelection()) {
                    SelectionRange sel = getSelection();
                    cursorLine = sel.startLine(); cursorCol = sel.startCol();
                    selAnchorLine = -1; scrollToCursor(); return true;
                }
                if (shift && selAnchorLine < 0) { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
                if (!shift) selAnchorLine = -1;
                if (cursorCol > 0) cursorCol--;
                else if (cursorLine > 0) { cursorLine--; cursorCol = lines.get(cursorLine).length(); }
                scrollToCursor(); return true;
            }
            case 262 -> {
                if (!shift && hasSelection()) {
                    SelectionRange sel = getSelection();
                    cursorLine = sel.endLine(); cursorCol = sel.endCol();
                    selAnchorLine = -1; scrollToCursor(); return true;
                }
                if (shift && selAnchorLine < 0) { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
                if (!shift) selAnchorLine = -1;
                String line = lines.get(cursorLine);
                if (cursorCol < line.length()) cursorCol++;
                else if (cursorLine < lines.size() - 1) { cursorLine++; cursorCol = 0; }
                scrollToCursor(); return true;
            }
            case 264 -> {
                if (shift && selAnchorLine < 0) { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
                if (!shift) selAnchorLine = -1;
                rebuildVisualRows();
                int vrDown = cursorVisualRow();
                if (vrDown < visualRows.size() - 1) {
                    VisualRow next = visualRows.get(vrDown + 1);
                    int col = cursorCol - visualRows.get(vrDown).startCol();
                    cursorLine = next.logLine();
                    cursorCol  = Math.min(lines.get(cursorLine).length(), next.startCol() + col);
                }
                scrollToCursor(); return true;
            }
            case 265 -> {
                if (shift && selAnchorLine < 0) { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
                if (!shift) selAnchorLine = -1;
                rebuildVisualRows();
                int vrUp = cursorVisualRow();
                if (vrUp > 0) {
                    VisualRow prev = visualRows.get(vrUp - 1);
                    int col = cursorCol - visualRows.get(vrUp).startCol();
                    cursorLine = prev.logLine();
                    cursorCol  = Math.min(lines.get(cursorLine).length(), prev.startCol() + col);
                }
                scrollToCursor(); return true;
            }
            case 268 -> {
                if (shift && selAnchorLine < 0) { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
                if (!shift) selAnchorLine = -1;
                cursorCol = 0; scrollToCursor(); return true;
            }
            case 269 -> {
                if (shift && selAnchorLine < 0) { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
                if (!shift) selAnchorLine = -1;
                cursorCol = lines.get(cursorLine).length(); scrollToCursor(); return true;
            }
            case 83 -> { if (ctrl) { saveCurrentNote(); return true; } }
            case 65 -> {
                if (ctrl) {
                    selAnchorLine = 0; selAnchorCol = 0;
                    cursorLine = lines.size() - 1;
                    cursorCol = lines.get(cursorLine).length();
                    return true;
                }
            }
            case 67 -> {
                if (ctrl && hasSelection()) {
                    client.keyboard.setClipboard(getSelectedText()); return true;
                }
            }
            case 88 -> {
                if (ctrl && hasSelection()) {
                    client.keyboard.setClipboard(getSelectedText());
                    deleteSelection(); lastEditMs = System.currentTimeMillis(); scrollToCursor(); return true;
                }
            }
            case 86 -> {
                if (ctrl) { pasteFromClipboard(); return true; }
            }
        }
        return super.keyPressed(input);
    }

    // =========================================================================
    // Misc
    // =========================================================================
    /** Call whenever lines content changes; invalidates the visual-row cache. */
    private void markLinesChanged() { linesVersion++; }

    @Override
    public boolean shouldPause() { return false; }
}
