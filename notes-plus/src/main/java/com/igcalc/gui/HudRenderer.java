package com.igcalc.gui;

import com.igcalc.config.IgCalcHudState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Singleton that holds all pinned HUD state and renders it each frame.
 */
public class HudRenderer {

    public static final HudRenderer INSTANCE = new HudRenderer();

    // -------------------------------------------------------------------------
    // Notes pinned state
    // -------------------------------------------------------------------------
    public boolean      pinnedNotes      = false;
    public boolean      notesVisible     = true;
    public String       notesCurrentFile = null;
    public List<String> notesLines       = new ArrayList<>();
    public int          notesScroll      = 0;

    // Notes nav / search
    public List<String>  notesAllFiles         = new ArrayList<>();
    public String        notesDir              = null;
    public String        notesWorldContext     = null;
    public boolean       notesSearchActive     = false;
    public String        notesSearchQuery      = "";
    public int           notesSearchSelectedIdx = 0;
    private List<String> notesSearchResults    = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Timer pinned state
    // -------------------------------------------------------------------------
    public boolean           pinnedTimer          = false;
    public boolean           timerVisible         = true;
    public TimerScreen.State timerState           = TimerScreen.State.IDLE;
    public long              timerStartMs         = 0;
    public long              timerDurationMs      = 0;
    public long              timerPausedElapsedMs = 0;
    public int[]             timerDigits          = new int[6];
    public int[]             timerLastDigits      = new int[6];

    // Stopwatch state (mirrors TimerScreen when pinned in STOPWATCH mode)
    public TimerScreen.Mode    timerMode   = TimerScreen.Mode.TIMER;
    public TimerScreen.SwState swState     = TimerScreen.SwState.IDLE;
    public long                swStartMs   = 0;
    public long                swElapsedMs = 0;
    public List<Long>          swLapTimes  = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Toast
    // -------------------------------------------------------------------------
    public String toastText     = null;
    public long   toastExpireMs = 0;

    // -------------------------------------------------------------------------
    // Global state
    // -------------------------------------------------------------------------
    public boolean  hudGlobalVisible  = true;
    public boolean  interactionActive = false;
    public Runnable onTimerComplete   = null;

    // -------------------------------------------------------------------------
    // Direct-edit state for pinned Notes HUD
    // -------------------------------------------------------------------------
    public boolean notesEditingActive  = false;
    public int     notesEditCursorLine = 0;
    public int     notesEditCursorCol  = 0;
    private long   notesEditLastModMs  = 0;
    private static final int EDIT_AUTOSAVE_MS = 2000;
    private long   lastClickMs         = 0;
    private int    notesWrappedLineCount = 0;  // total wrapped lines, computed during render

    // Selection state (-1 = no selection)
    public int     notesSelAnchorLine = -1;
    public int     notesSelAnchorCol  = -1;

    // -------------------------------------------------------------------------
    // Corner-drag resize state (used in interaction mode)
    // -------------------------------------------------------------------------
    private boolean resizingNotes     = false;
    private boolean resizingTimer     = false;
    private int     resizeDragStartX  = 0;
    private int     resizeDragStartY  = 0;
    private int     resizeOrigW       = 0;
    private int     resizeOrigH       = 0;
    private static final int RESIZE_CORNER = 8;

    private HudRenderer() {}

    // =========================================================================
    // Registration
    // =========================================================================
    public void register() {
        HudRenderCallback.EVENT.register((ctx, tc) -> render(ctx));
    }

