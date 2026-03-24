package com.axiseditor.filemanager;

import com.axiseditor.editor.EditorPanel;
import com.axiseditor.ui.ConsolePanel;
import com.axiseditor.ui.StatusBar;
import com.axiseditor.ui.TabbedEditorPanel;
import com.axiseditor.ui.TabbedEditorPanel.TabState;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * FileManager — Phase 5 upgrade.
 *
 * All file operations are now tab-aware:
 *   • newFile()    → opens a new blank tab
 *   • openFile()   → opens in a new tab (or switches to existing tab)
 *   • saveFile()   → saves current tab's file
 *   • saveFileAs() → save-as for current tab
 *
 * Also integrates RecentFilesManager — every open/save updates the recent list.
 *
 * Phase 5 also adds:
 *   • attachToTab()          — rewires the manager to a tab when switching
 *   • markModifiedExternal() — called by TabbedEditorPanel when user types
 */
public class FileManager {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Window             parentWindow;
    private       StatusBar          statusBar;
    private       ConsolePanel       consolePanel;
    private       TabbedEditorPanel  tabbedEditor;

    // Phase 1-4 compatibility: single-panel mode
    private EditorPanel singleEditorPanel;

    // ── Recent files ──────────────────────────────────────────────────────────
    private final RecentFilesManager recentFilesManager = new RecentFilesManager();

    // ── Current tab state ─────────────────────────────────────────────────────
    private TabState currentTab;

    // ── Legacy single-file state ──────────────────────────────────────────────
    private File    currentFile;
    private boolean isModified;

    private static final FileNameExtensionFilter SCILAB_FILTER =
            new FileNameExtensionFilter("Scilab Scripts (*.sce, *.sci)", "sce", "sci");

    public FileManager(Window parentWindow) {
        this.parentWindow = parentWindow;
    }

    // ── Dependency injection ──────────────────────────────────────────────────

    public void setEditorPanel(EditorPanel ep) {
        this.singleEditorPanel = ep;
        ep.setOnModifiedCallback(this::markModified);
    }

    public void setStatusBar(StatusBar sb)    { this.statusBar  = sb; }
    public void setConsolePanel(ConsolePanel cp) { this.consolePanel = cp; }

    public void setTabbedEditor(TabbedEditorPanel te) {
        this.tabbedEditor = te;
        te.setOnTabChanged(ep -> {
            TabState state = te.currentState();
            attachToTab(state);
        });
    }

    public void attachToTab(TabState state) {
        this.currentTab = state;
        if (state == null) return;
        if (statusBar != null) {
            statusBar.setFilePath(
                state.file != null ? state.file.getAbsolutePath() : null,
                state.modified
            );
        }
    }

    public void markModifiedExternal() {
        if (statusBar != null && currentTab != null) {
            statusBar.setModified(currentTab.modified);
        }
    }

    // ── File actions ──────────────────────────────────────────────────────────

    public void newFile() {
        if (tabbedEditor != null) {
            tabbedEditor.newTab();
        } else {
            promptSaveBeforeAction("New File", () -> {
                singleEditorPanel.setText("");
                currentFile = null;
                isModified  = false;
                updateSingleUI("New untitled file created.\n");
            });
        }
    }

