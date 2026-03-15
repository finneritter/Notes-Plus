package com.igcalc.gui;

import com.igcalc.config.IgCalcHudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Visual HUD configuration screen. Users can drag and resize both HUD panes,
 * choose scale presets, and adjust opacity.
 */
public class HudConfigScreen extends Screen {

    private static final int COLOR_BG        = 0xEE1C1E2B;
    private static final int COLOR_TITLE_BAR = 0xFF262A3C;
    private static final int COLOR_TITLE     = 0xFFE5E5E5;
    private static final int COLOR_BORDER    = 0xFF3A3F52;
    private static final int COLOR_LABEL     = 0xFFB0B8D0;
    private static final int COLOR_BTN_NORM  = 0xFF2E3347;
    private static final int COLOR_BTN_HOVER = 0xFF3A4060;
    private static final int COLOR_DOT_RED   = 0xFFFF5F57;

    // Notes pane min/max
    private static final int NOTES_MIN_W = 140, NOTES_MAX_W = 400;
    private static final int NOTES_MIN_H = 90,  NOTES_MAX_H = 280;
    // Timer pane min/max
    private static final int TIMER_MIN_W = 70,  TIMER_MAX_W = 200;
    private static final int TIMER_MIN_H = 32,  TIMER_MAX_H = 80;

    private final Screen parent;

    // Working copies — committed on Done
    private int   notesX, notesY, notesW, notesH;
    private float notesOpacity;
    private int   timerX, timerY, timerW, timerH;
    private float timerOpacity;

    // Drag / resize state
    private boolean draggingNotes  = false;
    private boolean draggingTimer  = false;
    private boolean resizingNotes  = false;
    private boolean resizingTimer  = false;
    private int     dragOffX, dragOffY;
    private static final int RESIZE_CORNER = 8;

    public HudConfigScreen(Screen parent) {
        super(Text.translatable("igcalc.hud.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Load from HudState
        notesW = IgCalcHudState.notesHudW;
        notesH = IgCalcHudState.notesHudH;
        notesX = IgCalcHudState.notesHudX < 0 ? 8 : IgCalcHudState.notesHudX;
        notesY = IgCalcHudState.notesHudY < 0 ? 8 : IgCalcHudState.notesHudY;
        notesOpacity = IgCalcHudState.notesHudOpacity;

        timerW = IgCalcHudState.timerHudW;
        timerH = IgCalcHudState.timerHudH;
        timerX = IgCalcHudState.timerHudX < 0 ? sw - timerW - 8 : IgCalcHudState.timerHudX;
        timerY = IgCalcHudState.timerHudY < 0 ? 8 : IgCalcHudState.timerHudY;
        timerOpacity = IgCalcHudState.timerHudOpacity;

        clampPositions(sw, sh);

        // Done button
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> save())
                .dimensions(this.width / 2 - 50, this.height - 24, 100, 14).build());
    }

    private void clampPositions(int sw, int sh) {
        notesX = Math.max(0, Math.min(notesX, sw - notesW));
        notesY = Math.max(0, Math.min(notesY, sh - notesH));
        timerX = Math.max(0, Math.min(timerX, sw - timerW));
        timerY = Math.max(0, Math.min(timerY, sh - timerH));
    }

    private void save() {
        IgCalcHudState.notesHudX       = notesX;
        IgCalcHudState.notesHudY       = notesY;
        IgCalcHudState.notesHudW       = notesW;
        IgCalcHudState.notesHudH       = notesH;
        IgCalcHudState.notesHudOpacity = notesOpacity;
        IgCalcHudState.timerHudX       = timerX;
        IgCalcHudState.timerHudY       = timerY;
        IgCalcHudState.timerHudW       = timerW;
        IgCalcHudState.timerHudH       = timerH;
        IgCalcHudState.timerHudOpacity = timerOpacity;
        IgCalcHudState.save();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Dark overlay
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Instructions
        String hint = "Drag panes to reposition \u2022 S/M/L presets \u2022 opacity [ - ] [ + ]";
        ctx.drawCenteredTextWithShadow(tr, hint, this.width / 2, 6, COLOR_LABEL);

        // Notes pane
        renderPane(ctx, tr, mx, my,
                notesX, notesY, notesW, notesH, notesOpacity,
                "Notes HUD",
                new int[]{180, 120, 220, 160, 300, 200},
                true);

        // Timer pane
        renderPane(ctx, tr, mx, my,
                timerX, timerY, timerW, timerH, timerOpacity,
                "Timer HUD",
                new int[]{90, 40, 110, 50, 140, 60},
                false);

        super.render(ctx, mx, my, delta);
    }

