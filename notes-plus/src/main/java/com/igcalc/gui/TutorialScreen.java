package com.igcalc.gui;

import com.igcalc.config.IgCalcTutorialState;
import com.igcalc.config.IgCalcWindowState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A guided first-run tutorial walkthrough that showcases every major feature
 * of the notes+ mod. Renders a dim overlay with a spotlight cutout around
 * the highlighted UI element and a tooltip with description + navigation.
 *
 * Not a Screen — rendered on top of IgCalcOverlay which owns the lifecycle.
 */
public class TutorialScreen {

    // Tooltip style
    private static final int COLOR_DIM         = 0xAA000000;
    private static final int COLOR_TOOLTIP_BG  = 0xF0262A3C;
    private static final int COLOR_TOOLTIP_BD  = 0xFF3A3F52;
    private static final int COLOR_GLOW        = 0xFF0A84FF;
    private static final int COLOR_TEXT         = 0xFFE5E5E5;
    private static final int COLOR_TEXT_DIM     = 0xFF6B7099;
    private static final int COLOR_TEXT_ACCENT  = 0xFF7EB8FF;
    private static final int COLOR_BTN_NEXT     = 0xFF0A84FF;
    private static final int COLOR_BTN_NEXT_HOV = 0xFF3A9FFF;
    private static final int COLOR_BTN_BACK     = 0xFF3A3F52;
    private static final int COLOR_BTN_BACK_HOV = 0xFF4A5068;

    private static final int TOOLTIP_MAX_W = 260;
    private static final int TOOLTIP_PAD   = 10;
    private static final int BTN_H         = 16;

    // State
    private int currentStep = 0;
    private boolean active  = true;
    private final IgCalcOverlay overlay;
    private int width, height;
    private TextRenderer textRenderer;

    // Filtered step list (null = show all steps)
    private int[] filteredSteps = null;
    private int filteredIndex = 0;

    // Hover state for buttons
    private boolean hoverNext = false;
    private boolean hoverBack = false;
    private boolean hoverSkip = false;

    // Button hit areas (computed each frame)
    private int nextBtnX, nextBtnY, nextBtnW, nextBtnH;
    private int backBtnX, backBtnY, backBtnW, backBtnH;
    private int skipBtnX, skipBtnY, skipBtnW, skipBtnH;

    // -------------------------------------------------------------------------
    // Step data
    // -------------------------------------------------------------------------

    private enum Anchor { BELOW, ABOVE, RIGHT, LEFT, CENTER }

    private record TutorialStep(
        String title,
        String text,
        boolean showNotes,
        boolean showCalc,
        boolean showTimer,
        boolean stopwatchMode,
        Anchor anchor
    ) {}

    private static final TutorialStep[] STEPS = {
        // 0: Welcome
        new TutorialStep(
            "Welcome to Notes+!",
            "A toolkit for notes, calculations, and timers \u2014 all in-game. Let's take a quick tour.",
            false, false, false, false, Anchor.CENTER
        ),
        // 1: Notes
        new TutorialStep(
            "Notes (F7)",
            "Press **F7** to open Notes. Write and organize notes that save automatically per-world.",
            true, false, false, false, Anchor.RIGHT
        ),
        // 2: Sidebar
        new TutorialStep(
            "Sidebar",
            "Toggle the sidebar with **[\u2261]** to browse, search, and organize your notes into folders.",
            true, false, false, false, Anchor.RIGHT
        ),
        // 3: Markdown
        new TutorialStep(
            "Markdown",
            "Notes support markdown: **# headers**, **- bullets**, **- [ ] checkboxes**, `code`, **bold**, and more.",
            true, false, false, false, Anchor.RIGHT
        ),
        // 4: Note linking
        new TutorialStep(
            "Note Linking",
            "Type **[[Note Name]]** to link between notes. Click a link to jump to that note.",
            true, false, false, false, Anchor.RIGHT
        ),
        // 5: Export
        new TutorialStep(
            "Export",
            "Click the **[\u2197]** export button to copy your note to clipboard, export to desktop, or open the notes folder.",
            true, false, false, false, Anchor.RIGHT
        ),
        // 6: Calculator
        new TutorialStep(
            "Calculator (F6)",
            "Press **F6** for an in-game calculator. Supports +, \u2212, \u00d7, \u00f7, parentheses, and keyboard input.",
            false, true, false, false, Anchor.RIGHT
        ),
        // 7: Calc→Notes
        new TutorialStep(
            "Calc to Notes",
            "Click the **\u2192N** button to send your calculation result directly into your current note.",
            false, true, false, false, Anchor.RIGHT
        ),
        // 8: Timer
        new TutorialStep(
            "Timer (F8)",
            "Press **F8** for a timer and stopwatch. Switch modes with the tabs at the top.",
            false, false, true, false, Anchor.RIGHT
        ),
        // 9: Lap times (stopwatch mode)
        new TutorialStep(
            "Lap Times",
            "In stopwatch mode, click **Lap** to record split times as you go.",
            false, false, true, true, Anchor.RIGHT
        ),
        // 10: HUD pinning
        new TutorialStep(
            "HUD Pinning",
            "Click the **pin \u2691** button to keep notes or timer on your HUD. Toggle visibility with **F9**. Hold **Alt** to get a cursor and interact with pinned notes \u2014 scroll, switch files with **Ctrl+Left/Right**, and search with **Ctrl+Enter**.",
            true, false, false, false, Anchor.RIGHT
        ),
        // 11: Shortcuts
        new TutorialStep(
            "Quick Shortcuts",
            "**Ctrl+Shift+N** (quick note), **Ctrl+Shift+G** (insert coords), **Ctrl+Shift+E** (copy to clipboard). Customize all keybinds in Mod Menu \u203a Notes+ \u203a Settings.",
            false, false, false, false, Anchor.CENTER
        ),
    };