    public void openFile() {
        JFileChooser chooser = buildChooser();
        if (chooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    public void openFile(File file) {
        if (!file.exists()) {
            JOptionPane.showMessageDialog(parentWindow,
                "File not found:\n" + file.getAbsolutePath(),
                "File Not Found", JOptionPane.ERROR_MESSAGE);
            recentFilesManager.remove(file);
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (tabbedEditor != null) {
                tabbedEditor.openFileInTab(file, content);
                TabState state = tabbedEditor.currentState();
                if (state != null) { state.file = file; state.modified = false; }
                recentFilesManager.add(file);
                if (statusBar != null) statusBar.setFilePath(file.getAbsolutePath(), false);
                if (consolePanel != null) consolePanel.appendMessage("Opened: " + file.getAbsolutePath() + "\n");
            } else {
                singleEditorPanel.setText(content);
                currentFile = file;
                isModified  = false;
                recentFilesManager.add(file);
                updateSingleUI("Opened: " + file.getAbsolutePath() + "\n");
            }
        } catch (IOException e) {
            if (consolePanel != null) consolePanel.appendError("Could not read file: " + e.getMessage() + "\n");
        }
    }

    public void saveFile() {
        if (tabbedEditor != null) {
            TabState state = tabbedEditor.currentState();
            if (state == null) return;
            if (state.file == null) { saveFileAs(); return; }
            writeTabToDisk(state, state.file);
        } else {
            if (currentFile == null) { saveFileAs(); return; }
            writeSingleToDisk(currentFile);
        }
    }

    public void saveFileAs() {
        JFileChooser chooser = buildChooser();
        chooser.setDialogTitle("Save Scilab Script");
        File cur = getCurrentFile();
        if (cur != null) chooser.setSelectedFile(cur);
        else chooser.setSelectedFile(new File("untitled.sce"));

        if (chooser.showSaveDialog(parentWindow) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        if (!target.getName().contains("."))
            target = new File(target.getParent(), target.getName() + ".sce");

        if (tabbedEditor != null) {
            TabState state = tabbedEditor.currentState();
            if (state != null) writeTabToDisk(state, target);
        } else {
            writeSingleToDisk(target);
        }
    }

    public void promptSaveBeforeAction(String actionName, Runnable action) {
        boolean dirty = tabbedEditor != null
            ? (tabbedEditor.currentState() != null && tabbedEditor.currentState().modified)
            : isModified;

        if (!dirty) { action.run(); return; }

        String name = "Untitled";
        if (tabbedEditor != null && tabbedEditor.currentState() != null) {
            TabState s = tabbedEditor.currentState();
            name = s.file != null ? s.file.getName() : "Untitled";
        } else if (currentFile != null) {
            name = currentFile.getName();
        }

        int choice = JOptionPane.showConfirmDialog(parentWindow,
            "\"" + name + "\" has unsaved changes.\nSave before " + actionName + "?",
            "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        switch (choice) {
            case JOptionPane.YES_OPTION -> { saveFile(); action.run(); }
            case JOptionPane.NO_OPTION  -> action.run();
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public RecentFilesManager getRecentFilesManager() { return recentFilesManager; }

    public File getCurrentFile() {
        if (tabbedEditor != null && tabbedEditor.currentState() != null)
            return tabbedEditor.currentState().file;
        return currentFile;
    }

    public boolean isModified() {
        if (tabbedEditor != null && tabbedEditor.currentState() != null)
            return tabbedEditor.currentState().modified;
        return isModified;
    }

    public EditorPanel getCurrentEditorPanel() {
        if (tabbedEditor != null) return tabbedEditor.getCurrentEditor();
        return singleEditorPanel;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void writeTabToDisk(TabState state, File target) {
        try {
            Files.writeString(target.toPath(), state.editorPanel.getText(), StandardCharsets.UTF_8);
            state.file     = target;
            state.modified = false;
            tabbedEditor.markCurrentTabSaved(target);
            recentFilesManager.add(target);
            if (statusBar != null) statusBar.setFilePath(target.getAbsolutePath(), false);
            if (consolePanel != null) consolePanel.appendMessage("Saved: " + target.getAbsolutePath() + "\n");
        } catch (IOException e) {
            if (consolePanel != null) consolePanel.appendError("Could not save: " + e.getMessage() + "\n");
        }
    }

    private void writeSingleToDisk(File target) {
        try {
            Files.writeString(target.toPath(), singleEditorPanel.getText(), StandardCharsets.UTF_8);
            currentFile = target;
            isModified  = false;
            recentFilesManager.add(target);
            updateSingleUI("Saved: " + target.getAbsolutePath() + "\n");
        } catch (IOException e) {
            if (consolePanel != null) consolePanel.appendError("Could not save: " + e.getMessage() + "\n");
        }
    }

    private void markModified() {
        if (!isModified) {
            isModified = true;
            if (statusBar != null) statusBar.setModified(true);
        }
    }

    private void updateSingleUI(String msg) {
        if (statusBar != null)
            statusBar.setFilePath(currentFile != null ? currentFile.getAbsolutePath() : null, isModified);
        if (consolePanel != null) consolePanel.appendMessage(msg);
    }

    private JFileChooser buildChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(SCILAB_FILTER);
        chooser.setAcceptAllFileFilterUsed(true);
        File cur = getCurrentFile();
        if (cur != null) chooser.setCurrentDirectory(cur.getParentFile());
        return chooser;
    }
}
