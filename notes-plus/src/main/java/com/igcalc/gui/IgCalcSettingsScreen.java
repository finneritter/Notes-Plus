package com.igcalc.gui;

import com.igcalc.IgCalcClient;
import com.igcalc.config.IgCalcConfig;
import com.igcalc.config.IgCalcHudState;
import com.igcalc.config.IgCalcTutorialState;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class IgCalcSettingsScreen extends Screen {

    private static final int WIN_W            = 300;
    private static final int TOTAL_H         = 888;  // total content height

    // Offset from winY where the color TextFieldWidgets start
    // = TITLE_BAR_HEIGHT(18) + 10 + 8*(ROW_H+ROW_PAD)(192) + divider(10) + 5*(ROW_H+ROW_PAD)(120) + divider(10) + label(12)
    private static final int COLOR_SECTION_OFFSET = 348;
    private static final int TITLE_BAR_HEIGHT = 18;
    private static final int ROW_H            = 20;
    private static final int ROW_PAD          = 4;

    private static final int COLOR_BG        = 0xEE1C1E2B;
    private static final int COLOR_TITLE_BAR = 0xFF262A3C;
    private static final int COLOR_TITLE     = 0xFFE5E5E5;
    private static final int COLOR_BORDER    = 0xFF3A3F52;
    private static final int COLOR_DOT_RED   = 0xFFFF5F57;
    private static final int COLOR_LABEL     = 0xFFB0B8D0;
    private static final int COLOR_BTN_NORM  = 0xFF2E3347;
    private static final int COLOR_BTN_HOVER = 0xFF3A4060;
    private static final int COLOR_BTN_CAP   = 0xFF0A84FF;
    private static final int COLOR_BTN_TEXT  = 0xFFE5E5E5;
    private static final int COLOR_READONLY  = 0xFF5C6380;

    private final Screen parent;
    private int winX, winY;
    private int scrollOffset = 0;

    // Action currently waiting for a key press
    private String capturingFor = null;
    // Original binding before capture started (for ESC cancel)
    private IgCalcConfig.HotkeyBinding capturingOriginal = null;

    // Keybind keys (order must match KEYBIND_KEYS)
    private static final String[] KEYBIND_KEYS = {
        "keyNewNote", "keyFindSidebar", "keyQuickNote", "keyInsertCoords", "keyCopyClipboard", "keyStopwatchToggle", "keyStopwatchReset"
    };

    // Editable copies of config bindings
    private IgCalcConfig.HotkeyBinding bndNewNote;
    private IgCalcConfig.HotkeyBinding bndFindSidebar;
    private IgCalcConfig.HotkeyBinding bndQuickNote;
    private IgCalcConfig.HotkeyBinding bndInsertCoords;
    private IgCalcConfig.HotkeyBinding bndCopyClipboard;
    private IgCalcConfig.HotkeyBinding bndStopwatchToggle;
    private IgCalcConfig.HotkeyBinding bndStopwatchReset;

    private IgCalcConfig.HotkeyBinding getBinding(String key) {
        return switch (key) {
            case "keyNewNote"          -> bndNewNote;
            case "keyFindSidebar"      -> bndFindSidebar;
            case "keyQuickNote"        -> bndQuickNote;
            case "keyInsertCoords"     -> bndInsertCoords;
            case "keyCopyClipboard"    -> bndCopyClipboard;
            case "keyStopwatchToggle"  -> bndStopwatchToggle;
            case "keyStopwatchReset"   -> bndStopwatchReset;
            default -> null;
        };
    }

    private void setBinding(String key, IgCalcConfig.HotkeyBinding b) {
        switch (key) {
            case "keyNewNote"          -> bndNewNote          = b;
            case "keyFindSidebar"      -> bndFindSidebar      = b;
            case "keyQuickNote"        -> bndQuickNote        = b;
            case "keyInsertCoords"     -> bndInsertCoords     = b;
            case "keyCopyClipboard"    -> bndCopyClipboard    = b;
            case "keyStopwatchToggle"  -> bndStopwatchToggle  = b;
            case "keyStopwatchReset"   -> bndStopwatchReset   = b;
        }
    }

    private static IgCalcConfig.HotkeyBinding getDefault(String key) {
        return switch (key) {
            case "keyNewNote"          -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_N, true, false);
            case "keyFindSidebar"      -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_F, true, true);
            case "keyQuickNote"        -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_N, true, true);
            case "keyInsertCoords"     -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_G, true, true);
            case "keyCopyClipboard"    -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_E, true, true);
            case "keyStopwatchToggle"  -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_T, true, true);
            case "keyStopwatchReset"   -> new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_R, true, true);
            default -> null;
        };
    }

    // Context menu action toggles
    private Set<String> editEnabledActions;

    // Color text fields
    private TextFieldWidget colorBgField, colorTitleBarField, colorAccentField,
                            colorSidebarBgField, colorSidebarSelectField, colorBorderField;

    // Color picker popup state
    private boolean colorPickerOpen  = false;
    private int     colorPickerIndex = -1;
    private int     pickerX, pickerY;
    private float   pickerHue = 0f, pickerSat = 1f, pickerBri = 1f;
    private static final int PICKER_W = 120, PICKER_H = 100;
    private static final int HUE_BAR_H = 10;

    public IgCalcSettingsScreen(Screen parent) {
        super(Text.literal("Notes+ Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        IgCalcConfig cfg = IgCalcConfig.getInstance();
        cfg.load();
        bndNewNote       = cfg.keyNewNote;
        bndFindSidebar   = cfg.keyFindSidebar;
        bndQuickNote     = cfg.keyQuickNote;
        bndInsertCoords  = cfg.keyInsertCoords;
        bndCopyClipboard   = cfg.keyCopyClipboard;
        bndStopwatchToggle = cfg.keyStopwatchToggle;
        bndStopwatchReset  = cfg.keyStopwatchReset;
        editEnabledActions = new HashSet<>(cfg.enabledContextActions);

        int visH = Math.min(TOTAL_H, this.height - 10);
        winX = (this.width  - WIN_W) / 2;
        winY = (this.height - visH) / 2;
        scrollOffset = 0;

        addDrawableChild(ButtonWidget.builder(Text.translatable("igcalc.config.save"), btn -> {
            applyAndSave();
            close();
        }).dimensions(winX + WIN_W - 112, winY + visH - 16, 50, 12).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("igcalc.config.reset"), btn -> {
            resetDefaults();
        }).dimensions(winX + WIN_W - 58, winY + visH - 16, 52, 12).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Replay Tutorial"), btn -> {
            IgCalcTutorialState.reset();
            IgCalcOverlay o = new IgCalcOverlay(false, false, false);
            client.setScreen(o);
            o.startTutorial(0);
        }).dimensions(winX + 12, winY + visH - 50, WIN_W - 24, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("igcalc.hud.config.title"), btn -> {
            client.setScreen(new HudConfigScreen(this));
        }).dimensions(winX + 12, winY + visH - 32, WIN_W - 24, 14).build());

        // Color TextFieldWidgets (positioned based on scroll in render)
        int tfwX = winX + WIN_W - 90 + 16;
        int tfwW = 84 - 16;
        int tfwH = ROW_H - 4;
        String[] hexDefaults = {
            String.format("%08x", cfg.colorBg),
            String.format("%08x", cfg.colorTitleBar),
            String.format("%08x", cfg.colorAccent),
            String.format("%08x", cfg.colorSidebarBg),
            String.format("%08x", cfg.colorSidebarSelect),
            String.format("%08x", cfg.colorBorder)
        };
        TextFieldWidget[] fields = new TextFieldWidget[6];
        for (int k = 0; k < 6; k++) {
            int fy = winY + COLOR_SECTION_OFFSET + k * (ROW_H + ROW_PAD) + 2 - scrollOffset;
            fields[k] = new TextFieldWidget(textRenderer, tfwX, fy, tfwW, tfwH, Text.empty());
            fields[k].setMaxLength(8);
            fields[k].setText(hexDefaults[k]);
            addDrawableChild(fields[k]);
        }
        colorBgField            = fields[0];
        colorTitleBarField      = fields[1];
        colorAccentField        = fields[2];
        colorSidebarBgField     = fields[3];
        colorSidebarSelectField = fields[4];
        colorBorderField        = fields[5];
    }

    private void resetDefaults() {
        bndNewNote       = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_N, true, false);
        bndFindSidebar   = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_F, true, true);
        bndQuickNote     = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_N, true, true);
        bndInsertCoords  = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_G, true, true);
        bndCopyClipboard   = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_E, true, true);
        bndStopwatchToggle = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_T, true, true);
        bndStopwatchReset  = new IgCalcConfig.HotkeyBinding(GLFW.GLFW_KEY_R, true, true);
        capturingFor   = null;
        editEnabledActions = new HashSet<>(IgCalcConfig.allContextActions());
        colorBgField           .setText("ee1c1e2b");
        colorTitleBarField     .setText("ff262a3c");
        colorAccentField       .setText("ff0a84ff");
        colorSidebarBgField    .setText("ff171a27");
        colorSidebarSelectField.setText("ff2a4070");
        colorBorderField       .setText("ff3a3f52");
    }

    private void applyAndSave() {
        IgCalcConfig cfg = IgCalcConfig.getInstance();
        cfg.keyNewNote       = bndNewNote;
        cfg.keyFindSidebar   = bndFindSidebar;
        cfg.keyQuickNote     = bndQuickNote;
        cfg.keyInsertCoords  = bndInsertCoords;
        cfg.keyCopyClipboard    = bndCopyClipboard;
        cfg.keyStopwatchToggle  = bndStopwatchToggle;
        cfg.keyStopwatchReset   = bndStopwatchReset;
        cfg.enabledContextActions = new HashSet<>(editEnabledActions);
        cfg.colorBg            = parseHex(colorBgField.getText(),            cfg.colorBg);
        cfg.colorTitleBar      = parseHex(colorTitleBarField.getText(),      cfg.colorTitleBar);
        cfg.colorAccent        = parseHex(colorAccentField.getText(),        cfg.colorAccent);
        cfg.colorSidebarBg     = parseHex(colorSidebarBgField.getText(),     cfg.colorSidebarBg);
        cfg.colorSidebarSelect = parseHex(colorSidebarSelectField.getText(), cfg.colorSidebarSelect);
        cfg.colorBorder        = parseHex(colorBorderField.getText(),        cfg.colorBorder);
        cfg.save();
    }

    private static int parseHex(String s, int def) {
        try { return (int) Long.parseLong(s.replaceAll("^#", ""), 16); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public void close() {
        capturingFor = null;
        client.setScreen(parent);
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
    private int getVisH() {
        return Math.min(TOTAL_H, this.height - 10);
    }

    private int getMaxScroll() {
        int visH = getVisH();
        // Content below title bar: TOTAL_H - TITLE_BAR_HEIGHT - 54 (bottom buttons)
        // Visible: visH - TITLE_BAR_HEIGHT - 54
        return Math.max(0, TOTAL_H - visH);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int visH = getVisH();
        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        fillRoundedRect(ctx, winX, winY, WIN_W, visH, 6, COLOR_BG);
        fillRoundedRectTopOnly(ctx, winX, winY, WIN_W, TITLE_BAR_HEIGHT, 6, COLOR_TITLE_BAR);

        ctx.fill(winX,           winY,           winX + WIN_W, winY + 1,     COLOR_BORDER);
        ctx.fill(winX,           winY + visH - 1,winX + WIN_W, winY + visH,  COLOR_BORDER);
        ctx.fill(winX,           winY,           winX + 1,     winY + visH,  COLOR_BORDER);
        ctx.fill(winX + WIN_W-1, winY,           winX + WIN_W, winY + visH,  COLOR_BORDER);

        boolean hoverClose = mouseX >= winX + 4 && mouseX <= winX + 15
                          && mouseY >= winY + 3 && mouseY <= winY + 15;
        ctx.drawText(textRenderer, "\u00D7", winX + 5, winY + 5,
                hoverClose ? COLOR_DOT_RED : 0xFF6B7099, false);

        String titleStr = "Notes+ Configuration";
        ctx.drawText(textRenderer, titleStr,
                winX + (WIN_W - textRenderer.getWidth(titleStr)) / 2,
                winY + (TITLE_BAR_HEIGHT - textRenderer.fontHeight) / 2,
                COLOR_TITLE, false);

        // Scrollable content area — scissor to clip
        int clipTop    = winY + TITLE_BAR_HEIGHT;
        int clipBottom = winY + visH - 54;  // leave room for bottom buttons
        ctx.enableScissor(winX, clipTop, winX + WIN_W, clipBottom);

        int contentY = winY + TITLE_BAR_HEIGHT + 10 - scrollOffset;
        int labelX   = winX + 12;
        int btnX     = winX + WIN_W - 90;
        int btnW     = 84;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.newNote").getString(),
                bndNewNote, "keyNewNote");
        contentY += ROW_H + ROW_PAD;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.findSidebar").getString(),
                bndFindSidebar, "keyFindSidebar");
        contentY += ROW_H + ROW_PAD;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.quickNote").getString(),
                bndQuickNote, "keyQuickNote");
        contentY += ROW_H + ROW_PAD;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.insertCoords").getString(),
                bndInsertCoords, "keyInsertCoords");
        contentY += ROW_H + ROW_PAD;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.copyClipboard").getString(),
                bndCopyClipboard, "keyCopyClipboard");
        contentY += ROW_H + ROW_PAD;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.stopwatchToggle").getString(),
                bndStopwatchToggle, "keyStopwatchToggle");
        contentY += ROW_H + ROW_PAD;

        renderRow(ctx, mouseX, mouseY, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.stopwatchReset").getString(),
                bndStopwatchReset, "keyStopwatchReset");
        contentY += ROW_H + ROW_PAD;

        // Divider 1
        contentY += 4;
        ctx.fill(winX + 12, contentY, winX + WIN_W - 12, contentY + 1, 0xFF2E3347);
        contentY += 6;

        // Read-only rows — show actual bound key from Controls
        renderReadOnlyRow(ctx, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.openCalc").getString(),
                IgCalcClient.calculatorKey.getBoundKeyLocalizedText().getString() + " \u2014 Controls");
        contentY += ROW_H + ROW_PAD;

        renderReadOnlyRow(ctx, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.openNotes").getString(),
                IgCalcClient.notesKey.getBoundKeyLocalizedText().getString() + " \u2014 Controls");
        contentY += ROW_H + ROW_PAD;

        renderReadOnlyRow(ctx, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.openTimer").getString(),
                IgCalcClient.timerKey.getBoundKeyLocalizedText().getString() + " \u2014 Controls");
        contentY += ROW_H + ROW_PAD;

        renderReadOnlyRow(ctx, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.hudToggle").getString(),
                IgCalcClient.hudToggleKey.getBoundKeyLocalizedText().getString() + " \u2014 Controls");
        contentY += ROW_H + ROW_PAD;

        renderReadOnlyRow(ctx, contentY, labelX, btnX, btnW,
                Text.translatable("igcalc.config.key.hudInteract").getString(),
                IgCalcClient.hudInteractKey.getBoundKeyLocalizedText().getString() + " \u2014 Controls");
        contentY += ROW_H + ROW_PAD;

        // Divider 2
        contentY += 4;
        ctx.fill(winX + 12, contentY, winX + WIN_W - 12, contentY + 1, 0xFF2E3347);
        contentY += 6;

        // Colors section
        ctx.drawText(textRenderer, "Colors", labelX, contentY, 0xFFB0B8D0, false);
        contentY += 12;

        // Reposition color TextFieldWidgets based on scroll
        TextFieldWidget[] colorFields = {
            colorBgField, colorTitleBarField, colorAccentField,
            colorSidebarBgField, colorSidebarSelectField, colorBorderField
        };
        for (int k = 0; k < 6; k++) {
            int fy = winY + COLOR_SECTION_OFFSET + k * (ROW_H + ROW_PAD) + 2 - scrollOffset;
            colorFields[k].setY(fy);
            colorFields[k].visible = (fy >= clipTop && fy + (ROW_H - 4) <= clipBottom);
        }

        String[] colorLabels = {
            Text.translatable("igcalc.config.color.bg").getString(),
            Text.translatable("igcalc.config.color.titleBar").getString(),
            Text.translatable("igcalc.config.color.accent").getString(),
            Text.translatable("igcalc.config.color.sidebarBg").getString(),
            Text.translatable("igcalc.config.color.sidebarSelect").getString(),
            Text.translatable("igcalc.config.color.border").getString()
        };
        int tfwX = winX + WIN_W - 90 + 16;
        for (int k = 0; k < 6; k++) {
            ctx.drawText(textRenderer, colorLabels[k], labelX,
                    contentY + (ROW_H - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
            // Color preview square — positioned just left of text field
            int previewX = tfwX - 16;
            try {
                int previewColor = (int) Long.parseLong(colorFields[k].getText().replaceAll("^#", ""), 16);
                ctx.fill(previewX, contentY + 4, previewX + 12, contentY + 16, previewColor | 0xFF000000);
                ctx.fill(previewX, contentY + 4, previewX + 12, contentY + 5,  0x44FFFFFF);
                ctx.fill(previewX, contentY + 4, previewX + 1,  contentY + 16, 0x44FFFFFF);
            } catch (NumberFormatException ignored) {}
            contentY += ROW_H + ROW_PAD;
        }

        // Divider 3
        contentY += 4;
        ctx.fill(winX + 12, contentY, winX + WIN_W - 12, contentY + 1, 0xFF2E3347);
        contentY += 6;

        // HUD background toggle checkboxes
        ctx.drawText(textRenderer, "HUD Display", labelX, contentY, 0xFFB0B8D0, false);
        contentY += 12;

        renderCheckboxRow(ctx, mouseX, mouseY, contentY, labelX,
                "Notes HUD background", IgCalcHudState.notesHudShowBg, "notesHudShowBg");
        contentY += ROW_H + ROW_PAD;

        renderCheckboxRow(ctx, mouseX, mouseY, contentY, labelX,
                "Timer HUD background", IgCalcHudState.timerHudShowBg, "timerHudShowBg");
        contentY += ROW_H + ROW_PAD;

        // Notes window opacity row
        ctx.drawText(textRenderer, "Notes window opacity", labelX,
                contentY + (ROW_H - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
        int opBtnW = 14;
        int opBtnX = winX + WIN_W - 90;
        int pct = Math.round(IgCalcHudState.notesWindowOpacity * 100);
        String pctStr = pct + "%";
        boolean hDec = mouseX >= opBtnX && mouseX < opBtnX + opBtnW && mouseY >= contentY && mouseY < contentY + ROW_H;
        boolean hInc = mouseX >= opBtnX + opBtnW + textRenderer.getWidth(pctStr) + 6
                    && mouseX < opBtnX + opBtnW + textRenderer.getWidth(pctStr) + 6 + opBtnW
                    && mouseY >= contentY && mouseY < contentY + ROW_H;
        fillRoundedRect(ctx, opBtnX, contentY + 1, opBtnW, ROW_H - 4, 2, hDec ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
        ctx.drawText(textRenderer, "\u2212", opBtnX + (opBtnW - textRenderer.getWidth("\u2212")) / 2,
                contentY + (ROW_H - textRenderer.fontHeight) / 2, COLOR_BTN_TEXT, false);
        ctx.drawText(textRenderer, pctStr, opBtnX + opBtnW + 4,
                contentY + (ROW_H - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
        int incX = opBtnX + opBtnW + textRenderer.getWidth(pctStr) + 6;
        fillRoundedRect(ctx, incX, contentY + 1, opBtnW, ROW_H - 4, 2, hInc ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
        ctx.drawText(textRenderer, "+", incX + (opBtnW - textRenderer.getWidth("+")) / 2,
                contentY + (ROW_H - textRenderer.fontHeight) / 2, COLOR_BTN_TEXT, false);

        contentY += ROW_H + ROW_PAD;

        // Divider 4
        contentY += 4;
        ctx.fill(winX + 12, contentY, winX + WIN_W - 12, contentY + 1, 0xFF2E3347);
        contentY += 6;

        // Right-Click Menu section
        ctx.drawText(textRenderer, "Right-Click Menu", labelX, contentY, 0xFFB0B8D0, false);
        contentY += 12;

        String[][] contextActions = {
            {"cut", "Cut"}, {"copy", "Copy"}, {"paste", "Paste"}, {"selectAll", "Select All"},
            {"insertCoords", "Insert Coordinates"}, {"exportClipboard", "Export to Clipboard"},
            {"newNote", "New Note"}, {"quickNote", "Quick Note"}, {"find", "Find"}
        };
        for (String[] entry : contextActions) {
            boolean checked = editEnabledActions.contains(entry[0]);
            renderCheckboxRow(ctx, mouseX, mouseY, contentY, labelX,
                    entry[1], checked, "ctx_" + entry[0]);
            contentY += ROW_H + ROW_PAD;
        }

        ctx.disableScissor();

        // Scroll indicator
        if (getMaxScroll() > 0) {
            int trackH = clipBottom - clipTop;
            int thumbH = Math.max(10, trackH * trackH / (TOTAL_H - 36));
            int thumbY = clipTop + (int)((float) scrollOffset / getMaxScroll() * (trackH - thumbH));
            ctx.fill(winX + WIN_W - 4, thumbY, winX + WIN_W - 2, thumbY + thumbH, 0x66FFFFFF);
        }

        if (capturingFor != null) {
            String hint = Text.translatable("igcalc.config.capturing").getString();
            ctx.drawCenteredTextWithShadow(textRenderer, hint,
                    winX + WIN_W / 2, winY + visH - 46, 0xFF0A84FF);
        }

        super.render(ctx, mouseX, mouseY, delta);

        // Color picker popup — rendered on top of everything
        if (colorPickerOpen && colorPickerIndex >= 0) {
            renderColorPicker(ctx, mouseX, mouseY);
        }
    }

    private void renderColorPicker(DrawContext ctx, int mouseX, int mouseY) {
        int px = pickerX, py = pickerY;
        // Background + border
        ctx.fill(px - 1, py - 1, px + PICKER_W + 1, py + PICKER_H + 1, COLOR_BORDER);
        ctx.fill(px, py, px + PICKER_W, py + PICKER_H, 0xFF1C1E2B);

        // Saturation/Brightness square (top area)
        int sbH = PICKER_H - HUE_BAR_H - 6;
        int sbX = px + 4, sbY = py + 4, sbW = PICKER_W - 8;
        for (int row = 0; row < sbH; row++) {
            float bri = 1.0f - (float) row / (sbH - 1);
            for (int col = 0; col < sbW; col++) {
                float sat = (float) col / (sbW - 1);
                int rgb = java.awt.Color.HSBtoRGB(pickerHue, sat, bri);
                ctx.fill(sbX + col, sbY + row, sbX + col + 1, sbY + row + 1, 0xFF000000 | rgb);
            }
        }
        // Crosshair on SB square
        int chX = sbX + Math.round(pickerSat * (sbW - 1));
        int chY = sbY + Math.round((1.0f - pickerBri) * (sbH - 1));
        ctx.fill(chX - 1, chY, chX + 2, chY + 1, 0xFFFFFFFF);
        ctx.fill(chX, chY - 1, chX + 1, chY + 2, 0xFFFFFFFF);

        // Hue bar (bottom)
        int hbX = px + 4, hbY = py + PICKER_H - HUE_BAR_H - 2, hbW = PICKER_W - 8;
        for (int col = 0; col < hbW; col++) {
            float hue = (float) col / (hbW - 1);
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            ctx.fill(hbX + col, hbY, hbX + col + 1, hbY + HUE_BAR_H, 0xFF000000 | rgb);
        }
        // Hue indicator
        int hiX = hbX + Math.round(pickerHue * (hbW - 1));
        ctx.fill(hiX, hbY - 1, hiX + 1, hbY + HUE_BAR_H + 1, 0xFFFFFFFF);
    }

    private static final int RESET_BTN_W = 14;
    private static final int RESET_BTN_GAP = 2;

    private void renderRow(DrawContext ctx, int mouseX, int mouseY,
                            int y, int labelX, int btnX, int btnW,
                            String label, IgCalcConfig.HotkeyBinding binding, String key) {
        ctx.drawText(textRenderer, label, labelX,
                y + (ROW_H - textRenderer.fontHeight) / 2, COLOR_LABEL, false);

        // Reset button — shown when binding differs from default
        IgCalcConfig.HotkeyBinding def = getDefault(key);
        boolean nonDefault = def != null && !binding.equals(def);
        int kbBtnX = btnX;
        int kbBtnW = btnW;
        if (nonDefault) {
            kbBtnX = btnX + RESET_BTN_W + RESET_BTN_GAP;
            kbBtnW = btnW - RESET_BTN_W - RESET_BTN_GAP;
            boolean hoverReset = mouseX >= btnX && mouseX < btnX + RESET_BTN_W
                              && mouseY >= y && mouseY < y + ROW_H;
            fillRoundedRect(ctx, btnX, y, RESET_BTN_W, ROW_H - 2, 3,
                    hoverReset ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
            String resetIcon = "\u21BA";
            ctx.drawText(textRenderer, resetIcon,
                    btnX + (RESET_BTN_W - textRenderer.getWidth(resetIcon)) / 2,
                    y + (ROW_H - textRenderer.fontHeight) / 2, COLOR_READONLY, false);
        }

        boolean capturing = key.equals(capturingFor);
        boolean hovered   = !capturing && mouseX >= kbBtnX && mouseX < kbBtnX + kbBtnW
                         && mouseY >= y && mouseY < y + ROW_H;
        int bgCol = capturing ? COLOR_BTN_CAP : (hovered ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
        fillRoundedRect(ctx, kbBtnX, y, kbBtnW, ROW_H - 2, 3, bgCol);

        // While capturing, show the live binding (or prompt if unchanged)
        String btnLabel = capturing ? binding.display() : binding.display();
        int textX = kbBtnX + (kbBtnW - textRenderer.getWidth(btnLabel)) / 2;
        ctx.drawText(textRenderer, btnLabel, textX,
                y + (ROW_H - textRenderer.fontHeight) / 2,
                capturing ? 0xFFFFFFFF : COLOR_BTN_TEXT, false);
    }

    private void renderCheckboxRow(DrawContext ctx, int mouseX, int mouseY,
                                    int y, int labelX, String label, boolean checked, String key) {
        int cbX = labelX;
        int cbY = y + (ROW_H - 10) / 2;
        boolean hov = mouseX >= cbX && mouseX < cbX + 10 && mouseY >= cbY && mouseY < cbY + 10;
        ctx.fill(cbX, cbY, cbX + 10, cbY + 10, hov ? COLOR_BTN_HOVER : COLOR_BTN_NORM);
        ctx.fill(cbX, cbY, cbX + 10, cbY + 1, COLOR_BORDER);
        ctx.fill(cbX, cbY + 9, cbX + 10, cbY + 10, COLOR_BORDER);
        ctx.fill(cbX, cbY, cbX + 1, cbY + 10, COLOR_BORDER);
        ctx.fill(cbX + 9, cbY, cbX + 10, cbY + 10, COLOR_BORDER);
        if (checked) {
            ctx.drawText(textRenderer, "\u2714", cbX + 1, cbY + 1, 0xFFE5E5E5, false);
        }
        ctx.drawText(textRenderer, label, cbX + 14, y + (ROW_H - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
    }

    private void renderReadOnlyRow(DrawContext ctx, int y, int labelX, int btnX, int btnW,
                                    String label, String value) {
        ctx.drawText(textRenderer, label, labelX,
                y + (ROW_H - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
        fillRoundedRect(ctx, btnX, y, btnW, ROW_H - 2, 3, COLOR_BTN_NORM);
        int textX = btnX + (btnW - textRenderer.getWidth(value)) / 2;
        ctx.drawText(textRenderer, value, textX,
                y + (ROW_H - textRenderer.fontHeight) / 2, COLOR_READONLY, false);
    }

    // =========================================================================
    // Input
    // =========================================================================
    @Override
    public boolean mouseClicked(Click click, boolean propagated) {
        int mx = (int) click.x();
        int my = (int) click.y();

        // Color picker interaction — check first since it renders on top
        if (colorPickerOpen && colorPickerIndex >= 0) {
            if (handlePickerClick(mx, my)) return true;
            // Click outside picker closes it
            colorPickerOpen = false;
            colorPickerIndex = -1;
            return true;
        }

        if (mx >= winX + 4 && mx <= winX + 15 && my >= winY + 3 && my <= winY + 15) {
            close(); return true;
        }

        // Handle capture finalization on click
        if (capturingFor != null) {
            // Check if click is on the active keybind button — if so, stay in capture
            int capContentY = winY + TITLE_BAR_HEIGHT + 10 - scrollOffset;
            int capBtnX = winX + WIN_W - 90;
            int capBtnW = 84;
            boolean clickedOnCaptureBtn = false;
            for (String key : KEYBIND_KEYS) {
                if (key.equals(capturingFor) && my >= capContentY && my < capContentY + ROW_H
                        && mx >= capBtnX && mx < capBtnX + capBtnW) {
                    clickedOnCaptureBtn = true;
                    break;
                }
                capContentY += ROW_H + ROW_PAD;
            }
            if (clickedOnCaptureBtn) return true;
            // Click outside the capture button — finalize and fall through
            capturingFor = null;
            capturingOriginal = null;
        }

        // Scrollable content hit detection — clamp to visible clip region
        // so that scrolled rows don't intercept clicks on fixed-position buttons
        int clipTop    = winY + TITLE_BAR_HEIGHT;
        int clipBottom = winY + getVisH() - 36;

        // Check color preview square clicks
        int tfwX2 = winX + WIN_W - 90 + 16;
        int previewX2 = tfwX2 - 16;
        for (int k = 0; k < 6; k++) {
            int fy = winY + COLOR_SECTION_OFFSET + k * (ROW_H + ROW_PAD) + 2 - scrollOffset;
            int sqY = fy + 2;  // contentY + 4 relative
            if (sqY >= clipTop && sqY + 12 <= clipBottom
                    && mx >= previewX2 && mx < previewX2 + 12 && my >= sqY && my < sqY + 12) {
                openColorPicker(k, previewX2 - PICKER_W - 4, sqY);
                return true;
            }
        }

        if (my >= clipTop && my < clipBottom) {
            int contentY = winY + TITLE_BAR_HEIGHT + 10 - scrollOffset;
            int btnX     = winX + WIN_W - 90;
            int btnW     = 84;

            for (String key : KEYBIND_KEYS) {
                if (my >= contentY && my < contentY + ROW_H) {
                    // Check reset button click (left side of button area)
                    IgCalcConfig.HotkeyBinding cur = getBinding(key);
                    IgCalcConfig.HotkeyBinding def = getDefault(key);
                    boolean nonDefault = def != null && cur != null && !cur.equals(def);
                    if (nonDefault && mx >= btnX && mx < btnX + RESET_BTN_W) {
                        setBinding(key, def);
                        return true;
                    }
                    // Check keybind button click — start capture
                    int kbBtnX = nonDefault ? btnX + RESET_BTN_W + RESET_BTN_GAP : btnX;
                    int kbBtnW = nonDefault ? btnW - RESET_BTN_W - RESET_BTN_GAP : btnW;
                    if (mx >= kbBtnX && mx < kbBtnX + kbBtnW) {
                        capturingFor = key;
                        capturingOriginal = getBinding(key);
                        return true;
                    }
                }
                contentY += ROW_H + ROW_PAD;
            }

            // Skip past divider1 + 5 read-only rows + divider2 + colors-label + 6 color rows + divider3 + hud-label
            contentY += 10; // divider 1
            contentY += (ROW_H + ROW_PAD) * 5; // read-only rows
            contentY += 10; // divider 2
            contentY += 12; // colors section label
            contentY += (ROW_H + ROW_PAD) * 6; // color rows
            contentY += 10; // divider 3
            contentY += 12; // HUD Display label

            int labelX = winX + 12;
            // Notes HUD background checkbox
            if (mx >= labelX && mx < labelX + WIN_W - 24 && my >= contentY && my < contentY + ROW_H) {
                IgCalcHudState.notesHudShowBg = !IgCalcHudState.notesHudShowBg;
                IgCalcHudState.save();
                return true;
            }
            contentY += ROW_H + ROW_PAD;
            // Timer HUD background checkbox
            if (mx >= labelX && mx < labelX + WIN_W - 24 && my >= contentY && my < contentY + ROW_H) {
                IgCalcHudState.timerHudShowBg = !IgCalcHudState.timerHudShowBg;
                IgCalcHudState.save();
                return true;
            }
            contentY += ROW_H + ROW_PAD;
            // Notes window opacity [-] [+] buttons
            int opBtnX = winX + WIN_W - 90;
            int opBtnW = 14;
            if (my >= contentY && my < contentY + ROW_H) {
                int pct = Math.round(IgCalcHudState.notesWindowOpacity * 100);
                String pctStr = pct + "%";
                int incX = opBtnX + opBtnW + textRenderer.getWidth(pctStr) + 6;
                if (mx >= opBtnX && mx < opBtnX + opBtnW) {
                    IgCalcHudState.notesWindowOpacity = Math.max(0.3f, IgCalcHudState.notesWindowOpacity - 0.05f);
                    IgCalcHudState.save();
                    return true;
                }
                if (mx >= incX && mx < incX + opBtnW) {
                    IgCalcHudState.notesWindowOpacity = Math.min(1.0f, IgCalcHudState.notesWindowOpacity + 0.05f);
                    IgCalcHudState.save();
                    return true;
                }
            }

            contentY += ROW_H + ROW_PAD; // advance past opacity row
            contentY += 10; // divider 4
            contentY += 12; // section label

            String[] ctxKeys = {"cut", "copy", "paste", "selectAll", "insertCoords", "exportClipboard", "newNote", "quickNote", "find"};
            for (String ctxKey : ctxKeys) {
                if (mx >= labelX && mx < labelX + WIN_W - 24 && my >= contentY && my < contentY + ROW_H) {
                    if (editEnabledActions.contains(ctxKey)) editEnabledActions.remove(ctxKey);
                    else editEnabledActions.add(ctxKey);
                    return true;
                }
                contentY += ROW_H + ROW_PAD;
            }
        }

        return super.mouseClicked(click, propagated);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (colorPickerOpen && input.key() == 256) {
            colorPickerOpen = false;
            colorPickerIndex = -1;
            return true;
        }
        if (capturingFor != null) {
            int key  = input.key();
            int mods = input.modifiers();
            if (key == 256) { // ESC cancels — restore original
                if (capturingOriginal != null) setBinding(capturingFor, capturingOriginal);
                capturingFor = null;
                capturingOriginal = null;
                return true;
            }
            // Ignore modifier-only keys (Ctrl, Shift, Alt, Super)
            if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
             || key == GLFW.GLFW_KEY_LEFT_SHIFT   || key == GLFW.GLFW_KEY_RIGHT_SHIFT
             || key == GLFW.GLFW_KEY_LEFT_ALT     || key == GLFW.GLFW_KEY_RIGHT_ALT
             || key == GLFW.GLFW_KEY_LEFT_SUPER   || key == GLFW.GLFW_KEY_RIGHT_SUPER) {
                return true;
            }
            boolean ctrl  = (mods & 2) != 0;
            boolean shift = (mods & 1) != 0;
            IgCalcConfig.HotkeyBinding nb = new IgCalcConfig.HotkeyBinding(key, ctrl, shift);
            setBinding(capturingFor, nb);
            // Stay in capture mode — user clicks outside to finalize
            return true;
        }
        if (input.key() == 256) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseDragged(Click click, double dX, double dY) {
        if (colorPickerOpen && colorPickerIndex >= 0) {
            handlePickerClick((int) click.x(), (int) click.y());
            return true;
        }
        return super.mouseDragged(click, dX, dY);
    }

    private void openColorPicker(int index, int x, int y) {
        colorPickerOpen = true;
        colorPickerIndex = index;
        pickerX = x;
        pickerY = y;
        // Initialize HSB from current hex value
        TextFieldWidget[] fields = { colorBgField, colorTitleBarField, colorAccentField,
                colorSidebarBgField, colorSidebarSelectField, colorBorderField };
        try {
            int rgb = (int) Long.parseLong(fields[index].getText().replaceAll("^#", ""), 16);
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            pickerHue = hsb[0];
            pickerSat = hsb[1];
            pickerBri = hsb[2];
        } catch (NumberFormatException e) {
            pickerHue = 0f; pickerSat = 1f; pickerBri = 1f;
        }
    }

    private boolean handlePickerClick(int mx, int my) {
        int sbH = PICKER_H - HUE_BAR_H - 6;
        int sbX = pickerX + 4, sbY = pickerY + 4, sbW = PICKER_W - 8;
        int hbX = pickerX + 4, hbY = pickerY + PICKER_H - HUE_BAR_H - 2, hbW = PICKER_W - 8;

        if (mx >= sbX && mx < sbX + sbW && my >= sbY && my < sbY + sbH) {
            pickerSat = Math.max(0f, Math.min(1f, (float)(mx - sbX) / (sbW - 1)));
            pickerBri = Math.max(0f, Math.min(1f, 1.0f - (float)(my - sbY) / (sbH - 1)));
            updateColorFromPicker();
            return true;
        }
        if (mx >= hbX && mx < hbX + hbW && my >= hbY && my < hbY + HUE_BAR_H) {
            pickerHue = Math.max(0f, Math.min(1f, (float)(mx - hbX) / (hbW - 1)));
            updateColorFromPicker();
            return true;
        }
        // Click is inside picker bounds but not on a control
        if (mx >= pickerX && mx < pickerX + PICKER_W && my >= pickerY && my < pickerY + PICKER_H) {
            return true;
        }
        return false;
    }

    private void updateColorFromPicker() {
        int rgb = java.awt.Color.HSBtoRGB(pickerHue, pickerSat, pickerBri);
        // Preserve alpha from current field
        TextFieldWidget[] fields = { colorBgField, colorTitleBarField, colorAccentField,
                colorSidebarBgField, colorSidebarSelectField, colorBorderField };
        String currentHex = fields[colorPickerIndex].getText().replaceAll("^#", "");
        String alpha = "ff";
        if (currentHex.length() == 8) {
            alpha = currentHex.substring(0, 2);
        }
        String hex = alpha + String.format("%06x", rgb & 0x00FFFFFF);
        fields[colorPickerIndex].setText(hex);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        scrollOffset = Math.max(0, Math.min(getMaxScroll(), scrollOffset - (int)(vAmount * 10)));
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