    private static final int TOTAL_STEPS = STEPS.length;

    // Step ranges for per-window help buttons
    public static final int[] NOTES_STEPS = {1, 2, 3, 4, 5, 10};
    public static final int[] CALC_STEPS  = {6, 7};
    public static final int[] TIMER_STEPS = {8, 9, 10};

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public TutorialScreen(IgCalcOverlay overlay) {
        this(overlay, 0, null);
    }

    public TutorialScreen(IgCalcOverlay overlay, int startStep) {
        this(overlay, startStep, null);
    }

    public TutorialScreen(IgCalcOverlay overlay, int startStep, int[] filter) {
        this.overlay = overlay;
        this.filteredSteps = filter;
        if (filter != null) {
            this.filteredIndex = 0;
            this.currentStep = filter[0];
        } else {
            this.currentStep = Math.max(0, Math.min(startStep, TOTAL_STEPS - 1));
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void initTutorial(int screenWidth, int screenHeight) {
        this.width = screenWidth;
        this.height = screenHeight;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
        applyStepWindows();
    }

    private void applyStepWindows() {
        TutorialStep step = STEPS[currentStep];
        boolean wasNotes = overlay.showNotes;
        boolean wasCalc  = overlay.showCalc;
        boolean wasTimer = overlay.showTimer;
        overlay.showNotes = step.showNotes;
        overlay.showCalc  = step.showCalc;
        overlay.showTimer = step.showTimer;

        if (step.showNotes && !wasNotes) overlay.enableNotes();
        if (step.showCalc  && !wasCalc)  overlay.enableCalc();
        if (step.showTimer && !wasTimer) overlay.enableTimer();

        // Switch timer to stopwatch mode if needed
        if (step.showTimer && step.stopwatchMode) {
            overlay.timerScreen.currentMode = TimerScreen.Mode.STOPWATCH;
        }
    }

    private int getDisplayIndex() {
        if (filteredSteps != null) return filteredIndex;
        return currentStep;
    }

    private int getDisplayTotal() {
        if (filteredSteps != null) return filteredSteps.length;
        return TOTAL_STEPS;
    }

    private boolean isFirstStep() {
        if (filteredSteps != null) return filteredIndex == 0;
        return currentStep == 0;
    }

    private boolean isLastStep() {
        if (filteredSteps != null) return filteredIndex >= filteredSteps.length - 1;
        return currentStep >= TOTAL_STEPS - 1;
    }

    private void advanceStep() {
        if (filteredSteps != null) {
            if (filteredIndex < filteredSteps.length - 1) {
                filteredIndex++;
                currentStep = filteredSteps[filteredIndex];
                applyStepWindows();
            } else {
                completeTutorial();
            }
        } else {
            if (currentStep < TOTAL_STEPS - 1) {
                currentStep++;
                applyStepWindows();
            } else {
                completeTutorial();
            }
        }
    }

    private void retreatStep() {
        if (filteredSteps != null) {
            if (filteredIndex > 0) {
                filteredIndex--;
                currentStep = filteredSteps[filteredIndex];
                applyStepWindows();
            }
        } else {
            if (currentStep > 0) {
                currentStep--;
                applyStepWindows();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Highlight region computation
    // -------------------------------------------------------------------------

    /** Returns {x, y, w, h} for the region to spotlight, or null for centered card. */
    private int[] getHighlightRect() {
        switch (currentStep) {
            case 0: return null; // Welcome card
            case 1: // Notes window
                return new int[]{
                    IgCalcWindowState.notesX, IgCalcWindowState.notesY,
                    IgCalcWindowState.notesW, IgCalcWindowState.notesH
                };
            case 2: // Sidebar area
                return new int[]{
                    IgCalcWindowState.notesX, IgCalcWindowState.notesY + 18,
                    105 + 5, IgCalcWindowState.notesH - 18
                };
            case 3: // Editor area
            {
                int sbW = IgCalcWindowState.notesSidebar ? 105 : 0;
                return new int[]{
                    IgCalcWindowState.notesX + sbW, IgCalcWindowState.notesY + 18,
                    IgCalcWindowState.notesW - sbW, IgCalcWindowState.notesH - 18
                };
            }
            case 4: // Note linking — same as editor
            {
                int sbW = IgCalcWindowState.notesSidebar ? 105 : 0;
                return new int[]{
                    IgCalcWindowState.notesX + sbW, IgCalcWindowState.notesY + 18,
                    IgCalcWindowState.notesW - sbW, IgCalcWindowState.notesH - 18
                };
            }
            case 5: // Export button area (TB_W=14, pinX=winW-18, exportX=pinX-2-14=winW-34)
                return new int[]{
                    IgCalcWindowState.notesX + IgCalcWindowState.notesW - 34,
                    IgCalcWindowState.notesY + 3,
                    14, 12
                };
            case 6: // Calculator window
                return new int[]{
                    IgCalcWindowState.calcX, IgCalcWindowState.calcY,
                    160, 256
                };
            case 7: // →N button on calculator (rightmost, at winW-18)
                return new int[]{
                    IgCalcWindowState.calcX + 160 - 18, IgCalcWindowState.calcY + 3,
                    14, 12
                };
            case 8: // Timer window
                return new int[]{
                    IgCalcWindowState.timerX, IgCalcWindowState.timerY,
                    280, 216
                };
            case 9: // Timer window (stopwatch — same region)
                return new int[]{
                    IgCalcWindowState.timerX, IgCalcWindowState.timerY,
                    280, 216
                };
            case 10: // Pin button on notes (pinX = winW - 18, TB_W=14, TB_H=12)
                return new int[]{
                    IgCalcWindowState.notesX + IgCalcWindowState.notesW - 18,
                    IgCalcWindowState.notesY + 3,
                    14, 12
                };
            case 11: return null; // Summary card
            default: return null;
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!active || textRenderer == null) return;

        TutorialStep step = STEPS[currentStep];
        int[] hl = getHighlightRect();

        // 1. Dim overlay with spotlight cutout
        if (hl != null) {
            int hx = Math.max(0, hl[0]);
            int hy = Math.max(0, hl[1]);
            int hw = hl[2];
            int hh = hl[3];
            int hx2 = hx + hw;
            int hy2 = hy + hh;

            ctx.fill(0, 0, width, hy, COLOR_DIM);
            ctx.fill(0, hy, hx, hy2, COLOR_DIM);
            ctx.fill(hx2, hy, width, hy2, COLOR_DIM);
            ctx.fill(0, hy2, width, height, COLOR_DIM);

            // Glow border
            for (int i = 3; i >= 1; i--) {
                int alpha = 0x15 * (4 - i);
                int glowColor = (alpha << 24) | (COLOR_GLOW & 0x00FFFFFF);
                ctx.fill(hx - i, hy - i, hx2 + i, hy - i + 1, glowColor);
                ctx.fill(hx - i, hy2 + i - 1, hx2 + i, hy2 + i, glowColor);
                ctx.fill(hx - i, hy - i, hx - i + 1, hy2 + i, glowColor);
                ctx.fill(hx2 + i - 1, hy - i, hx2 + i, hy2 + i, glowColor);
            }
            ctx.fill(hx - 1, hy - 1, hx2 + 1, hy, COLOR_GLOW);
            ctx.fill(hx - 1, hy2, hx2 + 1, hy2 + 1, COLOR_GLOW);
            ctx.fill(hx - 1, hy - 1, hx, hy2 + 1, COLOR_GLOW);
            ctx.fill(hx2, hy - 1, hx2 + 1, hy2 + 1, COLOR_GLOW);
        } else {
            ctx.fill(0, 0, width, height, COLOR_DIM);
        }

        // 2. Compute tooltip content
        int tooltipW = TOOLTIP_MAX_W;
        int innerW = tooltipW - TOOLTIP_PAD * 2;
        List<String> wrappedTitle = wrapText(step.title, innerW);
        List<String> wrappedText  = wrapText(step.text, innerW);
        int lineH = textRenderer.fontHeight + 2;

        // Layout: title + gap + body + gap + [Back] [Next] row + gap + step counter + skip
        int titleH = wrappedTitle.size() * (lineH + 1);
        int bodyH  = wrappedText.size() * lineH;
        int contentH = titleH + 4 + bodyH + 10 + BTN_H + 6 + lineH;
        int tooltipH = contentH + TOOLTIP_PAD * 2;

        // 3. Position tooltip
        int tx, ty;
        if (hl == null || step.anchor == Anchor.CENTER) {
            tx = (width - tooltipW) / 2;
            ty = (height - tooltipH) / 2;
        } else {
            int hx = Math.max(0, hl[0]), hy = Math.max(0, hl[1]), hw = hl[2], hh = hl[3];
            switch (step.anchor) {
                case BELOW:
                    tx = hx + (hw - tooltipW) / 2; ty = hy + hh + 8; break;
                case ABOVE:
                    tx = hx + (hw - tooltipW) / 2; ty = hy - tooltipH - 8; break;
                case LEFT:
                    tx = hx - tooltipW - 8; ty = hy + (hh - tooltipH) / 2; break;
                case RIGHT: default:
                    tx = hx + hw + 8; ty = hy + (hh - tooltipH) / 2; break;
            }
            tx = Math.max(4, Math.min(tx, width - tooltipW - 4));
            ty = Math.max(4, Math.min(ty, height - tooltipH - 4));
        }

        // 4. Draw tooltip box
        drawTooltipBox(ctx, tx, ty, tooltipW, tooltipH);

        // 5. Draw content
        int cx = tx + TOOLTIP_PAD;
        int cy = ty + TOOLTIP_PAD;

        // Title
        for (String line : wrappedTitle) {
            ctx.drawText(textRenderer, line, cx, cy, COLOR_TEXT_ACCENT, false);
            cy += lineH + 1;
        }
        cy += 4;

        // Body text
        for (String line : wrappedText) {
            drawFormattedLine(ctx, line, cx, cy);
            cy += lineH;
        }
        cy += 10;

        // Button row: [Back] ... [Next/Done]
        int rightEdge = tx + tooltipW - TOOLTIP_PAD;
        int btnY = cy;

        // "Next →" / "Done"
        String nextLabel = isLastStep() ? "Done" : "Next \u2192";
        nextBtnW = textRenderer.getWidth(nextLabel) + 12;
        nextBtnH = BTN_H;
        nextBtnX = rightEdge - nextBtnW;
        nextBtnY = btnY;
        hoverNext = mouseX >= nextBtnX && mouseX <= nextBtnX + nextBtnW
                 && mouseY >= nextBtnY && mouseY <= nextBtnY + nextBtnH;
        fillRoundedRect(ctx, nextBtnX, nextBtnY, nextBtnW, nextBtnH, 3,
                hoverNext ? COLOR_BTN_NEXT_HOV : COLOR_BTN_NEXT);
        ctx.drawText(textRenderer, nextLabel,
                nextBtnX + (nextBtnW - textRenderer.getWidth(nextLabel)) / 2,
                nextBtnY + (nextBtnH - textRenderer.fontHeight) / 2,
                0xFFFFFFFF, false);

        // "← Back" (hidden on first step)
        if (!isFirstStep()) {
            String backLabel = "\u2190 Back";
            backBtnW = textRenderer.getWidth(backLabel) + 12;
            backBtnH = BTN_H;
            backBtnX = nextBtnX - backBtnW - 6;
            backBtnY = btnY;
            hoverBack = mouseX >= backBtnX && mouseX <= backBtnX + backBtnW
                     && mouseY >= backBtnY && mouseY <= backBtnY + backBtnH;
            fillRoundedRect(ctx, backBtnX, backBtnY, backBtnW, backBtnH, 3,
                    hoverBack ? COLOR_BTN_BACK_HOV : COLOR_BTN_BACK);
            ctx.drawText(textRenderer, backLabel,
                    backBtnX + (backBtnW - textRenderer.getWidth(backLabel)) / 2,
                    backBtnY + (backBtnH - textRenderer.fontHeight) / 2,
                    COLOR_TEXT, false);
        } else {
            backBtnX = backBtnY = backBtnW = backBtnH = 0;
        }

        cy += BTN_H + 6;

        // Bottom row: step counter left, "Skip tour" right
        String stepStr = "Step " + (getDisplayIndex() + 1) + " of " + getDisplayTotal();
        ctx.drawText(textRenderer, stepStr, cx, cy, COLOR_TEXT_DIM, false);

        String skipLabel = "Skip tour";
        skipBtnW = textRenderer.getWidth(skipLabel);
        skipBtnH = textRenderer.fontHeight;
        skipBtnX = rightEdge - skipBtnW;
        skipBtnY = cy;
        hoverSkip = mouseX >= skipBtnX && mouseX <= skipBtnX + skipBtnW
                 && mouseY >= skipBtnY && mouseY <= skipBtnY + skipBtnH;
        ctx.drawText(textRenderer, skipLabel, skipBtnX, skipBtnY,
                hoverSkip ? COLOR_TEXT : COLOR_TEXT_DIM, false);
    }

    // -------------------------------------------------------------------------
    // Text wrapping
    // -------------------------------------------------------------------------

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() > 0) {
                String test = current + " " + word;
                if (textRenderer.getWidth(stripFormatting(test)) > maxWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(" ").append(word);
                }
            } else {
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private String stripFormatting(String text) {
        return text.replace("**", "");
    }

    // -------------------------------------------------------------------------
    // Formatted text drawing (handles **bold** as accent color)
    // -------------------------------------------------------------------------

    private void drawFormattedLine(DrawContext ctx, String line, int x, int y) {
        int cx = x;
        int i = 0;
        while (i < line.length()) {
            int boldStart = line.indexOf("**", i);
            if (boldStart == -1) {
                ctx.drawText(textRenderer, line.substring(i), cx, y, COLOR_TEXT, false);
                break;
            }
            if (boldStart > i) {
                String before = line.substring(i, boldStart);
                ctx.drawText(textRenderer, before, cx, y, COLOR_TEXT, false);
                cx += textRenderer.getWidth(before);
            }
            int boldEnd = line.indexOf("**", boldStart + 2);
            if (boldEnd == -1) {
                ctx.drawText(textRenderer, line.substring(boldStart), cx, y, COLOR_TEXT, false);
                break;
            }
            String boldText = line.substring(boldStart + 2, boldEnd);
            ctx.drawText(textRenderer, boldText, cx, y, COLOR_TEXT_ACCENT, false);
            cx += textRenderer.getWidth(boldText);
            i = boldEnd + 2;
        }
    }

    // -------------------------------------------------------------------------
    // Drawing helpers
    // -------------------------------------------------------------------------

    private void drawTooltipBox(DrawContext ctx, int x, int y, int w, int h) {
        fillRoundedRect(ctx, x, y, w, h, 4, COLOR_TOOLTIP_BG);
        ctx.fill(x, y, x + w, y + 1, COLOR_TOOLTIP_BD);
        ctx.fill(x, y + h - 1, x + w, y + h, COLOR_TOOLTIP_BD);
        ctx.fill(x, y, x + 1, y + h, COLOR_TOOLTIP_BD);
        ctx.fill(x + w - 1, y, x + w, y + h, COLOR_TOOLTIP_BD);
    }

    private static void fillRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        ctx.fill(x + 1, y + 1, x + r, y + r, color);
        ctx.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        ctx.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    public boolean handleMouseClicked(int mx, int my) {
        if (!active) return false;

        // Next / Done
        if (mx >= nextBtnX && mx <= nextBtnX + nextBtnW
         && my >= nextBtnY && my <= nextBtnY + nextBtnH) {
            advanceStep();
            return true;
        }

        // Back
        if (!isFirstStep()
         && mx >= backBtnX && mx <= backBtnX + backBtnW
         && my >= backBtnY && my <= backBtnY + backBtnH) {
            retreatStep();
            return true;
        }

        // Skip
        if (mx >= skipBtnX && mx <= skipBtnX + skipBtnW
         && my >= skipBtnY && my <= skipBtnY + skipBtnH + 2) {
            completeTutorial();
            return true;
        }

        return true; // Consume all clicks
    }

    public boolean handleKeyPressed(int key) {
        if (!active) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            completeTutorial();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_ENTER) {
            advanceStep();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT && !isFirstStep()) {
            retreatStep();
            return true;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Completion
    // -------------------------------------------------------------------------

    private void completeTutorial() {
        active = false;
        IgCalcTutorialState.markCompleted();
        if (overlay.showNotes) overlay.notesScreen.save();
        overlay.showCalc  = false;
        overlay.showNotes = false;
        overlay.showTimer = false;
        MinecraftClient.getInstance().setScreen(null);
    }

    public boolean isActive() { return active; }

    public void setStep(int step) {
        this.currentStep = Math.max(0, Math.min(step, TOTAL_STEPS - 1));
        this.active = true;
        applyStepWindows();
    }
}
