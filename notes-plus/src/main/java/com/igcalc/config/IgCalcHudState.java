package com.igcalc.config;

import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class IgCalcHudState {

    // Notes HUD
    public static boolean notesHudPinned  = false;
    public static boolean notesHudVisible = true;
    public static int     notesHudX       = -1;
    public static int     notesHudY       = -1;
    public static int     notesHudW       = 220;
    public static int     notesHudH       = 160;
    public static float   notesHudOpacity = 0.85f;

    // Timer HUD
    public static boolean timerHudPinned  = false;
    public static boolean timerHudVisible = true;
    public static int     timerHudX       = -1;
    public static int     timerHudY       = -1;
    public static int     timerHudW       = 150;
    public static int     timerHudH       = 50;
    public static float   timerHudOpacity = 0.9f;

    // Notes window opacity
    public static float notesWindowOpacity = 1.0f;

    // HUD background visibility
    public static boolean notesHudShowBg = true;
    public static boolean timerHudShowBg = true;

    // Global toggle
    public static boolean hudGlobalVisible = true;

    private static Path getFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("igcalc_hud.properties");
    }

    public static void load() {
        Path path = getFile();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path)) {
            p.load(r);
            notesHudPinned   = boolOf(p,  "notes.hud.pinned",   false);
            notesHudVisible  = boolOf(p,  "notes.hud.visible",  true);
            notesHudX        = intOf(p,   "notes.hud.x",        -1);
            notesHudY        = intOf(p,   "notes.hud.y",        -1);
            notesHudW        = intOf(p,   "notes.hud.w",        220);
            notesHudH        = intOf(p,   "notes.hud.h",        160);
            notesHudOpacity  = floatOf(p, "notes.hud.opacity",  0.85f);
            timerHudPinned   = boolOf(p,  "timer.hud.pinned",   false);
            timerHudVisible  = boolOf(p,  "timer.hud.visible",  true);
            timerHudX        = intOf(p,   "timer.hud.x",        -1);
            timerHudY        = intOf(p,   "timer.hud.y",        -1);
            timerHudW        = intOf(p,   "timer.hud.w",        150);
            timerHudH        = intOf(p,   "timer.hud.h",        50);
            timerHudOpacity  = floatOf(p, "timer.hud.opacity",  0.9f);
            hudGlobalVisible = boolOf(p,  "hud.global.visible", true);
            notesHudShowBg      = boolOf(p,  "notes.hud.show.bg",  true);
            timerHudShowBg      = boolOf(p,  "timer.hud.show.bg",  true);
            notesWindowOpacity  = floatOf(p, "notes.window.opacity", 1.0f);
        } catch (IOException ignored) {}
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("notes.hud.pinned",   String.valueOf(notesHudPinned));
        p.setProperty("notes.hud.visible",  String.valueOf(notesHudVisible));
        p.setProperty("notes.hud.x",        String.valueOf(notesHudX));
        p.setProperty("notes.hud.y",        String.valueOf(notesHudY));
        p.setProperty("notes.hud.w",        String.valueOf(notesHudW));
        p.setProperty("notes.hud.h",        String.valueOf(notesHudH));
        p.setProperty("notes.hud.opacity",  String.valueOf(notesHudOpacity));
        p.setProperty("timer.hud.pinned",   String.valueOf(timerHudPinned));
        p.setProperty("timer.hud.visible",  String.valueOf(timerHudVisible));
        p.setProperty("timer.hud.x",        String.valueOf(timerHudX));
        p.setProperty("timer.hud.y",        String.valueOf(timerHudY));
        p.setProperty("timer.hud.w",        String.valueOf(timerHudW));
        p.setProperty("timer.hud.h",        String.valueOf(timerHudH));
        p.setProperty("timer.hud.opacity",  String.valueOf(timerHudOpacity));
        p.setProperty("hud.global.visible", String.valueOf(hudGlobalVisible));
        p.setProperty("notes.hud.show.bg",    String.valueOf(notesHudShowBg));
        p.setProperty("timer.hud.show.bg",    String.valueOf(timerHudShowBg));
        p.setProperty("notes.window.opacity", String.valueOf(notesWindowOpacity));
        try (Writer w = Files.newBufferedWriter(getFile())) {
            p.store(w, "Notes+ HUD State");
        } catch (IOException ignored) {}
    }

    private static int intOf(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static float floatOf(Properties p, String key, float def) {
        try { return Float.parseFloat(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean boolOf(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }
}
