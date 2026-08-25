package com.winlator.core;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Temporary on-device diagnostic sink for the LSFG-VK launch and guest process output. */
public abstract class LSFGDiagnostic {
    private static final Object LOCK = new Object();
    private static BufferedWriter writer;

    public static File getFile() {
        File parent = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Winlator");
        if (!parent.isDirectory()) parent.mkdirs();
        return new File(parent, "lsfg-diagnostic.txt");
    }

    public static void start() {
        synchronized (LOCK) {
            closeLocked();
            try {
                writer = new BufferedWriter(new FileWriter(getFile(), false), 32768);
                writeLocked("=== LSFG-VK DEVICE DIAGNOSTIC START ===");
            }
            catch (IOException ignored) {
                writer = null;
            }
        }
    }

    public static void log(String message) {
        synchronized (LOCK) {
            writeLocked(message);
        }
    }

    public static void process(String line) {
        log("[GUEST] " + line);
    }

    public static void finish(int status) {
        synchronized (LOCK) {
            writeLocked("=== GUEST PROCESS EXIT STATUS: " + status + " ===");
            closeLocked();
        }
    }

    private static void writeLocked(String message) {
        if (writer == null) return;
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            writer.write(time + " " + message);
            writer.newLine();
            writer.flush();
        }
        catch (IOException ignored) {}
    }

    private static void closeLocked() {
        if (writer == null) return;
        try {
            writer.flush();
            writer.close();
        }
        catch (IOException ignored) {}
        writer = null;
    }
}
