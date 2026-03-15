// FILE: src/main/java/com/igcalc/gui/CalculatorScreen.java
package com.igcalc.gui;

import com.igcalc.config.IgCalcWindowState;
import com.igcalc.gui.widget.CalculatorButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CalculatorScreen extends Screen {

    // -------------------------------------------------------------------------
    // Window geometry (fixed size, position is persisted)
    // -------------------------------------------------------------------------
    private static final int WIN_WIDTH        = 160;
    private static final int WIN_HEIGHT       = 256;
    private static final int TITLE_BAR_HEIGHT = 18;
    private static final int DISPLAY_H        = 80;   // history + current input panel
    private static final int HIST_LINE_H      = 11;   // pixels per history entry line

    // -------------------------------------------------------------------------
    // macOS terminal colour palette — dark navy (matches NotesScreen)
    // -------------------------------------------------------------------------
    // Window
    private static final int COLOR_BG           = 0xEE1C1E2B;
    private static final int COLOR_TITLE_BAR    = 0xFF262A3C;
    private static final int COLOR_TITLE_TEXT   = 0xFFE5E5E5;
    private static final int COLOR_BORDER       = 0xFF3A3F52;

    // Close dot
    private static final int COLOR_DOT_RED      = 0xFFFF5F57;

    // Display panel
    private static final int COLOR_DISPLAY_BG   = 0xFF13151F;
    private static final int COLOR_DISPLAY_TEXT = 0xFFD0D0D0;
    private static final int COLOR_DISPLAY_EXPR = 0xFF6B7099;

    // Number buttons
    private static final int COLOR_BTN_NUMBER       = 0xFF252A3E;
    private static final int COLOR_BTN_NUMBER_HOVER = 0xFF303548;
    private static final int COLOR_BTN_NUMBER_TEXT  = 0xFFD0D0D0;

    // Operator / parenthesis buttons
    private static final int COLOR_BTN_OP            = 0xFF1E2235;
    private static final int COLOR_BTN_OP_HOVER      = 0xFF282D42;
    private static final int COLOR_BTN_OP_TEXT       = 0xFFAAAAAA;

    // Display: result colours
    private static final int COLOR_DISPLAY_RESULT    = 0xFF33FF00; // bright green for current result
    private static final int COLOR_HIST_RESULT       = 0xFF2A7A40; // muted green for history results
    private static final int COLOR_HIST_SEP          = 0xFF1E2438; // subtle separator line

    // Equals button — blue accent (matches NotesScreen COLOR_STATUS)
    private static final int COLOR_BTN_EQUALS        = 0xFF0A84FF;
    private static final int COLOR_BTN_EQUALS_HOVER  = 0xFF2A9AFF;
    private static final int COLOR_BTN_EQUALS_TEXT   = 0xFFFFFFFF;

    // Clear button
    private static final int COLOR_BTN_CLEAR         = 0xFF3A3F52;
    private static final int COLOR_BTN_CLEAR_HOVER   = 0xFF4A5062;
    private static final int COLOR_BTN_CLEAR_TEXT    = 0xFFD0D0D0;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    /** Set by IgCalcOverlay to enable active/inactive title bar. */
    public IgCalcOverlay overlay;

    private int winX, winY;
    private boolean dragging    = false;
    private int     dragOffsetX, dragOffsetY;

    private String  expression    = "";
    private String  displayResult = "";
    private boolean resultShown   = false;

    // Calculation history — pairs of [expression, result]; per-world
    private final List<String[]> history = new ArrayList<>();
    private static final int MAX_HISTORY = 50;

    // Double-click C to clear history (iOS-style: C clears expression, CC clears history)
    private long lastClearTimeMs = 0;
    private static final long DOUBLE_CLICK_MS = 400;

    /** When set, called instead of super.close() — used by IgCalcOverlay. */
    public Runnable closeCallback = null;

    /** When set, called by the →N button to send result to notes. */
    public Runnable sendToNotes = null;

    // Button list — populated by rebuildButtons(), rendered manually in render()
    private final List<CalculatorButtonWidget> calcButtons = new ArrayList<>();

    // Keybind reference card
    private boolean keybindCardVisible = false;
    private static final int COLOR_MENU_BG     = 0xFF1C1E2B;
    private static final int COLOR_MENU_BORDER = 0xFF3A3F52;
    private static final int COLOR_MENU_HOVER  = 0xFF252A3E;
    private static final int MENU_ITEM_H       = 13;
    private static final int SEPARATOR_H       = 7;

    private static final String HISTORY_FILE = "igcalc_calc_history.properties";

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public CalculatorScreen() {
        super(Text.translatable("igcalc.title.calculator"));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @Override
    protected void init() {
        IgCalcWindowState.load();

        if (IgCalcWindowState.calcX == -1) {
            winX = (this.width  - WIN_WIDTH)  / 2;
            winY = (this.height - WIN_HEIGHT) / 2;
        } else {
            winX = Math.max(0, Math.min(this.width  - WIN_WIDTH,  IgCalcWindowState.calcX));
            winY = Math.max(0, Math.min(this.height - WIN_HEIGHT, IgCalcWindowState.calcY));
        }

        loadHistory();
        if (client != null) client.mouse.unlockCursor();
        rebuildButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (client != null) client.mouse.unlockCursor();
    }

    // -------------------------------------------------------------------------
    // Button construction
    // -------------------------------------------------------------------------
    private void rebuildButtons() {
        clearChildren();
        calcButtons.clear();

        int btnW   = 34;
        int btnH   = 22;
        int pad    = 4;
        int startX = winX + pad;
        int startY = winY + TITLE_BAR_HEIGHT + 4 + DISPLAY_H + 4; // below display panel

        // Row 1: C, (, ), /
        addCalcBtn(startX,                startY,                btnW, btnH, "C",
                COLOR_BTN_CLEAR,   COLOR_BTN_CLEAR_HOVER,   COLOR_BTN_CLEAR_TEXT,  this::onClear);
        addCalcBtn(startX + (btnW+pad),   startY,                btnW, btnH, "(",
                COLOR_BTN_OP,      COLOR_BTN_OP_HOVER,      COLOR_BTN_OP_TEXT,     () -> onInput("("));
        addCalcBtn(startX + (btnW+pad)*2, startY,                btnW, btnH, ")",
                COLOR_BTN_OP,      COLOR_BTN_OP_HOVER,      COLOR_BTN_OP_TEXT,     () -> onInput(")"));
        addCalcBtn(startX + (btnW+pad)*3, startY,                btnW, btnH, "/",
                COLOR_BTN_OP,      COLOR_BTN_OP_HOVER,      COLOR_BTN_OP_TEXT,     () -> onInput("/"));

        // Row 2: 7, 8, 9, *
        addCalcBtn(startX,                startY + (btnH+pad),   btnW, btnH, "7",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("7"));
        addCalcBtn(startX + (btnW+pad),   startY + (btnH+pad),   btnW, btnH, "8",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("8"));
        addCalcBtn(startX + (btnW+pad)*2, startY + (btnH+pad),   btnW, btnH, "9",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("9"));
        addCalcBtn(startX + (btnW+pad)*3, startY + (btnH+pad),   btnW, btnH, "*",
                COLOR_BTN_OP,      COLOR_BTN_OP_HOVER,      COLOR_BTN_OP_TEXT,     () -> onInput("*"));

        // Row 3: 4, 5, 6, -
        addCalcBtn(startX,                startY + (btnH+pad)*2, btnW, btnH, "4",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("4"));
        addCalcBtn(startX + (btnW+pad),   startY + (btnH+pad)*2, btnW, btnH, "5",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("5"));
        addCalcBtn(startX + (btnW+pad)*2, startY + (btnH+pad)*2, btnW, btnH, "6",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("6"));
        addCalcBtn(startX + (btnW+pad)*3, startY + (btnH+pad)*2, btnW, btnH, "-",
                COLOR_BTN_OP,      COLOR_BTN_OP_HOVER,      COLOR_BTN_OP_TEXT,     () -> onInput("-"));

        // Row 4: 1, 2, 3, +
        addCalcBtn(startX,                startY + (btnH+pad)*3, btnW, btnH, "1",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("1"));
        addCalcBtn(startX + (btnW+pad),   startY + (btnH+pad)*3, btnW, btnH, "2",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("2"));
        addCalcBtn(startX + (btnW+pad)*2, startY + (btnH+pad)*3, btnW, btnH, "3",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("3"));
        addCalcBtn(startX + (btnW+pad)*3, startY + (btnH+pad)*3, btnW, btnH, "+",
                COLOR_BTN_OP,      COLOR_BTN_OP_HOVER,      COLOR_BTN_OP_TEXT,     () -> onInput("+"));

        // Row 5: 0 (double-wide), ., =
        int zeroW = btnW * 2 + pad;
        addCalcBtn(startX,                startY + (btnH+pad)*4, zeroW, btnH, "0",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("0"));
        addCalcBtn(startX + zeroW + pad,  startY + (btnH+pad)*4, btnW,  btnH, ".",
                COLOR_BTN_NUMBER,  COLOR_BTN_NUMBER_HOVER,  COLOR_BTN_NUMBER_TEXT, () -> onInput("."));
        addCalcBtn(startX + (btnW+pad)*3, startY + (btnH+pad)*4, btnW,  btnH, "=",
                COLOR_BTN_EQUALS,  COLOR_BTN_EQUALS_HOVER,  COLOR_BTN_EQUALS_TEXT, this::onEquals);
    }

    private void addCalcBtn(int x, int y, int w, int h, String label,
                             int bg, int hover, int textCol, Runnable action) {
        calcButtons.add(new CalculatorButtonWidget(x, y, w, h, label, bg, hover, textCol, action));
    }

    // -------------------------------------------------------------------------
    // Calculator logic
    // -------------------------------------------------------------------------
    private void onInput(String token) {
        if (resultShown) {
            // Push the completed calculation into history before starting fresh
            if (!expression.isEmpty()) {
                history.add(new String[]{ expression, displayResult });
                if (history.size() > MAX_HISTORY) history.remove(0);
                saveHistory();
            }
            if (token.equals("+") || token.equals("-")
                    || token.equals("*") || token.equals("/")) {
                expression = displayResult + token;
            } else {
                expression = token;
            }
            displayResult = "";
            resultShown   = false;
        } else {
            expression += token;
        }
    }

    private void onClear() {
        long now = System.currentTimeMillis();
        if (expression.isEmpty() && !resultShown && (now - lastClearTimeMs) < DOUBLE_CLICK_MS) {
            // Double-click C while already cleared → clear all history
            history.clear();
            saveHistory();
            lastClearTimeMs = 0;
        } else {
            expression    = "";
            displayResult = "";
            resultShown   = false;
            lastClearTimeMs = now;
        }
    }

    private void onEquals() {
        if (expression.isEmpty()) return;
        try {
            double result = new ExpressionParser(expression.replaceAll("\\s+", "")).parse();
            if (result == Math.floor(result) && !Double.isInfinite(result) && Math.abs(result) < 1e15) {
                displayResult = String.valueOf((long) result);
            } else {
                displayResult = String.valueOf(result);
            }
        } catch (Exception e) {
            displayResult = "Error";
        }
        resultShown = true;
    }

    // -------------------------------------------------------------------------
    // Rendering helpers — rounded rectangles
    // -------------------------------------------------------------------------
    /** Fills a rectangle with fully rounded corners of radius r. */
    private static void fillRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y,     x + w - r, y + h,     color); // centre vertical strip
        ctx.fill(x,     y + r, x + r,     y + h - r, color); // left strip
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color); // right strip
        for (int i = 0; i < r; i++) {
            double dy   = r - i - 0.5;
            int    xOff = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + xOff, y + i,         x + w - xOff, y + i + 1,         color); // top arc
            ctx.fill(x + xOff, y + h - 1 - i, x + w - xOff, y + h - i,         color); // bottom arc
        }
    }

    /** Fills a rectangle with rounded top corners only (bottom edge is flat). */
    private static void fillRoundedRectTopOnly(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x, y + r, x + w, y + h, color); // bottom portion (full width)
        for (int i = 0; i < r; i++) {
            double dy   = r - i - 0.5;
            int    xOff = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + xOff, y + i, x + w - xOff, y + i + 1, color); // top arc
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int wx = winX;
        int wy = winY;

        // Window: semi-transparent body, opaque title bar
        fillRoundedRect(context, wx, wy, WIN_WIDTH, WIN_HEIGHT, 6, COLOR_BG);
        boolean isFocused = (overlay != null && overlay.getFocusedWindow() == this);
        int titleBarColor = isFocused ? COLOR_TITLE_BAR : 0xFF1E2030;
        fillRoundedRectTopOnly(context, wx, wy, WIN_WIDTH, TITLE_BAR_HEIGHT, 6, titleBarColor);
        int iconDefault = isFocused ? 0xFF6B7099 : 0xFF4A5068;

        // ── Title bar buttons ─────────────────────────────────────────────
        int TB_W = 14, TB_H = 12, TB_GAP = 2;
        int tbY = wy + (TITLE_BAR_HEIGHT - TB_H) / 2;
        int tbTextY = tbY + (TB_H - textRenderer.fontHeight) / 2;

        // Close [×] — leftmost
        int closeX = wx + 4;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + TB_W
                          && mouseY >= tbY && mouseY < tbY + TB_H;
        String closeIcon = "\u00D7";
        context.drawText(textRenderer, closeIcon,
                closeX + (TB_W - textRenderer.getWidth(closeIcon)) / 2, tbTextY,
                hoverClose ? COLOR_DOT_RED : iconDefault, false);

        // Right-side buttons (right to left): →N (if wired), Help
        int rightBtnX = wx + WIN_WIDTH - 4 - TB_W;

        if (sendToNotes != null) {
            // →N button — rightmost
            int nBtnX = rightBtnX;
            boolean hoverN = mouseX >= nBtnX && mouseX < nBtnX + TB_W
                          && mouseY >= tbY && mouseY < tbY + TB_H;
            String nIcon = "\u2192N";
            context.drawText(textRenderer, nIcon,
                    nBtnX + (TB_W - textRenderer.getWidth(nIcon)) / 2, tbTextY,
                    hoverN ? 0xFF0A84FF : iconDefault, false);
            rightBtnX = nBtnX - TB_GAP - TB_W;
        }

        // Help [?]
        int helpBtnX = rightBtnX;
        boolean hoverHelp = mouseX >= helpBtnX && mouseX < helpBtnX + TB_W
                         && mouseY >= tbY && mouseY < tbY + TB_H;
        String helpIcon = "?";
        context.drawText(textRenderer, helpIcon,
                helpBtnX + (TB_W - textRenderer.getWidth(helpIcon)) / 2, tbTextY,
                hoverHelp ? 0xFF0A84FF : iconDefault, false);

        // Title — centered between close and right buttons
        int titleLeft = closeX + TB_W + 4;
        int titleRight = helpBtnX - 4;
        String titleStr = "Calculator";
        int titleW = textRenderer.getWidth(titleStr);
        context.drawText(textRenderer, titleStr,
                titleLeft + (titleRight - titleLeft - titleW) / 2,
                wy + (TITLE_BAR_HEIGHT - textRenderer.fontHeight) / 2,
                COLOR_TITLE_TEXT, false);

        // Display panel
        int dispX  = wx + 4;
        int dispY  = wy + TITLE_BAR_HEIGHT + 4;
        int dispW  = WIN_WIDTH - 8;
        int innerX = dispX + 4;
        int innerW = dispW - 8;
        fillRoundedRect(context, dispX - 1, dispY - 1, dispW + 2, DISPLAY_H + 2, 3, COLOR_BORDER);
        fillRoundedRect(context, dispX,     dispY,     dispW,     DISPLAY_H,     2, COLOR_DISPLAY_BG);

        // --- History entries (scrolled, anchored at top) ---
        // Reserve bottom area for: current expression + optional result line
        int currentAreaH = 4 + HIST_LINE_H + (resultShown ? HIST_LINE_H : 0);
        int histAreaH    = DISPLAY_H - 4 - currentAreaH; // 4px top padding
        int maxHistLines = histAreaH / HIST_LINE_H;
        int histStart    = Math.max(0, history.size() - maxHistLines);

        int lineY = dispY + 4;
        for (int i = histStart; i < history.size(); i++) {
            String hExpr   = history.get(i)[0];
            String hResult = history.get(i)[1];
            int    resW    = textRenderer.getWidth(hResult);
            String trimmed = textRenderer.trimToWidth(hExpr, innerW - resW - 4);
            context.drawText(textRenderer, trimmed,  innerX,                  lineY, COLOR_DISPLAY_EXPR, false);
            context.drawText(textRenderer, hResult,  innerX + innerW - resW,  lineY, COLOR_HIST_RESULT,  false);
            lineY += HIST_LINE_H;
        }

        // Separator between history and active input (only when there is history)
        if (!history.isEmpty()) {
            int sepY = dispY + DISPLAY_H - currentAreaH - 1;
            context.fill(dispX + 2, sepY, dispX + dispW - 2, sepY + 1, COLOR_HIST_SEP);
        }

        // --- Current expression (bottom of panel) ---
        int curExprY = dispY + DISPLAY_H - currentAreaH + 2;
        String exprDisplay = expression.isEmpty() && !resultShown ? "0" : expression;
        // Trim from the left if too wide, showing "..." prefix
        if (textRenderer.getWidth(exprDisplay) > innerW) {
            while (exprDisplay.length() > 1 && textRenderer.getWidth("..." + exprDisplay) > innerW) {
                exprDisplay = exprDisplay.substring(1);
            }
            exprDisplay = "..." + exprDisplay;
        }
        int exprColor = resultShown ? COLOR_DISPLAY_EXPR : COLOR_DISPLAY_TEXT;
        context.drawText(textRenderer, exprDisplay, innerX, curExprY, exprColor, false);

        // --- Current result (shown after pressing =) ---
        if (resultShown && !displayResult.isEmpty()) {
            int resW = textRenderer.getWidth(displayResult);
            context.drawText(textRenderer, displayResult,
                    innerX + innerW - resW,
                    curExprY + HIST_LINE_H,
                    COLOR_DISPLAY_RESULT, false);
        }

        // Draw calculator buttons manually
        for (CalculatorButtonWidget btn : calcButtons) {
            boolean hovered = mouseX >= btn.x() && mouseX < btn.x() + btn.w()
                           && mouseY >= btn.y() && mouseY < btn.y() + btn.h();
            int bg = hovered ? btn.hoverColor() : btn.bgColor();
            fillRoundedRect(context, btn.x(), btn.y(), btn.w(), btn.h(), 3, bg);
            // C button shows "AC" when expression is already clear (next press clears history)
            String renderLabel = btn.label();
            if ("C".equals(renderLabel) && expression.isEmpty() && !resultShown && !history.isEmpty()) {
                renderLabel = "AC";
            }
            int labelX = btn.x() + (btn.w() - textRenderer.getWidth(renderLabel)) / 2;
            int labelY = btn.y() + (btn.h() - textRenderer.fontHeight) / 2;
            context.drawText(textRenderer, renderLabel, labelX, labelY, btn.textColor(), false);
        }

        // Keybind reference card
        if (keybindCardVisible) {
            int cardW = 150;
            int cardX = Math.max(wx, helpBtnX + TB_W - cardW);
            int cardY2 = tbY + TB_H + 2;
            String[][] rows = {
                {"Send to Notes", "\u2192N btn"},
                {"Evaluate", "Enter"},
                {"Clear", "Delete"},
                {"Delete Last", "Backspace"},
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

        super.render(context, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------
    // Mouse input
    // -------------------------------------------------------------------------
    @Override
    public boolean mouseClicked(Click click, boolean propagated) {
        int mx = (int) click.x();
        int my = (int) click.y();

        // ── Title bar button hit testing ──────────────────────────────────
        int TB_W = 14, TB_H = 12, TB_GAP = 2;
        int tbY = winY + (TITLE_BAR_HEIGHT - TB_H) / 2;
        boolean inTbRow = my >= tbY && my < tbY + TB_H;
        int closeBtnX = winX + 4;
        int rightBtnX = winX + WIN_WIDTH - 4 - TB_W;

        // Close [×]
        if (inTbRow && mx >= closeBtnX && mx < closeBtnX + TB_W) {
            this.close();
            return true;
        }
        // →N button (rightmost if wired)
        if (sendToNotes != null && inTbRow && mx >= rightBtnX && mx < rightBtnX + TB_W) {
            sendToNotes.run();
            return true;
        }
        // Keybind card click
        if (keybindCardVisible) {
            keybindCardVisible = false;
            int helpX = sendToNotes != null ? rightBtnX - TB_GAP - TB_W : rightBtnX;
            int cardW = 150, cardX = Math.max(winX, helpX + TB_W - cardW);
            int cardY2 = tbY + TB_H + 2;
            int cardH = 4 * MENU_ITEM_H + SEPARATOR_H + MENU_ITEM_H + 4;
            if (mx >= cardX && mx < cardX + cardW && my >= cardY2 && my < cardY2 + cardH) {
                // Check tour link click
                int tourY = cardY2 + 2 + 4 * MENU_ITEM_H + SEPARATOR_H;
                if (my >= tourY && my < tourY + MENU_ITEM_H) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.currentScreen instanceof IgCalcOverlay ov) {
                        com.igcalc.config.IgCalcTutorialState.reset();
                        ov.startTutorialFiltered(TutorialScreen.CALC_STEPS);
                    }
                }
            }
            return true;
        }
        // Help [?]
        int helpClickX = sendToNotes != null ? rightBtnX - TB_GAP - TB_W : rightBtnX;
        if (inTbRow && mx >= helpClickX && mx < helpClickX + TB_W) {
            keybindCardVisible = !keybindCardVisible;
            return true;
        }
        // Drag on title bar (between close and right buttons)
        int dragRightLimit = helpClickX - 4;
        if (mx >= winX + 20 && mx <= dragRightLimit
                && my >= winY && my <= winY + TITLE_BAR_HEIGHT) {
            dragging    = true;
            dragOffsetX = mx - winX;
            dragOffsetY = my - winY;
            return true;
        }
        // Calculator buttons
        for (CalculatorButtonWidget btn : calcButtons) {
            if (mx >= btn.x() && mx < btn.x() + btn.w()
                    && my >= btn.y() && my < btn.y() + btn.h()) {
                btn.action().run();
                return true;
            }
        }
        return super.mouseClicked(click, propagated);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            winX = Math.max(0, Math.min(this.width  - WIN_WIDTH,  (int) click.x() - dragOffsetX));
            winY = Math.max(0, Math.min(this.height - WIN_HEIGHT, (int) click.y() - dragOffsetY));
            rebuildButtons();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        IgCalcWindowState.calcX = winX;
        IgCalcWindowState.calcY = winY;
        IgCalcWindowState.save();
        return super.mouseReleased(click);
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------
    @Override
    public void close() {
        // Save any pending result to history before closing
        if (resultShown && !expression.isEmpty()) {
            history.add(new String[]{ expression, displayResult });
            if (history.size() > MAX_HISTORY) history.remove(0);
        }
        saveHistory();
        IgCalcWindowState.calcX = winX;
        IgCalcWindowState.calcY = winY;
        IgCalcWindowState.save();
        if (closeCallback != null) closeCallback.run();
        else super.close();
    }

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------
    @Override
    public boolean shouldPause() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Keyboard input
    // -------------------------------------------------------------------------
    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        if (Character.isDigit(chr) || chr == '+' || chr == '-' || chr == '*'
                || chr == '/' || chr == '.' || chr == '(' || chr == ')') {
            onInput(String.valueOf(chr));
            return true;
        }
        if (chr == '=') {
            onEquals();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        switch (keyCode) {
            case 256 -> { // ESC
                if (keybindCardVisible) { keybindCardVisible = false; return true; }
                this.close();
                return true;
            }
            case 257, 335 -> { // Enter / Numpad Enter → equals
                onEquals();
                return true;
            }
            case 259 -> { // Backspace → delete last character
                if (!expression.isEmpty()) {
                    if (resultShown) {
                        expression    = "";
                        displayResult = "";
                        resultShown   = false;
                    } else {
                        expression = expression.substring(0, expression.length() - 1);
                    }
                }
                return true;
            }
            case 261 -> { // Delete → clear all
                onClear();
                return true;
            }
            // Numpad numbers (KP_0–KP_9 = 320–329)
            case 320 -> { onInput("0"); return true; }
            case 321 -> { onInput("1"); return true; }
            case 322 -> { onInput("2"); return true; }
            case 323 -> { onInput("3"); return true; }
            case 324 -> { onInput("4"); return true; }
            case 325 -> { onInput("5"); return true; }
            case 326 -> { onInput("6"); return true; }
            case 327 -> { onInput("7"); return true; }
            case 328 -> { onInput("8"); return true; }
            case 329 -> { onInput("9"); return true; }
            // Numpad operators
            case 331 -> { onInput("/"); return true; }   // KP_DIVIDE
            case 332 -> { onInput("*"); return true; }   // KP_MULTIPLY
            case 333 -> { onInput("-"); return true; }   // KP_SUBTRACT
            case 334 -> { onInput("+"); return true; }   // KP_ADD
            case 330 -> { onInput("."); return true; }   // KP_DECIMAL
        }
        return super.keyPressed(input);
    }

    // -------------------------------------------------------------------------
    // History persistence
    // -------------------------------------------------------------------------
    private static Path historyPath() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path base = (mc.getServer() != null)
                ? mc.getServer().getSavePath(WorldSavePath.ROOT)
                : mc.runDirectory.toPath();
        return base.resolve(HISTORY_FILE);
    }

    private void loadHistory() {
        Path path = historyPath();
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException ignored) { return; }
        int count = 0;
        try { count = Integer.parseInt(props.getProperty("history.count", "0")); } catch (NumberFormatException ignored) {}
        history.clear();
        for (int i = 0; i < Math.min(count, MAX_HISTORY); i++) {
            String expr   = props.getProperty("history." + i + ".expr", "");
            String result = props.getProperty("history." + i + ".result", "");
            if (!expr.isEmpty()) history.add(new String[]{ expr, result });
        }
    }

    private void saveHistory() {
        Properties props = new Properties();
        props.setProperty("history.count", String.valueOf(history.size()));
        for (int i = 0; i < history.size(); i++) {
            props.setProperty("history." + i + ".expr",   history.get(i)[0]);
            props.setProperty("history." + i + ".result", history.get(i)[1]);
        }
        try (var writer = Files.newBufferedWriter(historyPath())) {
            props.store(writer, "Notes+ calculator history");
        } catch (IOException ignored) {}
    }

    /** Public accessor for the current expression. */
    public String getExpression()    { return expression; }
    /** Public accessor for the current display result. */
    public String getDisplayResult() { return displayResult; }

    // -------------------------------------------------------------------------
    // Recursive-descent expression parser
    // Grammar:
    //   expr   := addSub
    //   addSub := mulDiv ( ('+' | '-') mulDiv )*
    //   mulDiv := unary  ( ('*' | '/') unary  )*
    //   unary  := '-' primary | '+' primary | primary
    //   primary:= '(' expr ')' | number
    // -------------------------------------------------------------------------
    private static class ExpressionParser {
        private final String src;
        private int pos;

        ExpressionParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        double parse() {
            double v = addSub();
            if (pos < src.length())
                throw new RuntimeException("Unexpected: " + src.charAt(pos));
            return v;
        }

        private double addSub() {
            double left = mulDiv();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '+' || c == '-') {
                    pos++;
                    double right = mulDiv();
                    left = (c == '+') ? left + right : left - right;
                } else break;
            }
            return left;
        }

        private double mulDiv() {
            double left = unary();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '*' || c == '/') {
                    pos++;
                    double right = unary();
                    if (c == '/') {
                        if (right == 0) throw new ArithmeticException("Division by zero");
                        left /= right;
                    } else {
                        left *= right;
                    }
                } else break;
            }
            return left;
        }

        private double unary() {
            if (pos < src.length() && src.charAt(pos) == '-') { pos++; return -primary(); }
            if (pos < src.length() && src.charAt(pos) == '+') { pos++; }
            return primary();
        }

        private double primary() {
            if (pos < src.length() && src.charAt(pos) == '(') {
                pos++;
                double v = addSub();
                if (pos < src.length() && src.charAt(pos) == ')') pos++;
                else throw new RuntimeException("Missing ')'");
                return v;
            }
            return number();
        }

        private double number() {
            int start = pos;
            while (pos < src.length()
                    && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) throw new RuntimeException("Expected number at pos " + pos);
            return Double.parseDouble(src.substring(start, pos));
        }
    }
}
