package com.igcalc.config;

import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class IgCalcTutorialState {

    private static Path getFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("igcalc_tutorial.properties");
    }

    public static boolean isCompleted() {
        Path path = getFile();
        if (!Files.exists(path)) return false;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path)) {
            p.load(r);
            return Boolean.parseBoolean(p.getProperty("completed", "false"));
        } catch (IOException e) {
            return false;
        }
    }

    public static void markCompleted() {
        Properties p = new Properties();
        p.setProperty("completed", "true");
        try (Writer w = Files.newBufferedWriter(getFile())) {
            p.store(w, "Notes+ Tutorial State");
        } catch (IOException ignored) {}
    }

    public static void reset() {
        try {
            Files.deleteIfExists(getFile());
        } catch (IOException ignored) {}
    }
}
