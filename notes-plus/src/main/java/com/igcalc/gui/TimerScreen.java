package com.igcalc.gui;

import com.igcalc.config.IgCalcWindowState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TimerScreen extends Screen {

    // -------------------------------------------------------------------------
    // Window geometry
    // -------------------------------------------------------------------------
    static final int WIN_W         = 310;
    static final int WIN_H         = 216;  // +16 for tab bar
    static final int TITLE_BAR_H   = 18;
    static final int TAB_BAR_H     = 16;
    static final int CLOCK_PANEL_W = 160;
    static final int CLOCK_CX_OFF  = 80;
    static final int CLOCK_CY_OFF  = 96;   // offset from winY (includes title bar)
    static final int CLOCK_R       = 42;
    static final int ARC_INNER_R   = 35;
    static final int ARC_OUTER_R   = 42;

    // -------------------------------------------------------------------------
    // Color palette
    // -------------------------------------------------------------------------
    private static final int COLOR_BG          = 0xEE1C1E2B;
    private static final int COLOR_TITLE_BAR   = 0xFF262A3C;
    private static final int COLOR_BORDER      = 0xFF3A3F52;
    private static final int COLOR_TITLE       = 0xFFE5E5E5;
    private static final int COLOR_DOT_RED     = 0xFFFF5F57;
    private static final int COLOR_LABEL       = 0xFFB0B8D0;
    private static final int COLOR_COMPLETE    = 0xFFE8C170;
    private static final int COLOR_CLOCK_BG    = 0xFF1A1C28;
    private static final int COLOR_HAND_IDLE   = 0xFF555870;
    private static final int COLOR_HAND_LIVE   = 0xFFE5E5E5;
    private static final int COLOR_ARC         = 0xFF0A84FF;
    private static final int COLOR_TICK        = 0xFF3A3F52;
    private static final int COLOR_BTN_NORM    = 0xFF2E3347;
    private static final int COLOR_BTN_HOVER   = 0xFF3A4060;
    private static final int COLOR_BTN_RED     = 0xFF6B1A18;
    private static final int COLOR_BTN_RED_H   = 0xFF8B2A28;
    private static final int COLOR_BTN_PAUSE   = 0xFF1A3050;
    private static final int COLOR_BTN_PAUSE_H = 0xFF1E4070;
    private static final int COLOR_BTN_GREEN   = 0xFF2A6040;
    private static final int COLOR_BTN_GREEN_H = 0xFF3A8050;

    // -------------------------------------------------------------------------
    // Mode and state enums
    // -------------------------------------------------------------------------
    public enum Mode { TIMER, STOPWATCH }

    enum State { IDLE, ACTIVE, PAUSED, COMPLETE }

    public enum SwState { IDLE, RUNNING, STOPPED }

    // -------------------------------------------------------------------------
    // Timer state
    // -------------------------------------------------------------------------
    State state = State.IDLE;

    int[] digits     = new int[6];  // [H10, H1, M10, M1, S10, S1]
    int[] lastDigits = new int[6];

    long startMs;
    long durationMs;
    long pausedElapsedMs;

    // -------------------------------------------------------------------------
    // Mode + stopwatch state
    // -------------------------------------------------------------------------
    public Mode    currentMode = Mode.TIMER;
    public SwState swState     = SwState.IDLE;
    public long    swStartMs   = 0;
    public long    swElapsedMs = 0;
    public List<Long> swLaps  = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Window / drag
    // -------------------------------------------------------------------------
    /** Set by IgCalcOverlay to enable active/inactive title bar. */
    public IgCalcOverlay overlay;

    int winX, winY;
    private boolean dragging;
    private int dragOffX, dragOffY;

    public Runnable closeCallback;
    public Runnable onTimerComplete;

    // Keybind reference card
    private boolean keybindCardVisible = false;
    private static final int COLOR_MENU_BG     = 0xFF1C1E2B;
    private static final int COLOR_MENU_BORDER = 0xFF3A3F52;
    private static final int COLOR_MENU_HOVER  = 0xFF252A3E;
    private static final int MENU_ITEM_H       = 13;
    private static final int SEPARATOR_H       = 7;

    // Timer button hit areas (computed during render)
    private int cancelX, cancelY, cancelW, cancelH;
    private int pauseX,  pauseY,  pauseW,  pauseH;
    private int repeatX, repeatY, repeatW, repeatH;

    // Stopwatch button hit areas (computed during render; -1 = not shown)
    private int swBtn1X = -1, swBtn1Y, swBtn1W, swBtn1H;
    private int swBtn2X = -1, swBtn2Y, swBtn2W, swBtn2H;
    private int swLapBtnX = -1, swLapBtnY, swLapBtnW, swLapBtnH;
    private int lapScrollOffset = 0;

    public TimerScreen() {
        super(Text.literal("Timer"));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @Override
    protected void init() {
        IgCalcWindowState.load();
        if (IgCalcWindowState.timerX < 0 || IgCalcWindowState.timerY < 0) {
            winX = (this.width  - WIN_W) / 2 + 60;
            winY = (this.height - WIN_H) / 2;
        } else {
            winX = IgCalcWindowState.timerX;
            winY = IgCalcWindowState.timerY;
        }
        winX = Math.max(0, Math.min(winX, this.width  - WIN_W));
        winY = Math.max(0, Math.min(winY, this.height - WIN_H));
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------
    @Override
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.mouse.unlockCursor();
        if (currentMode == Mode.TIMER
                && state == State.ACTIVE
                && getEffectiveElapsedMs() >= durationMs) {
            pausedElapsedMs = durationMs;
            state = State.COMPLETE;
            syncToHud();
            if (mc != null && mc.player != null)
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), 1.0f, 2.0f);
            if (onTimerComplete != null) onTimerComplete.run();
        }
    }

    // -------------------------------------------------------------------------
    // Time helpers
    // -------------------------------------------------------------------------
    private long getEffectiveElapsedMs() {
        return (state == State.ACTIVE)
            ? System.currentTimeMillis() - startMs
            : pausedElapsedMs;
    }

    private long getSwElapsedMs() {
        return (swState == SwState.RUNNING)
            ? swElapsedMs + (System.currentTimeMillis() - swStartMs)
            : swElapsedMs;
    }

    private int getTotalSeconds() {
        return digits[0] * 36000 + digits[1] * 3600
             + digits[2] * 600   + digits[3] * 60
             + digits[4] * 10    + digits[5];
    }

    private void pushDigit(int d) {
        System.arraycopy(digits, 1, digits, 0, 5);
        digits[5] = d;
    }

    private void popDigit() {
        System.arraycopy(digits, 0, digits, 1, 5);
        digits[0] = 0;
    }

    private void startTimer() {
        lastDigits      = digits.clone();
        durationMs      = getTotalSeconds() * 1000L;
        startMs         = System.currentTimeMillis();
        pausedElapsedMs = 0;
        state           = State.ACTIVE;
    }

    private void pauseTimer() {
        pausedElapsedMs = System.currentTimeMillis() - startMs;
        state = State.PAUSED;
    }

    private void resumeTimer() {
        startMs = System.currentTimeMillis() - pausedElapsedMs;
        state   = State.ACTIVE;
    }

    private void syncToHud() {
        if (!HudRenderer.INSTANCE.pinnedTimer) return;
        HudRenderer.INSTANCE.timerMode          = currentMode;
        HudRenderer.INSTANCE.timerState         = state;
        HudRenderer.INSTANCE.timerStartMs       = startMs;
        HudRenderer.INSTANCE.timerDurationMs    = durationMs;
        HudRenderer.INSTANCE.timerPausedElapsedMs = pausedElapsedMs;
        HudRenderer.INSTANCE.timerDigits        = digits.clone();
        HudRenderer.INSTANCE.timerLastDigits    = lastDigits.clone();
        HudRenderer.INSTANCE.swState            = swState;
        HudRenderer.INSTANCE.swStartMs          = swStartMs;
        HudRenderer.INSTANCE.swElapsedMs        = swElapsedMs;
        HudRenderer.INSTANCE.swLapTimes         = new ArrayList<>(swLaps);
    }

    // -------------------------------------------------------------------------
    // Rendering helpers
    // -------------------------------------------------------------------------
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

    private static void drawFilledCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.sqrt((double)(r * r - dy * dy));
            ctx.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
        }
    }

    private static void drawArc(DrawContext ctx, int cx, int cy, int inner, int outer, double sweep, int color) {
        for (int px = cx - outer; px <= cx + outer; px++) {
            for (int py = cy - outer; py <= cy + outer; py++) {
                double dx   = px - cx + 0.5;
                double dy   = py - cy + 0.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < inner || dist > outer) continue;
                double angle = Math.atan2(dx, -dy);
                if (angle < 0) angle += 2 * Math.PI;
                if (angle <= sweep) ctx.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private static void drawHand(DrawContext ctx, int cx, int cy, double angle, int len, int color) {
        int ex    = cx + (int)(Math.sin(angle) * len);
        int ey    = cy - (int)(Math.cos(angle) * len);
        int dx    = ex - cx;
        int dy    = ey - cy;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) { ctx.fill(cx, cy, cx + 2, cy + 2, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = cx + dx * i / steps;
            int y = cy + dy * i / steps;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawTickMarks(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int h = 0; h < 12; h++) {
            double a  = h * Math.PI / 6.0;
            int    ox = cx + (int)(Math.sin(a) * (r - 1));
            int    oy = cy - (int)(Math.cos(a) * (r - 1));
            ctx.fill(ox, oy, ox + 2, oy + 2, color);
        }
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer    tr = mc.textRenderer;

        // === Window chrome ===
        fillRoundedRect(ctx, winX, winY, WIN_W, WIN_H, 6, COLOR_BG);
        boolean isFocused = (overlay != null && overlay.getFocusedWindow() == this);
        int titleBarCol = isFocused ? COLOR_TITLE_BAR : 0xFF1E2030;
        fillRoundedRectTopOnly(ctx, winX, winY, WIN_W, TITLE_BAR_H, 6, titleBarCol);
        int iconDefault = isFocused ? 0xFF6B7099 : 0xFF4A5068;
        ctx.fill(winX,           winY,           winX + WIN_W, winY + 1,     COLOR_BORDER);
        ctx.fill(winX,           winY + WIN_H-1, winX + WIN_W, winY + WIN_H, COLOR_BORDER);
        ctx.fill(winX,           winY,           winX + 1,     winY + WIN_H, COLOR_BORDER);
        ctx.fill(winX + WIN_W-1, winY,           winX + WIN_W, winY + WIN_H, COLOR_BORDER);

        // ── Title bar buttons ─────────────────────────────────────────────
        int TB_W = 14, TB_H = 12, TB_GAP = 2;
        int tbY = winY + (TITLE_BAR_H - TB_H) / 2;
        int tbTextY = tbY + (TB_H - tr.fontHeight) / 2;

        // Close [×] — leftmost
        int closeX = winX + 4;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + TB_W
                          && mouseY >= tbY && mouseY < tbY + TB_H;
        String closeIcon = "\u00D7";
        ctx.drawText(tr, closeIcon,
                closeX + (TB_W - tr.getWidth(closeIcon)) / 2, tbTextY,
                hoverClose ? COLOR_DOT_RED : iconDefault, false);

        // Right-side buttons (right to left): Pin, Help
        boolean isPinned = HudRenderer.INSTANCE.pinnedTimer;

        int pinBtnX = winX + WIN_W - 4 - TB_W;
        boolean hoverPin = mouseX >= pinBtnX && mouseX < pinBtnX + TB_W
                        && mouseY >= tbY && mouseY < tbY + TB_H;
        String pinIcon = "\u2691";
        ctx.drawText(tr, pinIcon,
                pinBtnX + (TB_W - tr.getWidth(pinIcon)) / 2, tbTextY,
                (isPinned || hoverPin) ? 0xFFFEBC2E : iconDefault, false);

        int helpBtnX = pinBtnX - TB_GAP - TB_W;
        boolean hoverHelp = mouseX >= helpBtnX && mouseX < helpBtnX + TB_W
                         && mouseY >= tbY && mouseY < tbY + TB_H;
        String helpIcon = "?";
        ctx.drawText(tr, helpIcon,
                helpBtnX + (TB_W - tr.getWidth(helpIcon)) / 2, tbTextY,
                hoverHelp ? 0xFF0A84FF : iconDefault, false);

        // Title — centered between close and right buttons
        int titleLeft = closeX + TB_W + 4;
        int titleRight = helpBtnX - 4;
        String titleStr = "Timer";
        int titleW = tr.getWidth(titleStr);
        ctx.drawText(tr, titleStr,
                titleLeft + (titleRight - titleLeft - titleW) / 2,
                winY + (TITLE_BAR_H - tr.fontHeight) / 2,
                COLOR_TITLE, false);

        // === Tab bar ===
        int tabY = winY + TITLE_BAR_H;
        ctx.fill(winX + 1, tabY, winX + WIN_W - 1, tabY + TAB_BAR_H, 0xFF1A1C2A);

        boolean timerTabActive = (currentMode == Mode.TIMER);
        boolean timerTabHov    = !timerTabActive
                && (mouseX >= winX + 8 && mouseX < winX + 62
                 && mouseY >= tabY && mouseY < tabY + TAB_BAR_H);
        int timerTabX1 = winX + 8, timerTabX2 = winX + 62;
        ctx.fill(timerTabX1, tabY + 2, timerTabX2, tabY + TAB_BAR_H - 1,
                timerTabActive ? 0xFF262A3C : (timerTabHov ? 0xFF1E2235 : 0xFF1A1C2A));
        int timerLabelW = tr.getWidth("Timer");
        ctx.drawText(tr, "Timer",
                timerTabX1 + (timerTabX2 - timerTabX1 - timerLabelW) / 2,
                tabY + (TAB_BAR_H - tr.fontHeight) / 2,
                timerTabActive ? 0xFFE5E5E5 : 0xFF6B7099, false);

        boolean swTabActive = (currentMode == Mode.STOPWATCH);
        boolean swTabHov    = !swTabActive
                && (mouseX >= winX + 66 && mouseX < winX + 148
                 && mouseY >= tabY && mouseY < tabY + TAB_BAR_H);
        int swTabX1 = winX + 66, swTabX2 = winX + 148;
        ctx.fill(swTabX1, tabY + 2, swTabX2, tabY + TAB_BAR_H - 1,
                swTabActive ? 0xFF262A3C : (swTabHov ? 0xFF1E2235 : 0xFF1A1C2A));
        int swLabelW = tr.getWidth("Stopwatch");
        ctx.drawText(tr, "Stopwatch",
                swTabX1 + (swTabX2 - swTabX1 - swLabelW) / 2,
                tabY + (TAB_BAR_H - tr.fontHeight) / 2,
                swTabActive ? 0xFFE5E5E5 : 0xFF6B7099, false);

        // Content base Y (below title bar + tab bar)
        int contentBaseY = winY + TITLE_BAR_H + TAB_BAR_H;

        // Panel divider
        ctx.fill(winX + CLOCK_PANEL_W, contentBaseY,
                 winX + CLOCK_PANEL_W + 1, winY + WIN_H, COLOR_BORDER);

        // === Left panel — analog clock / stopwatch arc ===
        int cx = winX + CLOCK_CX_OFF;
        int cy = winY + TAB_BAR_H + CLOCK_CY_OFF;

        drawFilledCircle(ctx, cx, cy, CLOCK_R, COLOR_CLOCK_BG);
        drawTickMarks(ctx, cx, cy, CLOCK_R, COLOR_TICK);

        if (currentMode == Mode.TIMER) {
            if (state == State.IDLE) {
                long  worldTime   = (mc.world != null) ? mc.world.getTimeOfDay() : 0L;
                float smoothTime  = worldTime + delta;
                double minuteAngle = (smoothTime % 1000f / 1000f)   * 2 * Math.PI;
                double hourAngle   = (smoothTime % 12000f / 12000f) * 2 * Math.PI;
                drawHand(ctx, cx, cy, hourAngle,   22, COLOR_HAND_IDLE);
                drawHand(ctx, cx, cy, minuteAngle, 32, COLOR_HAND_IDLE);
            } else if (state == State.ACTIVE || state == State.PAUSED) {
                double progress = Math.min(1.0, (double) getEffectiveElapsedMs() / durationMs);
                double sweep    = progress * 2 * Math.PI;
                drawArc(ctx, cx, cy, ARC_INNER_R, ARC_OUTER_R, sweep, COLOR_ARC);
                drawHand(ctx, cx, cy, sweep, CLOCK_R - 6, COLOR_HAND_LIVE);
            } else { // COMPLETE
                drawArc(ctx, cx, cy, ARC_INNER_R, ARC_OUTER_R, 2 * Math.PI, COLOR_ARC);
                drawHand(ctx, cx, cy, 0, CLOCK_R - 6, COLOR_HAND_LIVE);
            }
        } else { // STOPWATCH
            if (swState == SwState.IDLE) {
                long  worldTime   = (mc.world != null) ? mc.world.getTimeOfDay() : 0L;
                float smoothTime  = worldTime + delta;
                double minuteAngle = (smoothTime % 1000f / 1000f)   * 2 * Math.PI;
                double hourAngle   = (smoothTime % 12000f / 12000f) * 2 * Math.PI;
                drawHand(ctx, cx, cy, hourAngle,   22, COLOR_HAND_IDLE);
                drawHand(ctx, cx, cy, minuteAngle, 32, COLOR_HAND_IDLE);
            } else {
                long   elapsed  = getSwElapsedMs();
                double progress = (elapsed / 1000.0) % 60.0 / 60.0;
                double sweep    = progress * 2 * Math.PI;
                drawArc(ctx, cx, cy, ARC_INNER_R, ARC_OUTER_R, sweep, COLOR_ARC);
                drawHand(ctx, cx, cy, sweep, CLOCK_R - 6, COLOR_HAND_LIVE);
            }
        }

        // === Buttons under clock (timer cancel/pause or stopwatch start/stop/reset) ===
        int btnW   = 54;
        int btnH   = 14;
        int btnGap = 6;
        int btnY   = winY + WIN_H - 22;

        if (currentMode == Mode.TIMER) {
            int totalBW = btnW * 2 + btnGap;
            cancelX = winX + CLOCK_CX_OFF - totalBW / 2;
            cancelY = btnY;
            cancelW = btnW;
            cancelH = btnH;
            pauseX  = cancelX + btnW + btnGap;
            pauseY  = btnY;
            pauseW  = btnW;
            pauseH  = btnH;

            boolean timerRunning = (state == State.ACTIVE || state == State.PAUSED);
            boolean hoverCancel  = mouseX >= cancelX && mouseX < cancelX + cancelW
                                && mouseY >= cancelY && mouseY < cancelY + cancelH;
            int cancelBg = timerRunning ? (hoverCancel ? COLOR_BTN_RED_H : COLOR_BTN_RED)
                                        : (hoverCancel ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
            fillRoundedRect(ctx, cancelX, cancelY, cancelW, cancelH, 3, cancelBg);
            String cancelLabel = "Cancel";
            ctx.drawText(tr, cancelLabel,
                    cancelX + (cancelW - tr.getWidth(cancelLabel)) / 2,
                    cancelY + (cancelH - tr.fontHeight) / 2,
                    0xFFE5E5E5, false);

            if (timerRunning) {
                boolean hoverPause = mouseX >= pauseX && mouseX < pauseX + pauseW
                                  && mouseY >= pauseY && mouseY < pauseY + pauseH;
                int pauseBg = (state == State.PAUSED)
                        ? (hoverPause ? COLOR_BTN_PAUSE_H : COLOR_BTN_PAUSE)
                        : (hoverPause ? COLOR_BTN_HOVER   : COLOR_BTN_NORM);
                fillRoundedRect(ctx, pauseX, pauseY, pauseW, pauseH, 3, pauseBg);
                String pauseLabel = (state == State.PAUSED) ? "Resume" : "Pause";
                ctx.drawText(tr, pauseLabel,
                        pauseX + (pauseW - tr.getWidth(pauseLabel)) / 2,
                        pauseY + (pauseH - tr.fontHeight) / 2,
                        0xFFE5E5E5, false);
            }
        } else { // STOPWATCH buttons
            swBtn1W = btnW;
            swBtn1H = btnH;
            swBtn1Y = btnY;

            switch (swState) {
                case IDLE -> {
                    // Single [Start] button centered under clock
                    swBtn1X = winX + CLOCK_CX_OFF - btnW / 2;
                    swBtn2X = -1;
                    boolean hov1 = mouseX >= swBtn1X && mouseX < swBtn1X + swBtn1W
                                && mouseY >= swBtn1Y && mouseY < swBtn1Y + swBtn1H;
                    fillRoundedRect(ctx, swBtn1X, swBtn1Y, swBtn1W, swBtn1H, 3,
                            hov1 ? COLOR_BTN_GREEN_H : COLOR_BTN_GREEN);
                    String s = "Start";
                    ctx.drawText(tr, s, swBtn1X + (swBtn1W - tr.getWidth(s)) / 2,
                            swBtn1Y + (swBtn1H - tr.fontHeight) / 2, 0xFFE5E5E5, false);
                }
                case RUNNING -> {
                    int lapBtnW = 36;
                    int totalBW = btnW * 2 + lapBtnW + btnGap * 2;
                    swBtn1X = winX + CLOCK_CX_OFF - totalBW / 2;
                    swLapBtnX = swBtn1X + btnW + btnGap;
                    swLapBtnY = btnY; swLapBtnW = lapBtnW; swLapBtnH = btnH;
                    swBtn2X = swLapBtnX + lapBtnW + btnGap;
                    swBtn2Y = btnY; swBtn2W = btnW; swBtn2H = btnH;
                    boolean hov1 = mouseX >= swBtn1X && mouseX < swBtn1X + swBtn1W
                                && mouseY >= swBtn1Y && mouseY < swBtn1Y + swBtn1H;
                    boolean hovLap = mouseX >= swLapBtnX && mouseX < swLapBtnX + swLapBtnW
                                  && mouseY >= swLapBtnY && mouseY < swLapBtnY + swLapBtnH;
                    boolean hov2 = mouseX >= swBtn2X && mouseX < swBtn2X + swBtn2W
                                && mouseY >= swBtn2Y && mouseY < swBtn2Y + swBtn2H;
                    fillRoundedRect(ctx, swBtn1X, swBtn1Y, swBtn1W, swBtn1H, 3,
                            hov1 ? COLOR_BTN_RED_H : COLOR_BTN_RED);
                    String s1 = "Stop";
                    ctx.drawText(tr, s1, swBtn1X + (swBtn1W - tr.getWidth(s1)) / 2,
                            swBtn1Y + (swBtn1H - tr.fontHeight) / 2, 0xFFE5E5E5, false);
                    fillRoundedRect(ctx, swLapBtnX, swLapBtnY, swLapBtnW, swLapBtnH, 3,
                            hovLap ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
                    String sLap = "Lap";
                    ctx.drawText(tr, sLap, swLapBtnX + (swLapBtnW - tr.getWidth(sLap)) / 2,
                            swLapBtnY + (swLapBtnH - tr.fontHeight) / 2, 0xFFE5E5E5, false);
                    fillRoundedRect(ctx, swBtn2X, swBtn2Y, swBtn2W, swBtn2H, 3,
                            hov2 ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
                    String s2 = "Reset";
                    ctx.drawText(tr, s2, swBtn2X + (swBtn2W - tr.getWidth(s2)) / 2,
                            swBtn2Y + (swBtn2H - tr.fontHeight) / 2, 0xFFE5E5E5, false);
                }
                case STOPPED -> {
                    int totalBW = btnW * 2 + btnGap;
                    swBtn1X = winX + CLOCK_CX_OFF - totalBW / 2;
                    swBtn2X = swBtn1X + btnW + btnGap;
                    swBtn2Y = btnY; swBtn2W = btnW; swBtn2H = btnH;
                    boolean hov1 = mouseX >= swBtn1X && mouseX < swBtn1X + swBtn1W
                                && mouseY >= swBtn1Y && mouseY < swBtn1Y + swBtn1H;
                    boolean hov2 = mouseX >= swBtn2X && mouseX < swBtn2X + swBtn2W
                                && mouseY >= swBtn2Y && mouseY < swBtn2Y + swBtn2H;
                    fillRoundedRect(ctx, swBtn1X, swBtn1Y, swBtn1W, swBtn1H, 3,
                            hov1 ? COLOR_BTN_PAUSE_H : COLOR_BTN_PAUSE);
                    String s1 = "Resume";
                    ctx.drawText(tr, s1, swBtn1X + (swBtn1W - tr.getWidth(s1)) / 2,
                            swBtn1Y + (swBtn1H - tr.fontHeight) / 2, 0xFFE5E5E5, false);
                    fillRoundedRect(ctx, swBtn2X, swBtn2Y, swBtn2W, swBtn2H, 3,
                            hov2 ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
                    String s2 = "Reset";
                    ctx.drawText(tr, s2, swBtn2X + (swBtn2W - tr.getWidth(s2)) / 2,
                            swBtn2Y + (swBtn2H - tr.fontHeight) / 2, 0xFFE5E5E5, false);
                }
            }
        }

        // === Right panel ===
        int rpX  = winX + CLOCK_PANEL_W + 2;
        int rpW  = WIN_W - CLOCK_PANEL_W - 2;
        int rpCX = rpX + rpW / 2;

        if (currentMode == Mode.TIMER) {
            renderTimerRightPanel(ctx, tr, rpX, rpW, rpCX, contentBaseY, mouseX, mouseY);
        } else {
            renderStopwatchRightPanel(ctx, tr, rpX, rpW, rpCX, contentBaseY);
        }

        // Keybind reference card
        if (keybindCardVisible) {
            int cardW = 160;
            int cardX = Math.max(winX, helpBtnX + TB_W - cardW);
            int cardY2 = tbY + TB_H + 2;
            String[][] rows = {
                {"Start/Pause", "Space"},
                {"Reset", "R"},
                {"Lap (Stopwatch)", "L"},
                {"Switch Mode", "Tab"},
            };
            int cardH = rows.length * MENU_ITEM_H + SEPARATOR_H + MENU_ITEM_H + 4;
            fillRoundedRect(ctx, cardX - 1, cardY2 - 1, cardW + 2, cardH + 2, 4, COLOR_MENU_BORDER);
            fillRoundedRect(ctx, cardX, cardY2, cardW, cardH, 3, COLOR_MENU_BG);
            int iy = cardY2 + 2;
            for (String[] row : rows) {
                boolean hov = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
                if (hov) fillRoundedRect(ctx, cardX + 1, iy, cardW - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
                ctx.drawText(tr, row[0], cardX + 6, iy + 3, 0xFFE5E5E5, false);
                int kbW = tr.getWidth(row[1]);
                ctx.drawText(tr, row[1], cardX + cardW - 6 - kbW, iy + 3, 0xFF6B7099, false);
                iy += MENU_ITEM_H;
            }
            ctx.fill(cardX + 6, iy + 3, cardX + cardW - 6, iy + 4, COLOR_MENU_BORDER);
            iy += SEPARATOR_H;
            boolean hovTour = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
            if (hovTour) fillRoundedRect(ctx, cardX + 1, iy, cardW - 2, MENU_ITEM_H, 2, COLOR_MENU_HOVER);
            ctx.drawText(tr, "Start guided tour \u2192", cardX + 6, iy + 3, 0xFF0A84FF, false);
        }
    }

    private void renderTimerRightPanel(DrawContext ctx, TextRenderer tr,
                                        int rpX, int rpW, int rpCX, int baseY,
                                        int mouseX, int mouseY) {
        if (state == State.IDLE) {
            String setLabel = "Set Timer";
            ctx.drawText(tr, setLabel,
                    rpCX - tr.getWidth(setLabel) / 2,
                    baseY + 10, COLOR_LABEL, false);

            String display = String.format("%d%d:%d%d:%d%d",
                    digits[0], digits[1], digits[2], digits[3], digits[4], digits[5]);
            int displayW = tr.getWidth(display);
            int drawX = rpX + (rpW - displayW * 2) / 2;
            int drawY = baseY + 32;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(drawX, drawY);
            ctx.getMatrices().scale(2f, 2f);
            ctx.drawText(tr, display, 0, 0, 0xFFE5E5E5, false);
            ctx.getMatrices().popMatrix();

            String hint = getTotalSeconds() > 0 ? "Enter to start" : "Type digits";
            ctx.drawText(tr, hint,
                    rpCX - tr.getWidth(hint) / 2,
                    baseY + 80, COLOR_LABEL, false);

        } else if (state == State.ACTIVE || state == State.PAUSED) {
            String remLabel = (state == State.PAUSED) ? "Paused" : "Remaining";
            ctx.drawText(tr, remLabel,
                    rpCX - tr.getWidth(remLabel) / 2,
                    baseY + 10, COLOR_LABEL, false);

            long remainMs = Math.max(0, durationMs - getEffectiveElapsedMs());
            int  totalSec = (int)(remainMs / 1000);
            int  hh       = totalSec / 3600;
            int  mm       = (totalSec % 3600) / 60;
            int  ss       = totalSec % 60;
            String countStr  = String.format("%02d:%02d:%02d", hh, mm, ss);
            int   displayW   = tr.getWidth(countStr);
            int   drawX      = rpX + (rpW - displayW * 2) / 2;
            int   drawY      = baseY + 32;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(drawX, drawY);
            ctx.getMatrices().scale(2f, 2f);
            ctx.drawText(tr, countStr, 0, 0, 0xFFE5E5E5, false);
            ctx.getMatrices().popMatrix();

        } else { // COMPLETE
            String completeMsg = "Timer complete!";
            int cmY = baseY + 40;
            ctx.drawText(tr, completeMsg,
                    rpCX - tr.getWidth(completeMsg) / 2,
                    cmY, COLOR_COMPLETE, false);

            repeatW = 90;
            repeatH = 16;
            repeatX = rpCX - repeatW / 2;
            repeatY = cmY + 24;
            boolean hoverRepeat = mouseX >= repeatX && mouseX < repeatX + repeatW
                               && mouseY >= repeatY && mouseY < repeatY + repeatH;
            fillRoundedRect(ctx, repeatX, repeatY, repeatW, repeatH, 3,
                    hoverRepeat ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
            String repeatLabel = "Repeat";
            ctx.drawText(tr, repeatLabel,
                    repeatX + (repeatW - tr.getWidth(repeatLabel)) / 2,
                    repeatY + (repeatH - tr.fontHeight) / 2,
                    0xFFE5E5E5, false);
        }
    }

    private void renderStopwatchRightPanel(DrawContext ctx, TextRenderer tr,
                                            int rpX, int rpW, int rpCX, int baseY) {
        ctx.drawText(tr, "Elapsed",
                rpCX - tr.getWidth("Elapsed") / 2,
                baseY + 10, COLOR_LABEL, false);

        long elapsed = getSwElapsedMs();
        int cs  = (int)(elapsed /       10) % 100;
        int ss  = (int)(elapsed /     1000) % 60;
        int mm  = (int)(elapsed /    60000) % 60;
        int hh  = (int)(elapsed / 3600000L);
        String display = String.format("%02d:%02d:%02d.%02d", hh, mm, ss, cs);
        int displayW = tr.getWidth(display);
        int drawX = rpX + (rpW - displayW * 2) / 2;
        int drawY = baseY + 30;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(drawX, drawY);
        ctx.getMatrices().scale(2f, 2f);
        ctx.drawText(tr, display, 0, 0, 0xFFE5E5E5, false);
        ctx.getMatrices().popMatrix();

        if (swLaps.isEmpty()) {
            String hint = switch (swState) {
                case IDLE    -> "Enter to start";
                case RUNNING -> "Enter to stop";
                case STOPPED -> "Enter to resume";
            };
            ctx.drawText(tr, hint, rpCX - tr.getWidth(hint) / 2, baseY + 80, COLOR_LABEL, false);
        } else {
            // Render lap list
            int lapY = baseY + 60;
            int lapH = 10;
            int maxLaps = (winY + WIN_H - 26 - lapY) / lapH;
            int startIdx = Math.max(0, Math.min(lapScrollOffset, swLaps.size() - maxLaps));
            for (int i = startIdx; i < swLaps.size() && (lapY + lapH) < winY + WIN_H - 22; i++) {
                long lapTime = swLaps.get(i);
                long prev = (i > 0) ? swLaps.get(i - 1) : 0;
                long split = lapTime - prev;
                String lapStr = String.format("L%d %s", i + 1, formatMs(lapTime));
                String splitStr = "+" + formatMsShort(split);
                ctx.drawText(tr, lapStr, rpX + 4, lapY, 0xFFE5E5E5, false);
                ctx.drawText(tr, splitStr, rpX + rpW - tr.getWidth(splitStr) - 4, lapY, 0xFF6B7099, false);
                lapY += lapH;
            }
        }
    }

    private static String formatMs(long ms) {
        int cs = (int)(ms / 10) % 100;
        int s  = (int)(ms / 1000) % 60;
        int m  = (int)(ms / 60000) % 60;
        int h  = (int)(ms / 3600000L);
        return h > 0 ? String.format("%d:%02d:%02d.%02d", h, m, s, cs)
                      : String.format("%02d:%02d.%02d", m, s, cs);
    }

    private static String formatMsShort(long ms) {
        int cs = (int)(ms / 10) % 100;
        int s  = (int)(ms / 1000) % 60;
        int m  = (int)(ms / 60000) % 60;
        return String.format("%02d:%02d.%02d", m, s, cs);
    }

    // -------------------------------------------------------------------------
    // Keyboard input
    // -------------------------------------------------------------------------
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        // ESC closes timer
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (keybindCardVisible) { keybindCardVisible = false; return true; }
            this.close();
            return true;
        }

        if (currentMode == Mode.STOPWATCH) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                switch (swState) {
                    case IDLE, STOPPED -> { swStartMs = System.currentTimeMillis(); swState = SwState.RUNNING; }
                    case RUNNING       -> { swElapsedMs = getSwElapsedMs(); swState = SwState.STOPPED; }
                }
                syncToHud();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                swState = SwState.IDLE; swElapsedMs = 0; swStartMs = 0;
                swLaps.clear(); lapScrollOffset = 0;
                syncToHud();
                return true;
            }
            return false;
        }

        // TIMER mode
        if (state == State.IDLE) {
            if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                pushDigit(keyCode - GLFW.GLFW_KEY_0); return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
                pushDigit(keyCode - GLFW.GLFW_KEY_KP_0); return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { popDigit(); return true; }
            if (keyCode == GLFW.GLFW_KEY_DELETE)    { Arrays.fill(digits, 0); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (getTotalSeconds() > 0) { startTimer(); syncToHud(); }
                return true;
            }
        }
        return false;
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
        int tbY = winY + (TITLE_BAR_H - TB_H) / 2;
        int closeBtnX = winX + 4;
        int pinBtnX = winX + WIN_W - 4 - TB_W;
        int helpBtnX = pinBtnX - TB_GAP - TB_W;
        boolean inTbRow = my >= tbY && my < tbY + TB_H;

        // Close [×]
        if (inTbRow && mx >= closeBtnX && mx < closeBtnX + TB_W) {
            if (closeCallback != null) closeCallback.run();
            return true;
        }
        // Keybind card click
        if (keybindCardVisible) {
            keybindCardVisible = false;
            int cardW = 160, cardX = Math.max(winX, helpBtnX + TB_W - cardW);
            int cardY2 = tbY + TB_H + 2;
            int cardH = 4 * MENU_ITEM_H + SEPARATOR_H + MENU_ITEM_H + 4;
            if (mx >= cardX && mx < cardX + cardW && my >= cardY2 && my < cardY2 + cardH) {
                int tourY = cardY2 + 2 + 4 * MENU_ITEM_H + SEPARATOR_H;
                if (my >= tourY && my < tourY + MENU_ITEM_H) {
                    net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                    if (mc.currentScreen instanceof IgCalcOverlay ov) {
                        com.igcalc.config.IgCalcTutorialState.reset();
                        ov.startTutorialFiltered(TutorialScreen.TIMER_STEPS);
                    }
                }
            }
            return true;
        }
        // Help [?]
        if (inTbRow && mx >= helpBtnX && mx < helpBtnX + TB_W) {
            keybindCardVisible = !keybindCardVisible;
            return true;
        }
        // Pin [⚑]
        if (inTbRow && mx >= pinBtnX && mx < pinBtnX + TB_W) {
            if (HudRenderer.INSTANCE.pinnedTimer) {
                HudRenderer.INSTANCE.pinnedTimer = false;
                com.igcalc.config.IgCalcHudState.timerHudPinned = false;
                com.igcalc.config.IgCalcHudState.save();
            } else {
                HudRenderer.INSTANCE.pinTimer(this);
            }
            return true;
        }

        // Ignore clicks outside window
        if (mx < winX || mx > winX + WIN_W || my < winY || my > winY + WIN_H) return false;

        // Title bar drag (between close and right buttons)
        if (my >= winY && my < winY + TITLE_BAR_H && mx >= winX + 20 && mx < winX + WIN_W - 36) {
            dragging = true;
            dragOffX = mx - winX;
            dragOffY = my - winY;
            return true;
        }

        // Tab bar clicks
        int tabY = winY + TITLE_BAR_H;
        if (my >= tabY && my < tabY + TAB_BAR_H) {
            if (mx >= winX + 8  && mx < winX + 62)  { currentMode = Mode.TIMER;     syncToHud(); return true; }
            if (mx >= winX + 66 && mx < winX + 148) { currentMode = Mode.STOPWATCH; syncToHud(); return true; }
        }

        if (currentMode == Mode.STOPWATCH) {
            // Stopwatch btn1 (Start / Stop / Resume)
            if (swBtn1X >= 0 && mx >= swBtn1X && mx < swBtn1X + swBtn1W
             && my >= swBtn1Y && my < swBtn1Y + swBtn1H) {
                switch (swState) {
                    case IDLE, STOPPED -> { swStartMs = System.currentTimeMillis(); swState = SwState.RUNNING; }
                    case RUNNING       -> { swElapsedMs = getSwElapsedMs(); swState = SwState.STOPPED; }
                }
                syncToHud();
                return true;
            }
            // Lap button (only when RUNNING)
            if (swLapBtnX >= 0 && swState == SwState.RUNNING
             && mx >= swLapBtnX && mx < swLapBtnX + swLapBtnW
             && my >= swLapBtnY && my < swLapBtnY + swLapBtnH) {
                swLaps.add(getSwElapsedMs());
                syncToHud();
                return true;
            }
            // Stopwatch btn2 (Reset)
            if (swBtn2X >= 0 && mx >= swBtn2X && mx < swBtn2X + swBtn2W
             && my >= swBtn2Y && my < swBtn2Y + swBtn2H) {
                swState = SwState.IDLE;
                swElapsedMs = 0;
                swStartMs   = 0;
                swLaps.clear();
                lapScrollOffset = 0;
                syncToHud();
                return true;
            }
            return true; // consume all clicks inside window
        }

        // TIMER mode clicks
        boolean timerRunning = (state == State.ACTIVE || state == State.PAUSED);

        // Cancel button
        if (mx >= cancelX && mx < cancelX + cancelW && my >= cancelY && my < cancelY + cancelH) {
            state = State.IDLE;
            Arrays.fill(digits, 0);
            syncToHud();
            return true;
        }

        // Pause/Resume button
        if (timerRunning && mx >= pauseX && mx < pauseX + pauseW && my >= pauseY && my < pauseY + pauseH) {
            if (state == State.ACTIVE) pauseTimer();
            else                       resumeTimer();
            syncToHud();
            return true;
        }

        // Complete state
        if (state == State.COMPLETE) {
            if (repeatW > 0 && mx >= repeatX && mx < repeatX + repeatW && my >= repeatY && my < repeatY + repeatH) {
                digits = lastDigits.clone();
                startTimer();
            } else {
                state = State.IDLE;
                Arrays.fill(digits, 0);
            }
            syncToHud();
            return true;
        }

        return true; // consume all clicks inside window
    }

    @Override
    public boolean mouseDragged(Click click, double dX, double dY) {
        if (!dragging) return false;
        int mx = (int) click.x();
        int my = (int) click.y();
        winX = Math.max(0, Math.min(mx - dragOffX, this.width  - WIN_W));
        winY = Math.max(0, Math.min(my - dragOffY, this.height - WIN_H));
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            IgCalcWindowState.timerX = winX;
            IgCalcWindowState.timerY = winY;
            IgCalcWindowState.save();
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (currentMode == Mode.STOPWATCH && !swLaps.isEmpty()) {
            lapScrollOffset = Math.max(0, lapScrollOffset - (int) vAmount);
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    // -------------------------------------------------------------------------
    // Lifecycle — sync stopwatch state to HUD on close
    // -------------------------------------------------------------------------
    @Override
    public void removed() {
        super.removed();
        swElapsedMs = getSwElapsedMs();
        syncToHud();
    }

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------
    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        IgCalcWindowState.timerX = winX;
        IgCalcWindowState.timerY = winY;
        IgCalcWindowState.save();
        if (closeCallback != null) closeCallback.run();
        else super.close();
    }
}