    private void renderPane(DrawContext ctx, TextRenderer tr, int mx, int my,
                             int px, int py, int pw, int ph, float opacity,
                             String label, int[] presets, boolean isNotes) {
        int bgColor = applyOpacity(0xEE1C1E2B, opacity);
        int brColor = applyOpacity(0xFF3A3F52, opacity);

        // Background
        ctx.fill(px, py, px + pw, py + ph, bgColor);
        ctx.fill(px, py, px + pw, py + 1, brColor);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, brColor);
        ctx.fill(px, py, px + 1, py + ph, brColor);
        ctx.fill(px + pw - 1, py, px + pw, py + ph, brColor);

        // Highlight on hover
        boolean hovered = mx >= px && mx < px + pw && my >= py && my < py + ph;
        if (hovered) {
            ctx.fill(px, py, px + pw, py + 1, 0xFF0A84FF);
            ctx.fill(px, py + ph - 1, px + pw, py + ph, 0xFF0A84FF);
            ctx.fill(px, py, px + 1, py + ph, 0xFF0A84FF);
            ctx.fill(px + pw - 1, py, px + pw, py + ph, 0xFF0A84FF);
        }

        // Resize corner indicator ⌟
        ctx.drawText(tr, "\u231F", px + pw - 8, py + ph - 9, 0xFF5C6380, false);

        // Label
        ctx.drawCenteredTextWithShadow(tr, label, px + pw / 2, py + (ph - tr.fontHeight) / 2, COLOR_TITLE);

        // Controls below pane
        int ctrlY = py + ph + 4;