    // =========================================================================
    // Tick (called from ClientTickEvents.END_CLIENT_TICK)
    // =========================================================================
    public void tick(MinecraftClient client) {
        if (!pinnedTimer || timerMode != TimerScreen.Mode.TIMER) return;
        if (timerState != TimerScreen.State.ACTIVE) return;
        if (getTimerEffectiveElapsedMs() >= timerDurationMs) {
            timerPausedElapsedMs = timerDurationMs;
            timerState = TimerScreen.State.COMPLETE;
            if (client.player != null)
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), 1.0f, 2.0f);
            if (onTimerComplete != null) onTimerComplete.run();
        }
        // Notes editing autosave
        if (notesEditingActive && notesEditLastModMs > 0
                && System.currentTimeMillis() - notesEditLastModMs > EDIT_AUTOSAVE_MS) {
            saveEditedNote();
            notesEditLastModMs = 0;
        }
    }

    // =========================================================================
    // Pin / unpin helpers
    // =========================================================================
    public void pinNotes(NotesScreen ns) {
        pinnedNotes      = true;
        notesVisible     = true;
        notesCurrentFile = ns.getCurrentFile();
        notesLines       = new ArrayList<>(ns.getLines());
        notesScroll      = 0;
        notesAllFiles    = new ArrayList<>(ns.getAllFiles());
        Path dir         = ns.getNotesDirPath();
        notesDir         = (dir != null) ? dir.toString() : null;
        notesWorldContext = ns.getWorldContextName();
        IgCalcHudState.notesHudPinned = true;
        IgCalcHudState.save();
    }

    public void pinTimer(TimerScreen ts) {
        pinnedTimer          = true;
        timerVisible         = true;
        timerState           = ts.state;
        timerStartMs         = ts.startMs;
        timerDurationMs      = ts.durationMs;
        timerPausedElapsedMs = ts.pausedElapsedMs;
        timerDigits          = ts.digits.clone();
        timerLastDigits      = ts.lastDigits.clone();
        timerMode            = ts.currentMode;
        swState              = ts.swState;
        swStartMs            = ts.swStartMs;
        swElapsedMs          = ts.swElapsedMs;
        swLapTimes           = new ArrayList<>(ts.swLaps);
        IgCalcHudState.timerHudPinned = true;
        IgCalcHudState.save();
    }

    public void restoreTimerState(TimerScreen ts) {
        ts.state           = timerState;
        ts.startMs         = timerStartMs;
        ts.durationMs      = timerDurationMs;
        ts.pausedElapsedMs = timerPausedElapsedMs;
        ts.digits          = timerDigits.clone();
        ts.lastDigits      = timerLastDigits.clone();
        ts.currentMode     = timerMode;
        ts.swState         = swState;
        ts.swStartMs       = swStartMs;
        ts.swElapsedMs     = swElapsedMs;
        ts.swLaps          = new ArrayList<>(swLapTimes);
    }

    // =========================================================================
    // Notes direct editing (in HUD)
    // =========================================================================
    public boolean hasSelection() {
        return notesSelAnchorLine >= 0 && notesSelAnchorCol >= 0
            && (notesSelAnchorLine != notesEditCursorLine || notesSelAnchorCol != notesEditCursorCol);
    }

    /** Returns [startLine, startCol, endLine, endCol] of the selection, ordered. */
    private int[] getSelectionRange() {
        int sL = notesSelAnchorLine, sC = notesSelAnchorCol;
        int eL = notesEditCursorLine, eC = notesEditCursorCol;
        if (sL > eL || (sL == eL && sC > eC)) {
            int tmp; tmp = sL; sL = eL; eL = tmp; tmp = sC; sC = eC; eC = tmp;
        }
        return new int[]{sL, sC, eL, eC};
    }

    public String getSelectedText() {
        if (!hasSelection()) return "";
        int[] r = getSelectionRange();
        if (r[0] == r[2]) return notesLines.get(r[0]).substring(r[1], r[3]);
        StringBuilder sb = new StringBuilder();
        sb.append(notesLines.get(r[0]).substring(r[1]));
        for (int i = r[0] + 1; i < r[2]; i++) sb.append('\n').append(notesLines.get(i));
        sb.append('\n').append(notesLines.get(r[2]), 0, r[3]);
        return sb.toString();
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int[] r = getSelectionRange();
        if (r[0] == r[2]) {
            String line = notesLines.get(r[0]);
            notesLines.set(r[0], line.substring(0, r[1]) + line.substring(r[3]));
        } else {
            String first = notesLines.get(r[0]).substring(0, r[1]);
            String last  = notesLines.get(r[2]).substring(r[3]);
            notesLines.set(r[0], first + last);
            for (int i = r[2]; i > r[0]; i--) notesLines.remove(i);
        }
        notesEditCursorLine = r[0];
        notesEditCursorCol  = r[1];
        clearSelection();
    }

    private void clearSelection() {
        notesSelAnchorLine = -1;
        notesSelAnchorCol  = -1;
    }

    private void startSelectionIfNeeded() {
        if (notesSelAnchorLine < 0) {
            notesSelAnchorLine = notesEditCursorLine;
            notesSelAnchorCol  = notesEditCursorCol;
        }
    }

    public void handleEditChar(int codepoint) {
        if (notesLines.isEmpty()) notesLines.add("");
        if (hasSelection()) deleteSelection();
        notesEditCursorLine = Math.max(0, Math.min(notesEditCursorLine, notesLines.size() - 1));
        String line = notesLines.get(notesEditCursorLine);
        notesEditCursorCol  = Math.max(0, Math.min(notesEditCursorCol, line.length()));
        String newLine = line.substring(0, notesEditCursorCol) + (char) codepoint + line.substring(notesEditCursorCol);
        notesLines.set(notesEditCursorLine, newLine);
        notesEditCursorCol++;
        clearSelection();
        notesEditLastModMs = System.currentTimeMillis();
    }

    public boolean handleEditKey(int key, int modifiers) {
        if (notesLines.isEmpty()) notesLines.add("");
        notesEditCursorLine = Math.max(0, Math.min(notesEditCursorLine, notesLines.size() - 1));
        String line = notesLines.get(notesEditCursorLine);
        notesEditCursorCol  = Math.max(0, Math.min(notesEditCursorCol, line.length()));

        boolean shift = (modifiers & 1) != 0;
        boolean ctrl  = (modifiers & 2) != 0;

        // Ctrl+A — select all
        if (ctrl && key == 65) {
            notesSelAnchorLine = 0;
            notesSelAnchorCol  = 0;
            notesEditCursorLine = notesLines.size() - 1;
            notesEditCursorCol  = notesLines.get(notesEditCursorLine).length();
            return true;
        }
        // Ctrl+C — copy
        if (ctrl && key == 67) {
            if (hasSelection()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), getSelectedText());
            }
            return true;
        }
        // Ctrl+X — cut
        if (ctrl && key == 88) {
            if (hasSelection()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), getSelectedText());
                deleteSelection();
                notesEditLastModMs = System.currentTimeMillis();
            }
            return true;
        }
        // Ctrl+V — paste
        if (ctrl && key == 86) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                String clip = org.lwjgl.glfw.GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                if (clip != null && !clip.isEmpty()) {
                    if (hasSelection()) deleteSelection();
                    line = notesLines.get(notesEditCursorLine);
                    notesEditCursorCol = Math.min(notesEditCursorCol, line.length());
                    String[] pasteLines = clip.split("\n", -1);
                    if (pasteLines.length == 1) {
                        String newLine = line.substring(0, notesEditCursorCol) + pasteLines[0] + line.substring(notesEditCursorCol);
                        notesLines.set(notesEditCursorLine, newLine);
                        notesEditCursorCol += pasteLines[0].length();
                    } else {
                        String before = line.substring(0, notesEditCursorCol);
                        String after  = line.substring(notesEditCursorCol);
                        notesLines.set(notesEditCursorLine, before + pasteLines[0]);
                        for (int i = 1; i < pasteLines.length - 1; i++) {
                            notesLines.add(notesEditCursorLine + i, pasteLines[i]);
                        }
                        notesLines.add(notesEditCursorLine + pasteLines.length - 1, pasteLines[pasteLines.length - 1] + after);
                        notesEditCursorLine += pasteLines.length - 1;
                        notesEditCursorCol = pasteLines[pasteLines.length - 1].length();
                    }
                    clearSelection();
                    notesEditLastModMs = System.currentTimeMillis();
                }
            }
            return true;
        }

        switch (key) {
            case 256 -> { // Escape — exit edit mode and save
                notesEditingActive = false;
                clearSelection();
                saveEditedNote();
                notesEditLastModMs = 0;
                return true;
            }
            case 257, 335 -> { // Enter
                if (hasSelection()) deleteSelection();
                line = notesLines.get(notesEditCursorLine);
                notesEditCursorCol = Math.min(notesEditCursorCol, line.length());
                String before = line.substring(0, notesEditCursorCol);
                String after  = line.substring(notesEditCursorCol);
                notesLines.set(notesEditCursorLine, before);
                notesLines.add(notesEditCursorLine + 1, after);
                notesEditCursorLine++;
                notesEditCursorCol = 0;
                clearSelection();
                notesEditLastModMs = System.currentTimeMillis();
                return true;
            }
            case 259 -> { // Backspace
                if (hasSelection()) {
                    deleteSelection();
                } else if (notesEditCursorCol > 0) {
                    String newLine = line.substring(0, notesEditCursorCol - 1) + line.substring(notesEditCursorCol);
                    notesLines.set(notesEditCursorLine, newLine);
                    notesEditCursorCol--;
                } else if (notesEditCursorLine > 0) {
                    String prev   = notesLines.get(notesEditCursorLine - 1);
                    int joinCol   = prev.length();
                    notesLines.set(notesEditCursorLine - 1, prev + line);
                    notesLines.remove(notesEditCursorLine);
                    notesEditCursorLine--;
                    notesEditCursorCol = joinCol;
                }
                clearSelection();
                notesEditLastModMs = System.currentTimeMillis();
                return true;
            }
            case 261 -> { // Delete
                if (hasSelection()) {
                    deleteSelection();
                } else if (notesEditCursorCol < line.length()) {
                    String newLine = line.substring(0, notesEditCursorCol) + line.substring(notesEditCursorCol + 1);
                    notesLines.set(notesEditCursorLine, newLine);
                } else if (notesEditCursorLine < notesLines.size() - 1) {
                    notesLines.set(notesEditCursorLine, line + notesLines.get(notesEditCursorLine + 1));
                    notesLines.remove(notesEditCursorLine + 1);
                }
                clearSelection();
                notesEditLastModMs = System.currentTimeMillis();
                return true;
            }
            case 263 -> { // Left
                if (shift) startSelectionIfNeeded();
                else if (hasSelection()) { int[] r = getSelectionRange(); notesEditCursorLine = r[0]; notesEditCursorCol = r[1]; clearSelection(); return true; }
                if (notesEditCursorCol > 0) notesEditCursorCol--;
                else if (notesEditCursorLine > 0) {
                    notesEditCursorLine--;
                    notesEditCursorCol = notesLines.get(notesEditCursorLine).length();
                }
                if (!shift) clearSelection();
                return true;
            }
            case 262 -> { // Right
                if (shift) startSelectionIfNeeded();
                else if (hasSelection()) { int[] r = getSelectionRange(); notesEditCursorLine = r[2]; notesEditCursorCol = r[3]; clearSelection(); return true; }
                if (notesEditCursorCol < line.length()) notesEditCursorCol++;
                else if (notesEditCursorLine < notesLines.size() - 1) {
                    notesEditCursorLine++;
                    notesEditCursorCol = 0;
                }
                if (!shift) clearSelection();
                return true;
            }
            case 265 -> { // Up
                if (shift) startSelectionIfNeeded();
                if (notesEditCursorLine > 0) {
                    notesEditCursorLine--;
                    notesEditCursorCol = Math.min(notesEditCursorCol, notesLines.get(notesEditCursorLine).length());
                }
                if (!shift) clearSelection();
                return true;
            }
            case 264 -> { // Down
                if (shift) startSelectionIfNeeded();
                if (notesEditCursorLine < notesLines.size() - 1) {
                    notesEditCursorLine++;
                    notesEditCursorCol = Math.min(notesEditCursorCol, notesLines.get(notesEditCursorLine).length());
                }
                if (!shift) clearSelection();
                return true;
            }
            case 268 -> { // Home
                if (shift) startSelectionIfNeeded();
                notesEditCursorCol = 0;
                if (!shift) clearSelection();
                return true;
            }
            case 269 -> { // End
                if (shift) startSelectionIfNeeded();
                notesEditCursorCol = line.length();
                if (!shift) clearSelection();
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Notes editing helpers
    // =========================================================================
    public void saveEditedNote() {
        if (notesCurrentFile == null || notesDir == null) return;
        try {
            Path path = Path.of(notesDir).resolve(notesCurrentFile);
            Files.writeString(path, String.join("\n", notesLines),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    // =========================================================================
    // Notes navigation
    // =========================================================================
    public void scrollNotesUp()   { notesScroll = Math.max(0, notesScroll - 1); }
    public void scrollNotesDown() { notesScroll = Math.min(Math.max(0, notesWrappedLineCount - 1), notesScroll + 1); }

    public void prevNote() { loadNoteAtOffset(-1); }
    public void nextNote() { loadNoteAtOffset(+1); }

    private void loadNoteAtOffset(int delta) {
        if (notesAllFiles.isEmpty() || notesDir == null) return;
        int idx  = notesAllFiles.indexOf(notesCurrentFile);
        int next = (idx + delta + notesAllFiles.size()) % notesAllFiles.size();
        String filename = notesAllFiles.get(next);
        try {
            notesLines = new ArrayList<>(
                    Files.readAllLines(Path.of(notesDir).resolve(filename), StandardCharsets.UTF_8));
        } catch (IOException e) {
            notesLines = new ArrayList<>();
        }
        notesCurrentFile = filename;
        notesScroll = 0;
    }

    // =========================================================================
    // Notes search
    // =========================================================================
    public void handleSearchChar(int codepoint) {
        notesSearchQuery += (char) codepoint;
        updateSearchResults();
    }

    public void handleSearchBackspace() {
        if (!notesSearchQuery.isEmpty())
            notesSearchQuery = notesSearchQuery.substring(0, notesSearchQuery.length() - 1);
        updateSearchResults();
    }

    private void updateSearchResults() {
        String q = notesSearchQuery.toLowerCase();
        notesSearchResults = notesAllFiles.stream()
                .filter(f -> f.toLowerCase().contains(q))
                .limit(8)
                .collect(Collectors.toList());
        notesSearchSelectedIdx = 0;
    }

    public void toggleSearch() {
        notesSearchActive = !notesSearchActive;
        if (!notesSearchActive) {
            notesSearchQuery = "";
            notesSearchResults.clear();
            notesSearchSelectedIdx = 0;
        }
    }

    public boolean handleSearchKeyNav(int key) {
        if (notesSearchResults.isEmpty()) return false;
        if (key == 265) { // Up
            notesSearchSelectedIdx = (notesSearchSelectedIdx - 1 + notesSearchResults.size()) % notesSearchResults.size();
            return true;
        }
        if (key == 264) { // Down
            notesSearchSelectedIdx = (notesSearchSelectedIdx + 1) % notesSearchResults.size();
            return true;
        }
        if (key == 257 || key == 335) { // Enter / KP Enter
            selectSearchResult(notesSearchResults.get(notesSearchSelectedIdx));
            return true;
        }
        return false;
    }

    public void selectSearchResult(String filename) {
        if (notesDir == null) return;
        try {
            notesLines = new ArrayList<>(
                    Files.readAllLines(Path.of(notesDir).resolve(filename), StandardCharsets.UTF_8));
        } catch (IOException e) {
            notesLines = new ArrayList<>();
        }
        notesCurrentFile  = filename;
        notesScroll       = 0;
        notesSearchActive = false;
        notesSearchQuery  = "";
        notesSearchResults.clear();
    }

    // =========================================================================
    // Timer interaction
    // =========================================================================
    public void pauseOrResumeTimer() {
        if (timerState == TimerScreen.State.ACTIVE) {
            timerPausedElapsedMs = System.currentTimeMillis() - timerStartMs;
            timerState = TimerScreen.State.PAUSED;
        } else if (timerState == TimerScreen.State.PAUSED) {
            timerStartMs = System.currentTimeMillis() - timerPausedElapsedMs;
            timerState   = TimerScreen.State.ACTIVE;
        }
    }

    public void cancelTimer() {
        timerState  = TimerScreen.State.IDLE;
        timerDigits = new int[6];
    }

    public void toggleStopwatch() {
        timerMode = TimerScreen.Mode.STOPWATCH;
        switch (swState) {
            case IDLE, STOPPED -> {
                swStartMs = System.currentTimeMillis();
                swState = TimerScreen.SwState.RUNNING;
            }
            case RUNNING -> {
                swElapsedMs = getSwEffectiveElapsedMs();
                swState = TimerScreen.SwState.STOPPED;
            }
        }
    }

    public void resetStopwatch() {
        timerMode = TimerScreen.Mode.STOPWATCH;
        swState = TimerScreen.SwState.IDLE;
        swElapsedMs = 0;
        swStartMs = 0;
        swLapTimes.clear();
    }

    public void repeatTimer() {
        timerDigits = timerLastDigits.clone();
        int totalSec = timerDigits[0] * 36000 + timerDigits[1] * 3600
                     + timerDigits[2] * 600   + timerDigits[3] * 60
                     + timerDigits[4] * 10    + timerDigits[5];
        timerDurationMs      = totalSec * 1000L;
        timerStartMs         = System.currentTimeMillis();
        timerPausedElapsedMs = 0;
        timerState           = TimerScreen.State.ACTIVE;
    }

    // =========================================================================
    // Time helpers
    // =========================================================================
    private long getTimerEffectiveElapsedMs() {
        return (timerState == TimerScreen.State.ACTIVE)
            ? System.currentTimeMillis() - timerStartMs
            : timerPausedElapsedMs;
    }

    private long getSwEffectiveElapsedMs() {
        return (swState == TimerScreen.SwState.RUNNING)
            ? swElapsedMs + (System.currentTimeMillis() - swStartMs)
            : swElapsedMs;
    }

    // =========================================================================
    // Rendering (HudRenderCallback — fires every frame)
    // =========================================================================
    public void render(DrawContext ctx) {
        if (!hudGlobalVisible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        if (pinnedTimer && timerVisible) renderTimer(ctx, mc, screenW, screenH);
        if (pinnedNotes && notesVisible) renderNotes(ctx, mc, screenW, screenH, -1, -1);
        drawToast(ctx, mc, screenW);
    }

    /** Called from HudInteractionScreen.render() */
    public void renderInteractive(DrawContext ctx, int mx, int my, float delta) {
        if (!hudGlobalVisible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        if (pinnedTimer && timerVisible) renderTimerInteractive(ctx, mc, screenW, screenH, mx, my);
        if (pinnedNotes && notesVisible) renderNotesInteractive(ctx, mc, screenW, screenH, mx, my);
        drawToast(ctx, mc, screenW);
    }

    private void drawToast(DrawContext ctx, MinecraftClient mc, int screenW) {
        if (toastText == null) return;
        if (System.currentTimeMillis() >= toastExpireMs) { toastText = null; return; }
        TextRenderer tr = mc.textRenderer;
        int nw = tr.getWidth(toastText) + 16;
        int nh = 16, nx = screenW - nw - 8, ny = 8;
        ctx.fill(nx, ny,          nx + nw, ny + nh,     0xEE1C1E2B);
        ctx.fill(nx, ny,          nx + nw, ny + 1,      0xFF3A3F52);
        ctx.fill(nx, ny + nh - 1, nx + nw, ny + nh,     0xFF3A3F52);
        ctx.drawText(tr, toastText, nx + 8, ny + 4, 0xFFE8C170, false);
    }

    // =========================================================================
    // Timer HUD rendering
    // =========================================================================
    private int[] getTimerHudPos(int screenW, int screenH) {
        int x = IgCalcHudState.timerHudX;
        int y = IgCalcHudState.timerHudY;
        int w = IgCalcHudState.timerHudW;
        int h = IgCalcHudState.timerHudH;
        if (x < 0) x = screenW - w - 8;
        if (y < 0) y = 8;
        x = Math.max(0, Math.min(x, screenW - w));
        y = Math.max(0, Math.min(y, screenH - h));
        return new int[]{x, y, w, h};
    }

    private void renderTimer(DrawContext ctx, MinecraftClient mc, int screenW, int screenH) {
        int[] pos = getTimerHudPos(screenW, screenH);
        int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
        float opacity = IgCalcHudState.timerHudOpacity;
        boolean showBg = IgCalcHudState.timerHudShowBg;

        if (showBg) {
            int bgColor = applyOpacity(0xEE1C1E2B, opacity);
            int brColor = applyOpacity(0xFF3A3F52, opacity);
            fillRoundedRect(ctx, x, y, w, h, 4, bgColor);
            ctx.fill(x,         y,         x + w, y + 1,     brColor);
            ctx.fill(x,         y + h - 1, x + w, y + h,     brColor);
            ctx.fill(x,         y,         x + 1, y + h,     brColor);
            ctx.fill(x + w - 1, y,         x + w, y + h,     brColor);
        }

        TextRenderer tr = mc.textRenderer;

        // Stopwatch mode
        if (timerMode == TimerScreen.Mode.STOPWATCH) {
            long elapsed = getSwEffectiveElapsedMs();
            int cs  = (int)(elapsed /       10) % 100;
            int ss  = (int)(elapsed /     1000) % 60;
            int mm  = (int)(elapsed /    60000) % 60;
            int hh  = (int)(elapsed / 3600000L);
            String display = String.format("%02d:%02d:%02d.%02d", hh, mm, ss, cs);
            int boxW = w - 4;
            float textScale = Math.min(2.0f, Math.max(0.5f, (boxW * 0.8f) / 66f));
            int displayW = tr.getWidth(display);
            int drawX    = x + (int)((w - displayW * textScale) / 2);
            int drawY    = y + (int)((h - tr.fontHeight * textScale) / 2);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(drawX, drawY);
            ctx.getMatrices().scale(textScale, textScale);
            ctx.drawText(tr, display, 0, 0, applyOpacity(showBg ? 0xFFE5E5E5 : 0xFFFFFFFF, opacity), false);
            ctx.getMatrices().popMatrix();
            return;
        }

        // Countdown timer mode
        String countStr;
        int    textColor;
        switch (timerState) {
            case IDLE -> {
                countStr  = "--:--:--";
                textColor = applyOpacity(showBg ? 0xFFB0B8D0 : 0xFFFFFFFF, opacity);
            }
            case ACTIVE -> {
                long rem = Math.max(0, timerDurationMs - getTimerEffectiveElapsedMs());
                int  ts  = (int)(rem / 1000);
                countStr  = String.format("%02d:%02d:%02d", ts / 3600, (ts % 3600) / 60, ts % 60);
                textColor = applyOpacity(showBg ? 0xFFE5E5E5 : 0xFFFFFFFF, opacity);
            }
            case PAUSED -> {
                long rem = Math.max(0, timerDurationMs - timerPausedElapsedMs);
                int  ts  = (int)(rem / 1000);
                countStr  = String.format("%02d:%02d:%02d", ts / 3600, (ts % 3600) / 60, ts % 60);
                textColor = applyOpacity(showBg ? 0xFF7ABAFF : 0xFFFFFFFF, opacity);
            }
            default -> { // COMPLETE
                countStr  = "DONE";
                textColor = applyOpacity(showBg ? 0xFFE8C170 : 0xFFFFFFFF, opacity);
            }
        }

        int boxW = w - 4;
        int displayW = tr.getWidth(countStr);
        float textScale = Math.min(2.0f, Math.max(0.5f, (boxW * 0.8f) / (float) displayW));
        int drawX    = x + (int)((w - displayW * textScale) / 2);
        int drawY    = y + (int)((h - tr.fontHeight * textScale) / 2);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(drawX, drawY);
        ctx.getMatrices().scale(textScale, textScale);
        ctx.drawText(tr, countStr, 0, 0, textColor, false);
        ctx.getMatrices().popMatrix();

        if (timerState == TimerScreen.State.PAUSED) {
            String lbl = "(paused)";
            ctx.drawText(tr, lbl, x + (w - tr.getWidth(lbl)) / 2, drawY + (int)(tr.fontHeight * textScale) + 2,
                    applyOpacity(showBg ? 0xFF7ABAFF : 0xFFFFFFFF, opacity * 0.7f), false);
        }
    }

    private void renderTimerInteractive(DrawContext ctx, MinecraftClient mc,
                                         int screenW, int screenH, int mx, int my) {
        renderTimer(ctx, mc, screenW, screenH);
        if (timerMode == TimerScreen.Mode.STOPWATCH) return; // no interactive buttons for stopwatch HUD
        int[] pos = getTimerHudPos(screenW, screenH);
        int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
        TextRenderer tr = mc.textRenderer;

        boolean running  = (timerState == TimerScreen.State.ACTIVE || timerState == TimerScreen.State.PAUSED);
        boolean complete = (timerState == TimerScreen.State.COMPLETE);
        if (!running && !complete) return;

        int btnY = y + h + 2, btnH = 12, btnW = 44, gap = 4;
        int totalBW = btnW * 2 + gap;
        int cx = x + (w - totalBW) / 2;

        if (running) {
            boolean hC = mx >= cx && mx < cx + btnW && my >= btnY && my < btnY + btnH;
            ctx.fill(cx, btnY, cx + btnW, btnY + btnH, hC ? 0xFF8B2A28 : 0xFF6B1A18);
            String cLbl = "Cancel";
            ctx.drawText(tr, cLbl, cx + (btnW - tr.getWidth(cLbl)) / 2,
                    btnY + (btnH - tr.fontHeight) / 2, 0xFFE5E5E5, false);

            int px2 = cx + btnW + gap;
            boolean hP = mx >= px2 && mx < px2 + btnW && my >= btnY && my < btnY + btnH;
            ctx.fill(px2, btnY, px2 + btnW, btnY + btnH, hP ? 0xFF1E4070 : 0xFF1A3050);
            String pLbl = (timerState == TimerScreen.State.PAUSED) ? "Resume" : "Pause";
            ctx.drawText(tr, pLbl, px2 + (btnW - tr.getWidth(pLbl)) / 2,
                    btnY + (btnH - tr.fontHeight) / 2, 0xFFE5E5E5, false);
        } else {
            boolean hR = mx >= cx && mx < cx + btnW && my >= btnY && my < btnY + btnH;
            ctx.fill(cx, btnY, cx + btnW, btnY + btnH, hR ? 0xFF3A4060 : 0xFF2E3347);
            String rLbl = "Repeat";
            ctx.drawText(tr, rLbl, cx + (btnW - tr.getWidth(rLbl)) / 2,
                    btnY + (btnH - tr.fontHeight) / 2, 0xFFE5E5E5, false);

            int px2 = cx + btnW + gap;
            boolean hD = mx >= px2 && mx < px2 + btnW && my >= btnY && my < btnY + btnH;
            ctx.fill(px2, btnY, px2 + btnW, btnY + btnH, hD ? 0xFF3A4060 : 0xFF2E3347);
            String dLbl = "Dismiss";
            ctx.drawText(tr, dLbl, px2 + (btnW - tr.getWidth(dLbl)) / 2,
                    btnY + (btnH - tr.fontHeight) / 2, 0xFFE5E5E5, false);
        }

        // Resize corner indicator ⌟
        ctx.drawText(tr, "\u231F", x + w - 8, y + h - 9, 0xFF5C6380, false);
    }

    // =========================================================================
    // Notes HUD rendering
    // =========================================================================
    private int[] getNotesHudPos(int screenW, int screenH) {
        int x = IgCalcHudState.notesHudX;
        int y = IgCalcHudState.notesHudY;
        int w = IgCalcHudState.notesHudW;
        int h = IgCalcHudState.notesHudH;
        if (x < 0) x = 8;
        if (y < 0) y = 8;
        x = Math.max(0, Math.min(x, screenW - w));
        y = Math.max(0, Math.min(y, screenH - h));
        return new int[]{x, y, w, h};
    }

    /** Render the Notes HUD. Pass mx=-1, my=-1 for non-interactive. */
    private void renderNotes(DrawContext ctx, MinecraftClient mc,
                              int screenW, int screenH, int mx, int my) {
        int[] pos = getNotesHudPos(screenW, screenH);
        int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
        float opacity = IgCalcHudState.notesHudOpacity;
        boolean showBg = IgCalcHudState.notesHudShowBg;

        if (showBg) {
            int bgColor = applyOpacity(0xEE1C1E2B, opacity);
            int brColor = applyOpacity(0xFF3A3F52, opacity);
            fillRoundedRect(ctx, x, y, w, h, 4, bgColor);
            ctx.fill(x,         y,         x + w, y + 1,     brColor);
            ctx.fill(x,         y + h - 1, x + w, y + h,     brColor);
            ctx.fill(x,         y,         x + 1, y + h,     brColor);
            ctx.fill(x + w - 1, y,         x + w, y + h,     brColor);
        }

        TextRenderer tr = mc.textRenderer;

        // Top nav bar (search field on left, arrows on right)
        int topBarH  = 12;
        int topBarY  = y + 1;
        int navBaseCol = showBg ? 0xFF5C6380 : 0xFFFFFFFF;
        int navActCol  = showBg ? 0xFF0A84FF : 0xFFFFFFFF;

        if (showBg) ctx.fill(x + 1, topBarY, x + w - 1, topBarY + topBarH, applyOpacity(0xFF1A1C2A, opacity));

        // Search field (left side, takes most of the width)
        int arrowAreaW = 22;  // space for ◄ ► on the right
        int searchFieldX = x + 3;
        int searchFieldW = w - 6 - arrowAreaW;
        int searchFieldY = topBarY + 1;
        int searchFieldH = topBarH - 2;

        // Search field background
        int sfBg = notesSearchActive ? applyOpacity(0xFF262A3C, opacity) : applyOpacity(0xFF1A1C2A, opacity);
        ctx.fill(searchFieldX, searchFieldY, searchFieldX + searchFieldW, searchFieldY + searchFieldH, sfBg);
        ctx.fill(searchFieldX, searchFieldY, searchFieldX + searchFieldW, searchFieldY + 1, applyOpacity(0xFF3A3F52, opacity));
        ctx.fill(searchFieldX, searchFieldY + searchFieldH - 1, searchFieldX + searchFieldW, searchFieldY + searchFieldH, applyOpacity(0xFF3A3F52, opacity));
        ctx.fill(searchFieldX, searchFieldY, searchFieldX + 1, searchFieldY + searchFieldH, applyOpacity(0xFF3A3F52, opacity));
        ctx.fill(searchFieldX + searchFieldW - 1, searchFieldY, searchFieldX + searchFieldW, searchFieldY + searchFieldH, applyOpacity(0xFF3A3F52, opacity));

        // Search field text
        if (notesSearchActive) {
            String searchTxt = notesSearchQuery + (System.currentTimeMillis() % 1000 < 500 ? "_" : "");
            ctx.drawText(tr, tr.trimToWidth(searchTxt, searchFieldW - 6), searchFieldX + 3, searchFieldY + 1, applyOpacity(0xFFE5E5E5, opacity), false);
        } else {
            // Show current note name as placeholder
            String placeholder = "";
            if (notesCurrentFile != null) {
                placeholder = notesCurrentFile.contains("/")
                        ? notesCurrentFile.substring(notesCurrentFile.lastIndexOf('/') + 1)
                        : notesCurrentFile;
                if (placeholder.endsWith(".txt")) placeholder = placeholder.substring(0, placeholder.length() - 4);
            }
            boolean hoverSearch = (mx >= searchFieldX && mx < searchFieldX + searchFieldW
                    && my >= searchFieldY && my < searchFieldY + searchFieldH);
            int placeholderCol = hoverSearch ? applyOpacity(navActCol, opacity) : applyOpacity(navBaseCol, opacity);
            ctx.drawText(tr, tr.trimToWidth(placeholder, searchFieldW - 6), searchFieldX + 3, searchFieldY + 1, placeholderCol, false);
        }

        // [◄] prev note
        int arrowX = x + w - arrowAreaW;
        boolean hPrev = (mx >= arrowX && mx < arrowX + 10 && my >= topBarY && my < topBarY + topBarH);
        ctx.drawText(tr, "\u25C4", arrowX + 1, topBarY + 2,
                hPrev ? applyOpacity(navActCol, opacity) : applyOpacity(navBaseCol, opacity), false);

        // [►] next note
        boolean hNext = (mx >= arrowX + 11 && mx < arrowX + 21 && my >= topBarY && my < topBarY + topBarH);
        ctx.drawText(tr, "\u25BA", arrowX + 12, topBarY + 2,
                hNext ? applyOpacity(navActCol, opacity) : applyOpacity(navBaseCol, opacity), false);

        // Divider below top bar
        if (showBg) ctx.fill(x + 1, topBarY + topBarH, x + w - 1, topBarY + topBarH + 1, applyOpacity(0xFF3A3F52, opacity));

        // Content area — below top bar to bottom border
        int contentX      = x + 3;
        int contentW      = w - 6;
        int contentY      = topBarY + topBarH + 2;
        int contentBottom = y + h - 3;

        // Count total wrapped lines for scroll clamping
        int totalWrapped = 0;
        for (int li = 0; li < notesLines.size(); li++) {
            String raw = notesLines.get(li);
            float scale = getHudLineScale(raw);
            String display = getHudLinePrefix(raw);
            totalWrapped += simpleWrap(display, tr, (int)(contentW / scale)).size();
        }
        notesWrappedLineCount = totalWrapped;

        // Selection range (ordered)
        int selSL = -1, selSC = -1, selEL = -1, selEC = -1;
        if (notesEditingActive && hasSelection()) {
            int[] sr = getSelectionRange();
            selSL = sr[0]; selSC = sr[1]; selEL = sr[2]; selEC = sr[3];
        }

        int skip = notesScroll;
        int cursorDrawX = -1, cursorDrawY = -1, cursorDrawH = -1;
        for (int li = 0; li < notesLines.size() && contentY < contentBottom; li++) {
            String raw   = notesLines.get(li);
            float  scale = getHudLineScale(raw);
            int    lineH = (int) Math.ceil(tr.fontHeight * scale);
            String display = getHudLinePrefix(raw);
            List<String> wrapped = simpleWrap(display, tr, (int)(contentW / scale));

            // Compute display col for cursor/selection on this line
            int rawPL  = getHudRawPrefixLen(raw);
            int dispPL = getHudDisplayPrefixLen(raw);

            int segStart = 0; // character offset into display for current segment
            for (int si = 0; si < wrapped.size(); si++) {
                String seg = wrapped.get(si);
                int segEnd = segStart + seg.length();
                if (skip > 0) { skip--; segStart = segEnd; continue; }
                if (contentY + lineH > contentBottom) break;

                int lineColor = showBg ? applyOpacity(getHudLineColor(raw), opacity)
                                       : applyOpacity(0xFFFFFFFF, opacity);
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(contentX, contentY);
                ctx.getMatrices().scale(scale, scale);
                ctx.drawText(tr, seg, 0, 0, lineColor, false);
                ctx.getMatrices().popMatrix();

                // Selection highlight for this segment
                if (selSL >= 0) {
                    int selStartInDisplay = -1, selEndInDisplay = -1;
                    if (li > selSL && li < selEL) {
                        // Entire line selected
                        selStartInDisplay = 0;
                        selEndInDisplay = display.length();
                    } else if (li == selSL && li == selEL) {
                        selStartInDisplay = rawToDisplayCol(selSC, rawPL, dispPL, display.length());
                        selEndInDisplay   = rawToDisplayCol(selEC, rawPL, dispPL, display.length());
                    } else if (li == selSL) {
                        selStartInDisplay = rawToDisplayCol(selSC, rawPL, dispPL, display.length());
                        selEndInDisplay   = display.length();
                    } else if (li == selEL) {
                        selStartInDisplay = 0;
                        selEndInDisplay   = rawToDisplayCol(selEC, rawPL, dispPL, display.length());
                    }
                    if (selStartInDisplay >= 0 && selEndInDisplay >= 0) {
                        int hlStart = Math.max(segStart, selStartInDisplay) - segStart;
                        int hlEnd   = Math.min(segEnd,   selEndInDisplay)   - segStart;
                        if (hlEnd > hlStart) {
                            int hlX1 = contentX + (int)(tr.getWidth(seg.substring(0, hlStart)) * scale);
                            int hlX2 = contentX + (int)(tr.getWidth(seg.substring(0, hlEnd))   * scale);
                            ctx.fill(hlX1, contentY, hlX2, contentY + lineH, 0x663A5A90);
                        }
                    }
                }

                // Track cursor position — find which segment contains the cursor
                if (notesEditingActive && li == notesEditCursorLine) {
                    int col = Math.min(notesEditCursorCol, raw.length());
                    int displayCol = rawToDisplayCol(col, rawPL, dispPL, display.length());
                    // Cursor is in this segment if displayCol falls within [segStart, segEnd]
                    // (or on the last segment if beyond all)
                    if ((displayCol >= segStart && displayCol <= segEnd)
                     || (si == wrapped.size() - 1 && displayCol >= segStart)) {
                        int colInSeg = Math.min(displayCol - segStart, seg.length());
                        String beforeCursor = seg.substring(0, colInSeg);
                        cursorDrawX = contentX + (int)(tr.getWidth(beforeCursor) * scale);
                        cursorDrawY = contentY;
                        cursorDrawH = lineH;
                    }
                }
                contentY += lineH;
                segStart = segEnd;
            }
        }
        // Draw blinking cursor
        if (notesEditingActive && cursorDrawX >= 0 && System.currentTimeMillis() % 1000 < 500) {
            ctx.fill(cursorDrawX, cursorDrawY, cursorDrawX + 1, cursorDrawY + cursorDrawH, 0xFFFFFFFF);
        }

        // Search results dropdown (below top bar)
        if (notesSearchActive && !notesSearchResults.isEmpty()) {
            int sbH        = 13;
            int numResults = notesSearchResults.size();
            int dropdownY  = topBarY + topBarH + 1;
            int dropdownH  = numResults * sbH;

            ctx.fill(x, dropdownY, x + w, dropdownY + dropdownH, 0xFF0A0A0A);
            ctx.fill(x, dropdownY, x + w, dropdownY + 1, 0xFF3A3F52);
            ctx.fill(x, dropdownY, x + 1, dropdownY + dropdownH, 0xFF3A3F52);
            ctx.fill(x + w - 1, dropdownY, x + w, dropdownY + dropdownH, 0xFF3A3F52);
            ctx.fill(x, dropdownY + dropdownH - 1, x + w, dropdownY + dropdownH, 0xFF3A3F52);
            for (int i = 0; i < numResults; i++) {
                String result = notesSearchResults.get(i);
                int ry = dropdownY + i * sbH;
                boolean selected = (i == notesSearchSelectedIdx);
                if (selected) ctx.fill(x + 1, ry, x + w - 1, ry + sbH, 0x33E8C170);
                if (i < numResults - 1)
                    ctx.fill(x + 1, ry + sbH - 1, x + w - 1, ry + sbH, 0xFF2A2D3D);
                String dispName = result.contains("/") ? result.substring(result.lastIndexOf('/') + 1) : result;
                if (dispName.endsWith(".txt")) dispName = dispName.substring(0, dispName.length() - 4);
                int textColor = selected ? 0xFFE8C170 : 0xFFD0D0D0;
                ctx.drawText(tr, tr.trimToWidth(dispName, w - 8), x + 4, ry + 3, textColor, false);
            }
        }
    }

    private void renderNotesInteractive(DrawContext ctx, MinecraftClient mc,
                                         int screenW, int screenH, int mx, int my) {
        renderNotes(ctx, mc, screenW, screenH, mx, my);
        int[] pos = getNotesHudPos(screenW, screenH);
        int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
        TextRenderer tr = mc.textRenderer;

        // Hover highlight border
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            ctx.fill(x,         y,         x + w, y + 1,     0x880A84FF);
            ctx.fill(x,         y + h - 1, x + w, y + h,     0x880A84FF);
            ctx.fill(x,         y,         x + 1, y + h,     0x880A84FF);
            ctx.fill(x + w - 1, y,         x + w, y + h,     0x880A84FF);
        }

        // Resize corner indicator ⌟
        ctx.drawText(tr, "\u231F", x + w - 8, y + h - 9, 0xFF5C6380, false);

        // Hover highlights for search results dropdown
        if (notesSearchActive && !notesSearchResults.isEmpty()) {
            int sbH        = 13;
            int numResults = notesSearchResults.size();
            int topBarH    = 12;
            int dropdownY  = y + 1 + topBarH + 1;
            for (int i = 0; i < numResults; i++) {
                int ry = dropdownY + i * sbH;
                boolean hov = (mx >= x && mx < x + w && my >= ry && my < ry + sbH);
                if (hov && i != notesSearchSelectedIdx) {
                    ctx.fill(x + 1, ry, x + w - 1, ry + sbH, 0xFF1A1C2A);
                }
            }
        }
    }

    // =========================================================================
    // Interaction event routing
    // =========================================================================
    public boolean mouseClicked(int mx, int my, MinecraftClient mc) {
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // Corner-drag resize hit detection for Notes HUD
        if (pinnedNotes && notesVisible) {
            int[] pos = getNotesHudPos(screenW, screenH);
            int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
            if (mx >= x + w - RESIZE_CORNER && mx < x + w
             && my >= y + h - RESIZE_CORNER && my < y + h) {
                resizingNotes    = true;
                resizingTimer    = false;
                resizeDragStartX = mx;
                resizeDragStartY = my;
                resizeOrigW      = w;
                resizeOrigH      = h;
                return true;
            }
        }

        // Corner-drag resize hit detection for Timer HUD
        if (pinnedTimer && timerVisible) {
            int[] pos = getTimerHudPos(screenW, screenH);
            int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
            if (mx >= x + w - RESIZE_CORNER && mx < x + w
             && my >= y + h - RESIZE_CORNER && my < y + h) {
                resizingTimer    = true;
                resizingNotes    = false;
                resizeDragStartX = mx;
                resizeDragStartY = my;
                resizeOrigW      = w;
                resizeOrigH      = h;
                return true;
            }
        }

        // Timer buttons (countdown mode only)
        if (pinnedTimer && timerVisible && timerMode == TimerScreen.Mode.TIMER) {
            int[] pos = getTimerHudPos(screenW, screenH);
            int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
            boolean running  = (timerState == TimerScreen.State.ACTIVE || timerState == TimerScreen.State.PAUSED);
            boolean complete = (timerState == TimerScreen.State.COMPLETE);

            if (running || complete) {
                int btnY = y + h + 2, btnH = 12, btnW = 44, gap = 4;
                int totalBW = btnW * 2 + gap;
                int cx = x + (w - totalBW) / 2;
                if (my >= btnY && my < btnY + btnH) {
                    if (running) {
                        if (mx >= cx && mx < cx + btnW) { cancelTimer(); return true; }
                        if (mx >= cx + btnW + gap && mx < cx + totalBW) { pauseOrResumeTimer(); return true; }
                    } else {
                        if (mx >= cx && mx < cx + btnW) { repeatTimer(); return true; }
                        if (mx >= cx + btnW + gap && mx < cx + totalBW) {
                            timerState = TimerScreen.State.IDLE; return true;
                        }
                    }
                }
            }
        }

        // Notes nav/search buttons
        if (pinnedNotes && notesVisible) {
            int[] pos = getNotesHudPos(screenW, screenH);
            int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
            int topBarH  = 12;
            int topBarY  = y + 1;
            int arrowAreaW = 22;
            int searchFieldX = x + 3;
            int searchFieldW = w - 6 - arrowAreaW;
            int searchFieldY = topBarY + 1;
            int searchFieldH = topBarH - 2;
            int arrowX = x + w - arrowAreaW;

            // Content area interaction (checkbox toggle, double-click to edit)
            int contentX      = x + 3;
            int contentW      = w - 6;
            int contentY      = topBarY + topBarH + 2;
            int contentBottom = y + h - 3;
            if (mx >= contentX && mx < contentX + contentW
             && my >= contentY && my < contentBottom) {
                // Dismiss search when clicking content area
                if (notesSearchActive) {
                    notesSearchActive = false;
                    notesSearchQuery = "";
                    notesSearchResults.clear();
                }
                long now = System.currentTimeMillis();
                boolean doubleClick = (now - lastClickMs) < 400;
                lastClickMs = now;

                MinecraftClient mcInner = MinecraftClient.getInstance();
                if (mcInner != null) {
                    net.minecraft.client.font.TextRenderer trInner = mcInner.textRenderer;
                    int skip = notesScroll;
                    int curY = contentY;
                    for (int li = 0; li < notesLines.size() && curY < contentBottom; li++) {
                        String raw   = notesLines.get(li);
                        float  scale = getHudLineScale(raw);
                        int    lineH = (int) Math.ceil(trInner.fontHeight * scale);
                        String display = getHudLinePrefix(raw);
                        List<String> wrapped = simpleWrap(display, trInner, (int)(contentW / scale));
                        for (String seg : wrapped) {
                            if (skip > 0) { skip--; continue; }
                            if (curY + lineH > contentBottom) break;
                            if (my >= curY && my < curY + lineH) {
                                if (doubleClick) {
                                    // Enter edit mode at clicked line
                                    notesEditingActive  = true;
                                    notesEditCursorLine = li;
                                    notesEditCursorCol  = raw.length();
                                    clearSelection();
                                    return true;
                                }
                                // Single click — toggle checkbox
                                if (raw.startsWith("- [ ] ")) {
                                    notesLines.set(li, "- [x] " + raw.substring(6));
                                    saveEditedNote(); return true;
                                } else if (raw.startsWith("- [x] ")) {
                                    notesLines.set(li, "- [ ] " + raw.substring(6));
                                    saveEditedNote(); return true;
                                }
                            }
                            curY += lineH;
                        }
                    }
                }
            }

            // Search field click — toggle search
            if (mx >= searchFieldX && mx < searchFieldX + searchFieldW
             && my >= searchFieldY && my < searchFieldY + searchFieldH) {
                notesSearchActive = !notesSearchActive;
                if (!notesSearchActive) {
                    notesSearchQuery = "";
                    notesSearchResults.clear();
                } else if (!notesSearchQuery.isEmpty()) {
                    updateSearchResults();
                }
                return true;
            }
            // [◄] prev note
            if (mx >= arrowX && mx < arrowX + 10 && my >= topBarY && my < topBarY + topBarH) {
                prevNote(); return true;
            }
            // [►] next note
            if (mx >= arrowX + 11 && mx < arrowX + 21 && my >= topBarY && my < topBarY + topBarH) {
                nextNote(); return true;
            }
            // Search results dropdown
            if (notesSearchActive && !notesSearchResults.isEmpty()) {
                int sbH = 13;
                int dropdownY = topBarY + topBarH + 1;
                for (int ri = 0; ri < notesSearchResults.size(); ri++) {
                    int ry = dropdownY + ri * sbH;
                    if (mx >= x && mx < x + w && my >= ry && my < ry + sbH) {
                        notesSearchSelectedIdx = ri;
                        selectSearchResult(notesSearchResults.get(ri)); return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean mouseDragged(int mx, int my) {
        if (notesSearchActive) return false; // block movement/resize while searching
        if (resizingNotes) {
            IgCalcHudState.notesHudW = Math.max(140, resizeOrigW + (mx - resizeDragStartX));
            IgCalcHudState.notesHudH = Math.max(80,  resizeOrigH + (my - resizeDragStartY));
            return true;
        }
        if (resizingTimer) {
            IgCalcHudState.timerHudW = Math.max(110, resizeOrigW + (mx - resizeDragStartX));
            IgCalcHudState.timerHudH = Math.max(32,  resizeOrigH + (my - resizeDragStartY));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mx, int my) {
        if (resizingNotes || resizingTimer) {
            IgCalcHudState.save();
            resizingNotes = false;
            resizingTimer = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double vAmount, MinecraftClient mc) {
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        if (pinnedNotes && notesVisible) {
            int[] pos = getNotesHudPos(screenW, screenH);
            int x = pos[0], y = pos[1], w = pos[2], h = pos[3];
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                int delta = (int)(-vAmount);
                notesScroll = Math.max(0, Math.min(Math.max(0, notesWrappedLineCount - 1), notesScroll + delta));
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Markdown helpers (adapted from NotesScreen)
    // =========================================================================
    static int getHudLineColor(String line) {
        if (line.startsWith("```"))   return 0xFF3A3F52;
        if (line.startsWith("#### ")) return 0xFFB48EAD;
        if (line.startsWith("### "))  return 0xFF6EB5D4;
        if (line.startsWith("## "))   return 0xFF7EC8A0;
        if (line.startsWith("# "))    return 0xFFE8C170;
        if (line.startsWith("> "))    return 0xFF6B7599;
        if (line.startsWith("- [x]") || line.startsWith("  - [x]")) return 0xFF666666;
        if (line.startsWith("* ")  || line.startsWith("- ")
         || line.startsWith("  * ")|| line.startsWith("  - ")) return 0xFFAAAAAA;
        if (line.startsWith("---"))   return 0xFFFFFFFF;
        return 0xFFD0D0D0;
    }

    static float getHudLineScale(String line) {
        if (line.startsWith("#### ")) return 1.25f;
        if (line.startsWith("### "))  return 1.5f;
        if (line.startsWith("## "))   return 1.75f;
        if (line.startsWith("# "))    return 2.0f;
        return 1.0f;
    }

    static String getHudLinePrefix(String line) {
        if (line.startsWith("#### ")) return line.substring(5);
        if (line.startsWith("### "))  return line.substring(4);
        if (line.startsWith("## "))   return line.substring(3);
        if (line.startsWith("# "))    return line.substring(2);
        if (line.startsWith("  - [ ] ")) return "    \u25AA " + line.substring(8);
        if (line.startsWith("  - [x] ")) return "    \u2713 "  + line.substring(8);
        if (line.startsWith("  - "))    return "    \u25AA "   + line.substring(4);
        if (line.startsWith("  * "))    return "    \u25AA "   + line.substring(4);
        if (line.startsWith("- [ ] ")) return "  \u2610 " + line.substring(6);
        if (line.startsWith("- [x] ")) return "  \u2713 "  + line.substring(6);
        if (line.startsWith("* "))    return "  \u2022 "   + line.substring(2);
        if (line.startsWith("- "))    return "  \u2022 "   + line.substring(2);
        if (line.startsWith("> "))    return "\u2502 "      + line.substring(2);
        return line;
    }

    private static int getHudRawPrefixLen(String line) {
        if (line.startsWith("#### "))    return 5;
        if (line.startsWith("### "))     return 4;
        if (line.startsWith("## "))      return 3;
        if (line.startsWith("# "))       return 2;
        if (line.startsWith("  - [ ] ")) return 8;
        if (line.startsWith("  - [x] ")) return 8;
        if (line.startsWith("  - "))     return 4;
        if (line.startsWith("  * "))     return 4;
        if (line.startsWith("- [ ] "))   return 6;
        if (line.startsWith("- [x] "))   return 6;
        if (line.startsWith("* "))       return 2;
        if (line.startsWith("- "))       return 2;
        if (line.startsWith("> "))       return 2;
        return 0;
    }

    private static int getHudDisplayPrefixLen(String line) {
        if (line.startsWith("#### "))    return 0;
        if (line.startsWith("### "))     return 0;
        if (line.startsWith("## "))      return 0;
        if (line.startsWith("# "))       return 0;
        if (line.startsWith("  - [ ] ")) return 6;  // "    ▪ "
        if (line.startsWith("  - [x] ")) return 6;  // "    ✓ "
        if (line.startsWith("  - "))     return 6;  // "    ▪ "
        if (line.startsWith("  * "))     return 6;  // "    ▪ "
        if (line.startsWith("- [ ] "))   return 4;  // "  ☐ "
        if (line.startsWith("- [x] "))   return 4;  // "  ✓ "
        if (line.startsWith("* "))       return 4;  // "  • "
        if (line.startsWith("- "))       return 4;  // "  • "
        if (line.startsWith("> "))       return 2;  // "│ "
        return 0;
    }

    private static int rawToDisplayCol(int rawCol, int rawPL, int dispPL, int displayLen) {
        int displayCol;
        if (rawCol <= rawPL) {
            displayCol = (rawPL > 0) ? (int)((float) rawCol / rawPL * dispPL) : 0;
        } else {
            displayCol = dispPL + (rawCol - rawPL);
        }
        return Math.min(displayCol, displayLen);
    }

    private static List<String> simpleWrap(String text, TextRenderer tr, int maxW) {
        List<String> result = new ArrayList<>();
        if (text.isEmpty()) { result.add(""); return result; }
        if (maxW <= 0 || tr.getWidth(text) <= maxW) { result.add(text); return result; }
        int start = 0;
        while (start < text.length()) {
            int end = start;
            for (int c = start + 1; c <= text.length(); c++) {
                if (tr.getWidth(text.substring(start, c)) > maxW) break;
                end = c;
            }
            if (end <= start) end = start + 1;
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end - 1);
                if (lastSpace > start) end = lastSpace + 1;
            }
            result.add(text.substring(start, Math.min(end, text.length())));
            start = end;
        }
        return result;
    }

    // =========================================================================
    // Rendering helpers
    // =========================================================================
    private static int applyOpacity(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, (int)(((color >>> 24) & 0xFF) * opacity)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

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
}
