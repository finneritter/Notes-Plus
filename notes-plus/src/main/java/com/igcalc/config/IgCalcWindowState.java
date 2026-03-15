package com.igcalc.config;

import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class IgCalcWindowState {

    // Notes window state
    public static int     notesX       = -1;   // -1 = not yet set (centre on first open)
    public static int     notesY       = -1;
    public static int     notesW       = 380;
    public static int     notesH       = 280;
    public static boolean notesSidebar = true;

    // Last-opened note file (to restore on re-open)
    public static String notesLastFile = null;

    // Calculator window state
    public static int calcX = -1;
    public static int calcY = -1;
    public static int calcW = 160;
    public static int calcH = 256;

    // Timer window state
    public static int timerX = -1;
    public static int timerY = -1;

    private static Path getFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("igcalc_window.properties");
    }

    public static void load() {
        Path path = getFile();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path)) {
            p.load(r);
            notesX       = intOf(p,  "notes.x",       -1);
            notesY       = intOf(p,  "notes.y",       -1);
            notesW       = intOf(p,  "notes.w",       380);
            notesH       = intOf(p,  "notes.h",       280);
            notesSidebar  = boolOf(p, "notes.sidebar", true);
            String lf     = p.getProperty("notes.lastFile", "");
            notesLastFile = (lf == null || lf.isEmpty()) ? null : lf;
            calcX         = intOf(p,  "calc.x",        -1);
            calcY        = intOf(p,  "calc.y",        -1);
            calcW        = intOf(p,  "calc.w",        160);
            calcH        = intOf(p,  "calc.h",        210);
            timerX       = intOf(p,  "timer.x",       -1);
            timerY       = intOf(p,  "timer.y",       -1);
        } catch (IOException ignored) {}
        IgCalcHudState.load();
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("notes.x",       String.valueOf(notesX));
        p.setProperty("notes.y",       String.valueOf(notesY));
        p.setProperty("notes.w",       String.valueOf(notesW));
        p.setProperty("notes.h",       String.valueOf(notesH));
        p.setProperty("notes.sidebar",  String.valueOf(notesSidebar));
        p.setProperty("notes.lastFile", notesLastFile != null ? notesLastFile : "");
        p.setProperty("calc.x",        String.valueOf(calcX));
        p.setProperty("calc.y",        String.valueOf(calcY));
        p.setProperty("calc.w",        String.valueOf(calcW));
        p.setProperty("calc.h",        String.valueOf(calcH));
        p.setProperty("timer.x",       String.valueOf(timerX));
        p.setProperty("timer.y",       String.valueOf(timerY));
        try (Writer w = Files.newBufferedWriter(getFile())) {
            p.store(w, "igCalc Window State");
        } catch (IOException ignored) {}
        IgCalcHudState.save();
    }

    private static int intOf(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean boolOf(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }
}
