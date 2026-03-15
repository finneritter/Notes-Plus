package com.igcalc;
import com.igcalc.config.IgCalcConfig;
import com.igcalc.config.IgCalcHudState;
import com.igcalc.config.IgCalcTutorialState;
import com.igcalc.gui.HudInteractionScreen;
import com.igcalc.gui.HudRenderer;
import com.igcalc.gui.IgCalcOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class IgCalcClient implements ClientModInitializer {

    private static final KeyBinding.Category NOTES_PLUS_CATEGORY =
            KeyBinding.Category.create(Identifier.of("igcalc", "keybind_category"));

    public static KeyBinding calculatorKey;
    public static KeyBinding notesKey;
    public static KeyBinding timerKey;
    public static KeyBinding hudToggleKey;
    public static KeyBinding hudInteractKey;

    private boolean quickNoteWasDown      = false;
    private boolean insertCoordsWasDown   = false;
    private boolean stopwatchToggleWasDown = false;
    private boolean stopwatchResetWasDown  = false;
    private boolean hudInteractWasDown    = false;
    private boolean hudSearchWasDown    = false;
    private boolean hudCtrlUpWasDown    = false;
    private boolean hudCtrlDownWasDown  = false;
    private boolean hudCtrlLeftWasDown  = false;
    private boolean hudCtrlRightWasDown = false;
    private static boolean tutorialChecked = false;

    /** Allow the first-run tutorial check to fire again (e.g. after tutorial reset). */
    public static void resetTutorialChecked() { tutorialChecked = false; }

    @Override
    public void onInitializeClient() {
        calculatorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "igcalc.keybind.calculator",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                NOTES_PLUS_CATEGORY
        ));

        notesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "igcalc.keybind.notes",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                NOTES_PLUS_CATEGORY
        ));

        timerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "igcalc.keybind.timer",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                NOTES_PLUS_CATEGORY
        ));

        hudToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "igcalc.keybind.hudToggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                NOTES_PLUS_CATEGORY
        ));

        hudInteractKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "igcalc.keybind.hudInteract",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                NOTES_PLUS_CATEGORY
        ));

        HudRenderer.INSTANCE.register();
        IgCalcConfig.getInstance().load();
        IgCalcHudState.load();
        // Restore pinned state from disk
        HudRenderer.INSTANCE.pinnedNotes      = IgCalcHudState.notesHudPinned;
        HudRenderer.INSTANCE.hudGlobalVisible = IgCalcHudState.hudGlobalVisible;
        HudRenderer.INSTANCE.pinnedTimer      = IgCalcHudState.timerHudPinned;
        // Toast when timer completes while pinned (no overlay open)
        HudRenderer.INSTANCE.onTimerComplete = () -> {
            HudRenderer.INSTANCE.toastText     = "Timer finished!";
            HudRenderer.INSTANCE.toastExpireMs = System.currentTimeMillis() + 5000;
        };

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (calculatorKey.wasPressed()) {
                if (client.currentScreen instanceof IgCalcOverlay overlay) {
                    if (overlay.showCalc) { overlay.showCalc = false; if (!overlay.showNotes && !overlay.showTimer) client.setScreen(null); }
                    else overlay.enableCalc();
                } else if (client.currentScreen == null) {
                    client.setScreen(new IgCalcOverlay(true, false, false));
                }
            }
            while (notesKey.wasPressed()) {
                if (client.currentScreen instanceof IgCalcOverlay overlay) {
                    if (overlay.showNotes) { overlay.showNotes = false; if (!overlay.showCalc && !overlay.showTimer) client.setScreen(null); }
                    else overlay.enableNotes();
                } else if (client.currentScreen == null) {
                    client.setScreen(new IgCalcOverlay(false, true, false));
                }
            }
            while (timerKey.wasPressed()) {
                if (client.currentScreen instanceof IgCalcOverlay overlay) {
                    if (overlay.showTimer) { overlay.showTimer = false; if (!overlay.showCalc && !overlay.showNotes) client.setScreen(null); }
                    else {
                        if (HudRenderer.INSTANCE.pinnedTimer)
                            HudRenderer.INSTANCE.restoreTimerState(overlay.timerScreen);
                        overlay.enableTimer();
                    }
                } else if (client.currentScreen == null) {
                    IgCalcOverlay o = new IgCalcOverlay(false, false, true);
                    if (HudRenderer.INSTANCE.pinnedTimer) HudRenderer.INSTANCE.restoreTimerState(o.timerScreen);
                    client.setScreen(o);
                }
            }

            // First-run tutorial detection
            if (!tutorialChecked && client.world != null && client.currentScreen == null) {
                tutorialChecked = true;
                if (!IgCalcTutorialState.isCompleted()) {
                    IgCalcOverlay o = new IgCalcOverlay(false, false, false);
                    client.setScreen(o);
                    o.startTutorial(0);
                }
            }

            // Global quick note — fires when overlay is not showing notes
            IgCalcConfig qcfg = IgCalcConfig.getInstance();
            long win = client.getWindow().getHandle();
            boolean ctrlDown  = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                             || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shiftDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT)   == GLFW.GLFW_PRESS
                             || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT)  == GLFW.GLFW_PRESS;
            boolean keyDown   = GLFW.glfwGetKey(win, qcfg.keyQuickNote.keyCode()) == GLFW.GLFW_PRESS;
            boolean match     = keyDown
                    && (qcfg.keyQuickNote.ctrl()  == ctrlDown)
                    && (qcfg.keyQuickNote.shift() == shiftDown);
            if (match && !quickNoteWasDown) {
                if (client.currentScreen instanceof IgCalcOverlay overlay && overlay.showNotes) {
                    // Notes is visible — keyPressed in NotesScreen handles it; don't double-fire
                } else if (client.currentScreen instanceof IgCalcOverlay overlay2) {
                    // Calc-only overlay: open notes with a pending quick note
                    overlay2.notesScreen.pendingQuickNote = true;
                    overlay2.enableNotes();
                } else if (client.currentScreen == null) {
                    IgCalcOverlay o = new IgCalcOverlay(false, true, false);
                    o.notesScreen.pendingQuickNote = true;
                    client.setScreen(o);
                }
            }
            quickNoteWasDown = match;

            // Global insert coords (Ctrl+Shift+G default) — fires when overlay is not showing notes
            boolean coordKeyDown = GLFW.glfwGetKey(win, qcfg.keyInsertCoords.keyCode()) == GLFW.GLFW_PRESS;
            boolean coordMatch   = coordKeyDown
                    && (qcfg.keyInsertCoords.ctrl()  == ctrlDown)
                    && (qcfg.keyInsertCoords.shift() == shiftDown);
            if (coordMatch && !insertCoordsWasDown) {
                if (client.currentScreen instanceof IgCalcOverlay overlay && overlay.showNotes) {
                    // Notes visible — keyPressed in NotesScreen handles it; don't double-fire
                } else if (client.currentScreen instanceof IgCalcOverlay overlay2) {
                    overlay2.enableNotes();
                    overlay2.notesScreen.insertPlayerCoords();
                } else if (client.currentScreen == null) {
                    IgCalcOverlay o = new IgCalcOverlay(false, true, false);
                    client.setScreen(o);
                    o.notesScreen.insertPlayerCoords();
                }
            }
            insertCoordsWasDown = coordMatch;

            // Global stopwatch toggle (Ctrl+Shift+T default)
            boolean swKeyDown = GLFW.glfwGetKey(win, qcfg.keyStopwatchToggle.keyCode()) == GLFW.GLFW_PRESS;
            boolean swMatch   = swKeyDown
                    && (qcfg.keyStopwatchToggle.ctrl()  == ctrlDown)
                    && (qcfg.keyStopwatchToggle.shift() == shiftDown);
            if (swMatch && !stopwatchToggleWasDown) {
                // Auto-pin the timer if not already pinned
                if (!HudRenderer.INSTANCE.pinnedTimer) {
                    HudRenderer.INSTANCE.pinnedTimer = true;
                    IgCalcHudState.timerHudPinned = true;
                    IgCalcHudState.save();
                }
                HudRenderer.INSTANCE.toggleStopwatch();
                // Sync back to the timer screen if it's open
                if (client.currentScreen instanceof IgCalcOverlay overlay && overlay.showTimer) {
                    HudRenderer.INSTANCE.restoreTimerState(overlay.timerScreen);
                }
            }
            stopwatchToggleWasDown = swMatch;

            // Global stopwatch reset (Ctrl+Shift+R default)
            boolean swResetDown = GLFW.glfwGetKey(win, qcfg.keyStopwatchReset.keyCode()) == GLFW.GLFW_PRESS;
            boolean swResetMatch = swResetDown
                    && (qcfg.keyStopwatchReset.ctrl()  == ctrlDown)
                    && (qcfg.keyStopwatchReset.shift() == shiftDown);
            if (swResetMatch && !stopwatchResetWasDown) {
                if (HudRenderer.INSTANCE.pinnedTimer) {
                    HudRenderer.INSTANCE.resetStopwatch();
                    if (client.currentScreen instanceof IgCalcOverlay overlay && overlay.showTimer) {
                        HudRenderer.INSTANCE.restoreTimerState(overlay.timerScreen);
                    }
                }
            }
            stopwatchResetWasDown = swResetMatch;

            // HUD tick (timer completion detection)
            HudRenderer.INSTANCE.tick(client);

            // HUD global toggle (registered KeyBinding — Controls tab)
            while (hudToggleKey.wasPressed()) {
                HudRenderer.INSTANCE.hudGlobalVisible = !HudRenderer.INSTANCE.hudGlobalVisible;
                IgCalcHudState.hudGlobalVisible = HudRenderer.INSTANCE.hudGlobalVisible;
                IgCalcHudState.save();
            }

            // HUD interact key (hold) — only when no screen is open and a HUD is pinned
            boolean interactDown = hudInteractKey.isPressed();
            if (interactDown && !hudInteractWasDown
                    && client.currentScreen == null
                    && (HudRenderer.INSTANCE.pinnedNotes || HudRenderer.INSTANCE.pinnedTimer)) {
                client.setScreen(new HudInteractionScreen());
            }
            hudInteractWasDown = interactDown;

            // Ctrl+Enter opens HUD search for pinned notes (no screen needed)
            long hudWin = client.getWindow().getHandle();
            if (HudRenderer.INSTANCE.pinnedNotes && client.currentScreen == null) {
                boolean ctrlHeld2 = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                                 || GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                boolean enterDown = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS;
                boolean searchCombo = ctrlHeld2 && enterDown;
                if (searchCombo && !hudSearchWasDown) {
                    HudRenderer.INSTANCE.notesSearchActive = true;
                    client.setScreen(new HudInteractionScreen());
                }
                hudSearchWasDown = searchCombo;
            } else {
                hudSearchWasDown = false;
            }

            // Ctrl+Up/Down/Left/Right controls pinned Notes HUD (no overlay open)
            if (HudRenderer.INSTANCE.pinnedNotes && client.currentScreen == null) {
                boolean ctrlHeld = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                                || GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                if (ctrlHeld) {
                    boolean upDown    = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_UP)    == GLFW.GLFW_PRESS;
                    boolean downDown  = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_DOWN)  == GLFW.GLFW_PRESS;
                    boolean leftDown  = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_LEFT)  == GLFW.GLFW_PRESS;
                    boolean rightDown = GLFW.glfwGetKey(hudWin, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
                    if (upDown    && !hudCtrlUpWasDown)    HudRenderer.INSTANCE.scrollNotesUp();
                    if (downDown  && !hudCtrlDownWasDown)  HudRenderer.INSTANCE.scrollNotesDown();
                    if (leftDown  && !hudCtrlLeftWasDown)  HudRenderer.INSTANCE.prevNote();
                    if (rightDown && !hudCtrlRightWasDown) HudRenderer.INSTANCE.nextNote();
                    hudCtrlUpWasDown    = upDown;
                    hudCtrlDownWasDown  = downDown;
                    hudCtrlLeftWasDown  = leftDown;
                    hudCtrlRightWasDown = rightDown;
                } else {
                    hudCtrlUpWasDown    = false;
                    hudCtrlDownWasDown  = false;
                    hudCtrlLeftWasDown  = false;
                    hudCtrlRightWasDown = false;
                }
            }
        });

        IgCalcMod.LOGGER.info("igCalc client initialized.");
    }
}
