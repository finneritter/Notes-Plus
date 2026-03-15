package com.igcalc.gui;

import com.igcalc.IgCalcClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Thin Screen opened while the HUD interact key is held.
 * Unlocks the cursor and routes mouse events to HudRenderer.
 * Closes itself when the hold key is released.
 */
public class HudInteractionScreen extends Screen {

    public HudInteractionScreen() {
        super(Text.literal(""));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.mouse.unlockCursor();
        HudRenderer.INSTANCE.interactionActive = true;
    }

    @Override
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) { close(); return; }
        mc.mouse.unlockCursor();

        // Close when the hold key is released, but stay open while editing
        boolean keyDown = IgCalcClient.hudInteractKey.isPressed();
        if (!keyDown && !HudRenderer.INSTANCE.notesEditingActive && !HudRenderer.INSTANCE.notesSearchActive) close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        HudRenderer.INSTANCE.renderInteractive(ctx, mx, my, delta);
        // Don't call super.render() — no child widgets needed
    }

    @Override
    public boolean mouseClicked(Click click, boolean propagated) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            return HudRenderer.INSTANCE.mouseClicked((int) click.x(), (int) click.y(), mc);
        }
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (HudRenderer.INSTANCE.pinnedNotes && HudRenderer.INSTANCE.notesEditingActive) {
            HudRenderer.INSTANCE.handleEditChar(input.codepoint());
            return true;
        }
        if (HudRenderer.INSTANCE.pinnedNotes && HudRenderer.INSTANCE.notesSearchActive) {
            HudRenderer.INSTANCE.handleSearchChar(input.codepoint());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Ctrl+Enter toggles HUD search
        if (HudRenderer.INSTANCE.pinnedNotes && !HudRenderer.INSTANCE.notesEditingActive) {
            boolean ctrlHeld = (input.modifiers() & 2) != 0;
            if (ctrlHeld && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
                HudRenderer.INSTANCE.toggleSearch();
                return true;
            }
        }
        if (HudRenderer.INSTANCE.pinnedNotes && HudRenderer.INSTANCE.notesEditingActive) {
            return HudRenderer.INSTANCE.handleEditKey(input.key(), input.modifiers());
        }
        if (HudRenderer.INSTANCE.pinnedNotes && HudRenderer.INSTANCE.notesSearchActive) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                HudRenderer.INSTANCE.handleSearchBackspace();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                HudRenderer.INSTANCE.toggleSearch();
                return true;
            }
            if (HudRenderer.INSTANCE.handleSearchKeyNav(input.key())) return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseDragged(Click click, double dX, double dY) {
        return HudRenderer.INSTANCE.mouseDragged((int) click.x(), (int) click.y());
    }

    @Override
    public boolean mouseReleased(Click click) {
        return HudRenderer.INSTANCE.mouseReleased((int) click.x(), (int) click.y());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            return HudRenderer.INSTANCE.mouseScrolled(mx, my, vAmount, mc);
        }
        return false;
    }

    @Override
    public void close() {
        HudRenderer.INSTANCE.interactionActive = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }
}
