// FILE: src/main/java/com/igcalc/gui/IgCalcOverlay.java
package com.igcalc.gui;

import com.igcalc.IgCalcClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Thin overlay screen that hosts both the calculator and notes windows
 * simultaneously. Each sub-screen manages its own state, children, and
 * rendering — this class just routes events and tracks focus.
 */
public class IgCalcOverlay extends Screen {

    public final CalculatorScreen calcScreen  = new CalculatorScreen();
    public final NotesScreen      notesScreen = new NotesScreen();
    public final TimerScreen      timerScreen = new TimerScreen();

    public boolean showCalc;
    public boolean showNotes;
    public boolean showTimer;

    private String pendingNotif  = null;
    private long   notifExpireMs = 0;

    /** The sub-screen that last received a mouse click (for keyboard routing). */
    Screen focusedWindow = null;

    /** Tutorial overlay — renders on top of everything when active. */
    public TutorialScreen tutorial = null;

    /** Returns the currently focused sub-screen (for active window indicator). */
    public Screen getFocusedWindow() { return focusedWindow; }

    public IgCalcOverlay(boolean showCalc, boolean showNotes, boolean showTimer) {
        super(Text.literal("Notes+"));
        this.showCalc  = showCalc;
        this.showNotes = showNotes;
        this.showTimer = showTimer;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @Override
    protected void init() {
        if (showCalc)  initCalc();
        if (showNotes) initNotes();
        if (showTimer) initTimer();
        if (tutorial != null && tutorial.isActive()) {
            tutorial.initTutorial(width, height);
        }
    }

    private void initCalc() {
        calcScreen.overlay = this;
        calcScreen.init(width, height);
        calcScreen.closeCallback = () -> {
            showCalc = false;
            if (!showNotes && !showTimer) super.close();
        };
        calcScreen.sendToNotes = () -> {
            String result = calcScreen.getDisplayResult();
            String expr   = calcScreen.getExpression();
            String text   = (result != null && !result.isEmpty())
                    ? expr + " = " + result
                    : expr;
            if (text == null || text.isEmpty()) return;
            if (!showNotes) enableNotes();
            notesScreen.insertText(text);
        };
    }

    private void initNotes() {
        notesScreen.overlay = this;
        notesScreen.init(width, height);
        notesScreen.closeCallback = () -> {
            showNotes = false;
            if (!showCalc && !showTimer) super.close();
            else notesScreen.save();
        };
    }

    private void initTimer() {
        timerScreen.overlay = this;
        timerScreen.init(width, height);
        timerScreen.closeCallback = () -> {
            showTimer = false;
            if (!showCalc && !showNotes) super.close();
        };
        timerScreen.onTimerComplete = () -> {
            pendingNotif  = "Timer finished!";
            notifExpireMs = System.currentTimeMillis() + 5000;
            HudRenderer.INSTANCE.toastText     = "Timer finished!";
            HudRenderer.INSTANCE.toastExpireMs = System.currentTimeMillis() + 5000;
        };
    }

    /** Called by IgCalcClient when toggling calc while overlay is already open. */
    public void enableCalc() {
        showCalc = true;
        initCalc();
        focusedWindow = calcScreen;
    }

    /** Called by IgCalcClient when toggling notes while overlay is already open. */
    public void enableNotes() {
        showNotes = true;
        initNotes();
        focusedWindow = notesScreen;
    }

    /** Called by IgCalcClient when toggling timer while overlay is already open. */
    public void enableTimer() {
        showTimer = true;
        initTimer();
        focusedWindow = timerScreen;
    }

    /** Starts the full tutorial from a given step. */
    public void startTutorial(int startStep) {
        tutorial = new TutorialScreen(this, startStep);
        tutorial.initTutorial(width, height);
    }

    /** Starts a filtered tutorial showing only the given steps. */
    public void startTutorialFiltered(int[] steps) {
        tutorial = new TutorialScreen(this, steps[0], steps);
        tutorial.initTutorial(width, height);
    }

    @Override
    public void tick() {
        if (client != null) client.mouse.unlockCursor();
        if (showCalc)  calcScreen.tick();
        if (showNotes) notesScreen.tick();
        if (showTimer) timerScreen.tick();
    }

    /** Saves open sub-screens when the overlay is dismissed by external means. */
    @Override
    public void removed() {
        if (showNotes) notesScreen.save();
    }

    // -------------------------------------------------------------------------
    // Rendering — sub-screens draw themselves (including their own children)
    // -------------------------------------------------------------------------
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (showNotes) notesScreen.render(context, mouseX, mouseY, delta);
        if (showCalc)  calcScreen.render(context, mouseX, mouseY, delta);
        if (showTimer) timerScreen.render(context, mouseX, mouseY, delta);
        // Notification toast — only while timer window is open
        if (showTimer && pendingNotif != null) {
            if (System.currentTimeMillis() < notifExpireMs) {
                int nw = textRenderer.getWidth(pendingNotif) + 16;
                int nh = 16, nx = this.width - nw - 8, ny = 8;
                context.fill(nx, ny, nx + nw, ny + nh, 0xEE1C1E2B);
                context.fill(nx, ny, nx + nw, ny + 1, 0xFF3A3F52);
                context.fill(nx, ny + nh - 1, nx + nw, ny + nh, 0xFF3A3F52);
                context.drawText(textRenderer, pendingNotif, nx + 8, ny + 4, 0xFFE8C170, false);
            } else {
                pendingNotif = null;
            }
        }
        // Tutorial overlay renders on top of everything
        if (tutorial != null && tutorial.isActive()) {
            tutorial.render(context, mouseX, mouseY, delta);
        }
        // Do not call super.render() — sub-screens handle their own child rendering
    }

