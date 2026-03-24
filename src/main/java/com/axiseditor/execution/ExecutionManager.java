package com.axiseditor.execution;

import com.axiseditor.editor.EditorPanel;
import com.axiseditor.filemanager.FileManager;
import com.axiseditor.ui.ConsolePanel;
import com.axiseditor.ui.ErrorPanel;
import com.axiseditor.ui.StatusBar;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * ExecutionManager — Phase 3 + 4 + 5 final version.
 *
 * Phase 3: Save → run via Scilab CLI → stream stdout/stderr to console
 * Phase 4: Full error parsing, red line highlights, gutter icons, ErrorPanel
 * Phase 5: Tab-aware — updateEditorPanel() called on every tab switch so Run
 *          always executes the content of whichever tab is currently active.
 */
public class ExecutionManager {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private       EditorPanel  activeEditor;   // always points to current tab's editor
    private final ConsolePanel consolePanel;
    private final FileManager  fileManager;
    private final StatusBar    statusBar;
    private       ErrorPanel   errorPanel;

    private final ScilabRunner runner = new ScilabRunner();

    // ── State ─────────────────────────────────────────────────────────────────
    private File          tempFile;
    private final StringBuilder stderrBuf = new StringBuilder();

    // ── Toolbar callbacks ─────────────────────────────────────────────────────
    private Runnable onRunStarted;
    private Runnable onRunFinished;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ExecutionManager(EditorPanel   initialEditor,
                            ConsolePanel  consolePanel,
                            FileManager   fileManager,
                            StatusBar     statusBar) {
        this.activeEditor  = initialEditor;
        this.consolePanel  = consolePanel;
        this.fileManager   = fileManager;
        this.statusBar     = statusBar;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setOnRunStarted(Runnable r)        { this.onRunStarted  = r; }
    public void setOnRunFinished(Runnable r)       { this.onRunFinished = r; }
    public void setErrorPanel(ErrorPanel ep)       { this.errorPanel    = ep; }

    /**
     * Phase 5 — called by MainWindow whenever the active tab changes.
     * Keeps Run targeting the correct editor at all times.
     */
    public void updateEditorPanel(EditorPanel ep) {
        this.activeEditor = ep;
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    public void run() {
        if (runner.isRunning()) {
            consolePanel.appendMessage("[Warning] Already running. Stop first.\n");
            return;
        }

        // Resolve the script file to execute
        String scriptPath = resolveScriptPath();
        if (scriptPath == null) return;

        // Prepare UI
        consolePanel.clear();
        consolePanel.appendInfo("▶  Running: " + scriptPath + "\n");
        consolePanel.appendInfo("─".repeat(50) + "\n");
        statusBar.flash("Running script…");

        // Clear previous error state
        if (activeEditor != null) activeEditor.clearErrorHighlights();
        if (errorPanel   != null) errorPanel.clearErrors();
        if (onRunStarted != null) SwingUtilities.invokeLater(onRunStarted);

        stderrBuf.setLength(0);

        // Launch Scilab
        runner
            .onOutput(line -> SwingUtilities.invokeLater(() ->
                    consolePanel.appendMessage(line)))
            .onError(line -> {
                SwingUtilities.invokeLater(() -> consolePanel.appendError(line));
                stderrBuf.append(line).append("\n");
            })
            .onDone(code -> SwingUtilities.invokeLater(() -> handleDone(code)))
            .run(scriptPath);
    }

    /** Kill the running Scilab process. */
    public void stop() {
        runner.stop();
        consolePanel.appendMessage("\n[Execution stopped by user.]\n");
        if (onRunFinished != null) SwingUtilities.invokeLater(onRunFinished);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Return the path to execute. Auto-saves if the file has unsaved changes.
     * Falls back to a temp file for untitled scripts.
     */
    private String resolveScriptPath() {
        // Prefer FileManager.getCurrentEditorPanel() so we always get the
        // active tab's content, even if activeEditor pointer is briefly stale.
        EditorPanel ep = fileManager.getCurrentEditorPanel();
        if (ep == null) ep = activeEditor;
        if (ep == null) {
            consolePanel.appendError("No editor open.\n");
            return null;
        }

        File currentFile = fileManager.getCurrentFile();
        if (currentFile != null) {
            if (fileManager.isModified()) fileManager.saveFile();
            return currentFile.getAbsolutePath();
        }

        // Untitled — write to temp file
        try {
            tempFile = File.createTempFile("axis_run_", ".sce");
            tempFile.deleteOnExit();
            Files.writeString(tempFile.toPath(), ep.getText(), StandardCharsets.UTF_8);
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            consolePanel.appendError("Could not create temp file: " + e.getMessage() + "\n");
            return null;
        }
    }

    /** Called on the EDT when the Scilab process exits. */
    private void handleDone(int exitCode) {
        consolePanel.appendInfo("─".repeat(50) + "\n");

        if (exitCode == 0) {
            consolePanel.appendSuccess("✔  Exited successfully (code 0)\n");
        } else if (exitCode == -1) {
            consolePanel.appendError("⚠  Scilab not found — check SCILAB_HOME.\n");
        } else {
            consolePanel.appendError("✘  Exited with code " + exitCode + "\n");
        }

        // Parse and display all errors
        String stderr = stderrBuf.toString();
        if (!stderr.isBlank()) {
            List<ErrorParser.ParsedError> errors = ErrorParser.parse(stderr);
            if (!errors.isEmpty()) {
                long withLine = errors.stream().filter(e -> e.lineNumber > 0).count();
                consolePanel.appendError(
                    "\n" + errors.size() + " error(s)" +
                    (withLine > 0 ? " — " + withLine + " with line info" : "") + "\n");

                if (activeEditor != null) activeEditor.highlightErrors(errors);
                if (errorPanel   != null) errorPanel.showErrors(errors);

                for (ErrorParser.ParsedError err : errors) {
                    consolePanel.appendError("  " + err + "\n");
                }
            }
        }

        if (onRunFinished != null) onRunFinished.run();
        statusBar.flash(exitCode == 0 ? "Execution complete." : "Execution failed.");

        if (tempFile != null) { tempFile.delete(); tempFile = null; }
    }
}