        // Scale presets S/M/L
        String[] btnLabels = {"S", "M", "L"};
        int btnW = 14, btnH = 12, gap = 3;
        int totalPresetW = btnW * 3 + gap * 2;
        int presetX = px + (pw - totalPresetW) / 2;
        for (int i = 0; i < 3; i++) {
            int bx = presetX + i * (btnW + gap);
            boolean hov = mx >= bx && mx < bx + btnW && my >= ctrlY && my < ctrlY + btnH;
            ctx.fill(bx, ctrlY, bx + btnW, ctrlY + btnH, hov ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
            ctx.drawCenteredTextWithShadow(tr, btnLabels[i], bx + btnW / 2, ctrlY + (btnH - tr.fontHeight) / 2, 0xFFE5E5E5);
        }

        // Opacity controls
        int opCtrlY = ctrlY + btnH + 3;
        String opStr = String.format("Opacity: %.0f%%", opacity * 100);
        ctx.drawCenteredTextWithShadow(tr, opStr, px + pw / 2, opCtrlY, COLOR_LABEL);
        int opBtnY = opCtrlY + tr.fontHeight + 2;
        int minusBx = px + pw / 2 - 20;
        int plusBx  = px + pw / 2 + 6;
        boolean hMinus = mx >= minusBx && mx < minusBx + 14 && my >= opBtnY && my < opBtnY + 12;
        boolean hPlus  = mx >= plusBx  && mx < plusBx  + 14 && my >= opBtnY && my < opBtnY + 12;
        ctx.fill(minusBx, opBtnY, minusBx + 14, opBtnY + 12, hMinus ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
        ctx.drawCenteredTextWithShadow(tr, "-", minusBx + 7, opBtnY + 2, 0xFFE5E5E5);
        ctx.fill(plusBx, opBtnY, plusBx + 14, opBtnY + 12, hPlus ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
        ctx.drawCenteredTextWithShadow(tr, "+", plusBx + 7, opBtnY + 2, 0xFFE5E5E5);
    }

    @Override
    public boolean mouseClicked(Click click, boolean propagated) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int sw = this.width, sh = this.height;

        // Check preset buttons and opacity buttons for notes
        if (handlePaneClicks(mx, my, true)) return true;
        if (handlePaneClicks(mx, my, false)) return true;

        // Check resize corners first
        if (mx >= notesX + notesW - RESIZE_CORNER && mx < notesX + notesW
         && my >= notesY + notesH - RESIZE_CORNER && my < notesY + notesH) {
            resizingNotes = true;
            dragOffX = mx;
            dragOffY = my;
            return true;
        }
        if (mx >= timerX + timerW - RESIZE_CORNER && mx < timerX + timerW
         && my >= timerY + timerH - RESIZE_CORNER && my < timerY + timerH) {
            resizingTimer = true;
            dragOffX = mx;
            dragOffY = my;
            return true;
        }

        // Start dragging panes
        if (mx >= notesX && mx < notesX + notesW && my >= notesY && my < notesY + notesH) {
            draggingNotes = true;
            dragOffX = mx - notesX;
            dragOffY = my - notesY;
            return true;
        }
        if (mx >= timerX && mx < timerX + timerW && my >= timerY && my < timerY + timerH) {
            draggingTimer = true;
            dragOffX = mx - timerX;
            dragOffY = my - timerY;
            return true;
        }

        return super.mouseClicked(click, propagated);
    }

    private boolean handlePaneClicks(int mx, int my, boolean isNotes) {
        int px = isNotes ? notesX : timerX;
        int py = isNotes ? notesY : timerY;
        int pw = isNotes ? notesW : timerW;
        int ph = isNotes ? notesH : timerH;
        int[] presets = isNotes
                ? new int[]{180, 120, 220, 160, 300, 200}
                : new int[]{90, 40, 110, 50, 140, 60};
        float opacity = isNotes ? notesOpacity : timerOpacity;

        int ctrlY = py + ph + 4;
        int btnW = 14, btnH = 12, gap = 3;
        int totalPresetW = btnW * 3 + gap * 2;
        int presetX = px + (pw - totalPresetW) / 2;

        for (int i = 0; i < 3; i++) {
            int bx = presetX + i * (btnW + gap);
            if (mx >= bx && mx < bx + btnW && my >= ctrlY && my < ctrlY + btnH) {
                int newW = presets[i * 2];
                int newH = presets[i * 2 + 1];
                if (isNotes) { notesW = newW; notesH = newH; }
                else         { timerW = newW; timerH = newH; }
                clampPositions(this.width, this.height);
                return true;
            }
        }

        int opCtrlY = ctrlY + btnH + 3;
        int opBtnY = opCtrlY + MinecraftClient.getInstance().textRenderer.fontHeight + 2;
        int minusBx = px + pw / 2 - 20;
        int plusBx  = px + pw / 2 + 6;

        if (my >= opBtnY && my < opBtnY + 12) {
            if (mx >= minusBx && mx < minusBx + 14) {
                float newOp = Math.max(0.1f, opacity - 0.05f);
                if (isNotes) notesOpacity = newOp; else timerOpacity = newOp;
                return true;
            }
            if (mx >= plusBx && mx < plusBx + 14) {
                float newOp = Math.min(1.0f, opacity + 0.05f);
                if (isNotes) notesOpacity = newOp; else timerOpacity = newOp;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dX, double dY) {
        int mx = (int) click.x();
        int my = (int) click.y();
        if (resizingNotes) {
            notesW = Math.max(NOTES_MIN_W, Math.min(NOTES_MAX_W, notesW + (mx - dragOffX)));
            notesH = Math.max(NOTES_MIN_H, Math.min(NOTES_MAX_H, notesH + (my - dragOffY)));
            dragOffX = mx;
            dragOffY = my;
            clampPositions(this.width, this.height);
            return true;
        }
        if (resizingTimer) {
            timerW = Math.max(TIMER_MIN_W, Math.min(TIMER_MAX_W, timerW + (mx - dragOffX)));
            timerH = Math.max(TIMER_MIN_H, Math.min(TIMER_MAX_H, timerH + (my - dragOffY)));
            dragOffX = mx;
            dragOffY = my;
            clampPositions(this.width, this.height);
            return true;
        }
        if (draggingNotes) {
            notesX = Math.max(0, Math.min(mx - dragOffX, this.width  - notesW));
            notesY = Math.max(0, Math.min(my - dragOffY, this.height - notesH));
            return true;
        }
        if (draggingTimer) {
            timerX = Math.max(0, Math.min(mx - dragOffX, this.width  - timerW));
            timerY = Math.max(0, Math.min(my - dragOffY, this.height - timerH));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingNotes  = false;
        draggingTimer  = false;
        resizingNotes  = false;
        resizingTimer  = false;
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256) { client.setScreen(parent); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static int applyOpacity(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, (int)(((color >>> 24) & 0xFF) * opacity)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