    // -------------------------------------------------------------------------
    // Mouse input
    // -------------------------------------------------------------------------
    @Override
    public boolean mouseClicked(Click click, boolean propagated) {
        // Tutorial intercepts all mouse input when active
        if (tutorial != null && tutorial.isActive()) {
            return tutorial.handleMouseClicked((int) click.x(), (int) click.y());
        }
        // Timer is rendered on top, so it gets priority
        if (showTimer && timerScreen.mouseClicked(click, propagated)) {
            focusedWindow = timerScreen;
            return true;
        }
        if (showCalc && calcScreen.mouseClicked(click, propagated)) {
            focusedWindow = calcScreen;
            return true;
        }
        if (showNotes && notesScreen.mouseClicked(click, propagated)) {
            focusedWindow = notesScreen;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dX, double dY) {
        if (tutorial != null && tutorial.isActive()) return true;
        if (showTimer && timerScreen.mouseDragged(click, dX, dY)) return true;
        if (showCalc  && calcScreen.mouseDragged(click, dX, dY))  return true;
        if (showNotes && notesScreen.mouseDragged(click, dX, dY)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (tutorial != null && tutorial.isActive()) return true;
        if (showTimer) timerScreen.mouseReleased(click);
        if (showCalc)  calcScreen.mouseReleased(click);
        if (showNotes) notesScreen.mouseReleased(click);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (tutorial != null && tutorial.isActive()) return true;
        if (showTimer && timerScreen.mouseScrolled(mx, my, hAmount, vAmount)) return true;
        if (showCalc  && calcScreen.mouseScrolled(mx, my, hAmount, vAmount))  return true;
        if (showNotes && notesScreen.mouseScrolled(mx, my, hAmount, vAmount)) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Keyboard input — routed to focused window only to avoid double-typing
    // -------------------------------------------------------------------------
    @Override
    public boolean keyPressed(KeyInput input) {
        // Tutorial intercepts all keyboard input when active
        if (tutorial != null && tutorial.isActive()) {
            return tutorial.handleKeyPressed(input.key());
        }
        // Toggle windows while the overlay is open (key bindings are suppressed by Minecraft when a screen is showing)
        if (IgCalcClient.calculatorKey.matchesKey(input)) {
            if (showCalc) { showCalc = false; if (!showNotes && !showTimer) super.close(); }
            else enableCalc();
            return true;
        }
        if (IgCalcClient.notesKey.matchesKey(input)) {
            if (showNotes) { showNotes = false; if (!showCalc && !showTimer) super.close(); }
            else enableNotes();
            return true;
        }
        if (IgCalcClient.timerKey.matchesKey(input)) {
            if (showTimer) { showTimer = false; if (!showCalc && !showNotes) super.close(); }
            else {
                if (HudRenderer.INSTANCE.pinnedTimer)
                    HudRenderer.INSTANCE.restoreTimerState(timerScreen);
                enableTimer();
            }
            return true;
        }
        if (focusedWindow == timerScreen && showTimer && timerScreen.keyPressed(input)) return true;
        if (focusedWindow == calcScreen  && showCalc  && calcScreen.keyPressed(input))  return true;
        if (focusedWindow == notesScreen && showNotes && notesScreen.keyPressed(input)) return true;
        // Fallthrough: ESC closes the overlay, or broadcast to non-focused windows
        if (input.key() == 256) { super.close(); return true; }
        if (showTimer && focusedWindow != timerScreen && timerScreen.keyPressed(input)) return true;
        if (showCalc  && focusedWindow != calcScreen  && calcScreen.keyPressed(input))  return true;
        if (showNotes && focusedWindow != notesScreen && notesScreen.keyPressed(input)) return true;
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (tutorial != null && tutorial.isActive()) return true;
        if (focusedWindow == timerScreen && showTimer) return timerScreen.charTyped(input);
        if (focusedWindow == calcScreen  && showCalc)  return calcScreen.charTyped(input);
        if (focusedWindow == notesScreen && showNotes) return notesScreen.charTyped(input);
        if (showTimer && timerScreen.charTyped(input)) return true;
        if (showCalc  && calcScreen.charTyped(input))  return true;
        if (showNotes && notesScreen.charTyped(input)) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------
    @Override
    public boolean shouldPause() { return false; }
}
