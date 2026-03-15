package com.igcalc.config;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class IgCalcConfig {

    private static IgCalcConfig instance;
    private static final String CONFIG_FILE = "igcalc_config.properties";

    // =========================================================================
    // HotkeyBinding record
    // =========================================================================
    public record HotkeyBinding(int keyCode, boolean ctrl, boolean shift) {
        public boolean matches(int key, int mods) {
            return key == keyCode
                    && ctrl  == ((mods & 2) != 0)
                    && shift == ((mods & 1) != 0);
        }
        public String display() {
            String name = GLFW.glfwGetKeyName(keyCode, 0);
            if (name == null) name = "Key " + keyCode;
            String s = name.toUpperCase();
            if (shift) s = "Shift+" + s;
            if (ctrl)  s = "Ctrl+" + s;
            return s;
        }
        public String serialize() {
            return keyCode + "," + ctrl + "," + shift;
        }
        public static HotkeyBinding parse(String s, HotkeyBinding def) {
            if (s == null || s.isEmpty()) return def;
            try {
                String[] parts = s.split(",");
                if (parts.length != 3) return def;
                int     key   = Integer.parseInt(parts[0].trim());
                boolean ctrl  = Boolean.parseBoolean(parts[1].trim());
                boolean shift = Boolean.parseBoolean(parts[2].trim());
                return new HotkeyBinding(key, ctrl, shift);
            } catch (Exception e) { return def; }
        }
    }

    // =========================================================================
    // Configurable in-screen hotkeys (defaults match previous hardcoded values)
    // =========================================================================
    public HotkeyBinding keyNewNote      = new HotkeyBinding(GLFW.GLFW_KEY_N, true,  false);
    public HotkeyBinding keyFindSidebar  = new HotkeyBinding(GLFW.GLFW_KEY_F, true,  true);
    public HotkeyBinding keyQuickNote    = new HotkeyBinding(GLFW.GLFW_KEY_N, true,  true);
    public HotkeyBinding keyInsertCoords = new HotkeyBinding(GLFW.GLFW_KEY_G, true,  true);
    public HotkeyBinding keyCopyClipboard    = new HotkeyBinding(GLFW.GLFW_KEY_E, true, true);
    public HotkeyBinding keyStopwatchToggle  = new HotkeyBinding(GLFW.GLFW_KEY_T, true, true);
    public HotkeyBinding keyStopwatchReset   = new HotkeyBinding(GLFW.GLFW_KEY_R, true, true);

    // =========================================================================
    // Configurable colours
    // =========================================================================
    public int colorBg            = 0xEE1C1E2B;
    public int colorTitleBar      = 0xFF262A3C;
    public int colorAccent        = 0xFF0A84FF;
    public int colorSidebarBg     = 0xFF171A27;
    public int colorSidebarSelect = 0xFF2A4070;
    public int colorBorder        = 0xFF3A3F52;

    // =========================================================================
    // Editor context menu
    // =========================================================================
    private static final Set<String> ALL_CONTEXT_ACTIONS = Set.of(
            "cut", "copy", "paste", "selectAll", "insertCoords",
            "exportClipboard", "newNote", "quickNote", "find");

    public Set<String> enabledContextActions = new HashSet<>(ALL_CONTEXT_ACTIONS);

    public boolean isContextActionEnabled(String key) {
        return enabledContextActions.contains(key);
    }

    public static Set<String> allContextActions() { return ALL_CONTEXT_ACTIONS; }

    private IgCalcConfig() {}

    public static IgCalcConfig getInstance() {
        if (instance == null) instance = new IgCalcConfig();
        return instance;
    }

    // =========================================================================
    // Persistence
    // =========================================================================
    private Path configPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(CONFIG_FILE);
    }

    public void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException ignored) { return; }
        keyNewNote      = HotkeyBinding.parse(props.getProperty("keyNewNote"),      keyNewNote);
        keyFindSidebar  = HotkeyBinding.parse(props.getProperty("keyFindSidebar"),  keyFindSidebar);
        keyQuickNote    = HotkeyBinding.parse(props.getProperty("keyQuickNote"),    keyQuickNote);
        keyInsertCoords = HotkeyBinding.parse(props.getProperty("keyInsertCoords"), keyInsertCoords);
        keyCopyClipboard    = HotkeyBinding.parse(props.getProperty("keyCopyClipboard"), keyCopyClipboard);
        keyStopwatchToggle  = HotkeyBinding.parse(props.getProperty("keyStopwatchToggle"), keyStopwatchToggle);
        keyStopwatchReset   = HotkeyBinding.parse(props.getProperty("keyStopwatchReset"),  keyStopwatchReset);
        colorBg            = hexColor(props, "colorBg",            colorBg);
        colorTitleBar      = hexColor(props, "colorTitleBar",      colorTitleBar);
        colorAccent        = hexColor(props, "colorAccent",        colorAccent);
        colorSidebarBg     = hexColor(props, "colorSidebarBg",     colorSidebarBg);
        colorSidebarSelect = hexColor(props, "colorSidebarSelect", colorSidebarSelect);
        colorBorder        = hexColor(props, "colorBorder",        colorBorder);
        String ctxActions = props.getProperty("contextMenuActions");
        if (ctxActions != null && !ctxActions.isEmpty()) {
            enabledContextActions = new HashSet<>(Arrays.asList(ctxActions.split(",")));
            enabledContextActions.retainAll(ALL_CONTEXT_ACTIONS);
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("keyNewNote",          keyNewNote.serialize());
        props.setProperty("keyFindSidebar",      keyFindSidebar.serialize());
        props.setProperty("keyQuickNote",        keyQuickNote.serialize());
        props.setProperty("keyInsertCoords",     keyInsertCoords.serialize());
        props.setProperty("keyCopyClipboard",    keyCopyClipboard.serialize());
        props.setProperty("keyStopwatchToggle",  keyStopwatchToggle.serialize());
        props.setProperty("keyStopwatchReset",   keyStopwatchReset.serialize());
        props.setProperty("colorBg",             String.format("%08x", colorBg));
        props.setProperty("colorTitleBar",       String.format("%08x", colorTitleBar));
        props.setProperty("colorAccent",         String.format("%08x", colorAccent));
        props.setProperty("colorSidebarBg",      String.format("%08x", colorSidebarBg));
        props.setProperty("colorSidebarSelect",  String.format("%08x", colorSidebarSelect));
        props.setProperty("colorBorder",         String.format("%08x", colorBorder));
        props.setProperty("contextMenuActions", String.join(",", enabledContextActions));
        try (var writer = Files.newBufferedWriter(configPath())) {
            props.store(writer, "Notes+ config");
        } catch (IOException ignored) {}
    }

    private static int hexColor(Properties p, String key, int def) {
        String s = p.getProperty(key);
        if (s == null || s.isEmpty()) return def;
        try { return (int) Long.parseLong(s.replaceAll("^#", ""), 16); }
        catch (NumberFormatException e) { return def; }
    }
}
