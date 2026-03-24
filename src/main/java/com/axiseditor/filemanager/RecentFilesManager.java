package com.axiseditor.filemanager;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * RecentFilesManager — persists the list of recently opened files.
 *
 * Storage: ~/.axiseditor/recent.properties  (user home directory)
 * Capacity: MAX_FILES most-recent entries (oldest removed when full)
 *
 * The list is ordered newest-first.
 * Listeners are notified whenever the list changes so the menu can rebuild.
 */
public class RecentFilesManager {

    private static final int    MAX_FILES     = 10;
    private static final String CONFIG_DIR    = ".axiseditor";
    private static final String RECENT_FILE   = "recent.properties";
    private static final String KEY_PREFIX    = "recent.";

    private final Path                  configPath;
    private final LinkedList<File>      recentFiles = new LinkedList<>();
    private final List<Runnable>        listeners   = new ArrayList<>();

    public RecentFilesManager() {
        configPath = Paths.get(System.getProperty("user.home"), CONFIG_DIR, RECENT_FILE);
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Add a file to the top of the recent list and persist. */
    public void add(File file) {
        if (file == null || !file.exists()) return;
        // Remove if already present (re-inserts at top)
        recentFiles.removeIf(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));
        recentFiles.addFirst(file);
        // Trim to max size
        while (recentFiles.size() > MAX_FILES) recentFiles.removeLast();
        save();
        notifyListeners();
    }

    /** Remove a file from the list (e.g. if it no longer exists). */
    public void remove(File file) {
        recentFiles.removeIf(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));
        save();
        notifyListeners();
    }

    /** Return an unmodifiable view of the recent files (newest first). */
    public List<File> getRecentFiles() {
        return Collections.unmodifiableList(recentFiles);
    }

    /** Clear the entire list. */
    public void clear() {
        recentFiles.clear();
        save();
        notifyListeners();
    }

    /** Register a callback invoked whenever the list changes. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(configPath)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
            // Read in order: recent.0, recent.1, ...
            for (int i = 0; i < MAX_FILES; i++) {
                String val = props.getProperty(KEY_PREFIX + i);
                if (val == null || val.isBlank()) break;
                File f = new File(val);
                if (f.exists()) recentFiles.addLast(f);
            }
        } catch (IOException ignored) {}
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            int i = 0;
            for (File f : recentFiles) {
                props.setProperty(KEY_PREFIX + i, f.getAbsolutePath());
                i++;
            }
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Axis IDE — Recent Files");
            }
        } catch (IOException ignored) {}
    }

    private void notifyListeners() {
        for (Runnable r : listeners) r.run();
    }
}
