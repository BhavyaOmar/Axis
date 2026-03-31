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

public class ExecutionManager {

    private       EditorPanel  activeEditor;
    private final ConsolePanel consolePanel;
    private final FileManager  fileManager;
    private final StatusBar    statusBar;
    private       ErrorPanel   errorPanel;

    private final ScilabRunner runner = new ScilabRunner();

    private File          tempFile;
    private final StringBuilder stderrBuf = new StringBuilder();

    private Runnable onRunStarted;
    private Runnable onRunFinished;

    public ExecutionManager(EditorPanel   initialEditor,
                            ConsolePanel  consolePanel,
                            FileManager   fileManager,
                            StatusBar     statusBar) {
        this.activeEditor  = initialEditor;
        this.consolePanel  = consolePanel;
        this.fileManager   = fileManager;
        this.statusBar     = statusBar;

        // Wire the console to the runner ONCE here in the constructor.
        // ConsolePanel holds a reference to runner so its Send button can
        // call runner.sendInput(). This never changes — same runner, same panel.
        consolePanel.setRunner(runner);
    }

    public void setOnRunStarted(Runnable r)  { this.onRunStarted  = r; }
    public void setOnRunFinished(Runnable r) { this.onRunFinished = r; }
    public void setErrorPanel(ErrorPanel ep) { this.errorPanel    = ep; }

    public void updateEditorPanel(EditorPanel ep) {
        this.activeEditor = ep;
    }

    public void run() {
        if (runner.isRunning()) {
            consolePanel.appendMessage("[Warning] Already running. Stop first.\n");
            return;
        }

        String scriptPath = resolveScriptPath();
        if (scriptPath == null) return;

        consolePanel.clear();
        consolePanel.appendInfo("▶  Running: " + scriptPath + "\n");
        consolePanel.appendInfo("   Scilab:  " + ScilabRunner.detectScilabPath() + "\n");
        consolePanel.appendInfo("─".repeat(50) + "\n");
        statusBar.flash("Running script…");

        if (activeEditor != null) activeEditor.clearErrorHighlights();
        if (errorPanel   != null) errorPanel.clearErrors();
        if (onRunStarted != null) SwingUtilities.invokeLater(onRunStarted);

        stderrBuf.setLength(0);

        // Enable the input bar immediately — the user may need to type
        // at any point while the script runs, not just when a prompt is detected.
        // This is how all terminal emulators work.
        consolePanel.setWaitingForInput(true);

        runner
            .onOutput(line -> SwingUtilities.invokeLater(() ->
                    consolePanel.appendMessage(line)))
            .onError(line -> {
                SwingUtilities.invokeLater(() -> consolePanel.appendError(line));
                stderrBuf.append(line).append("\n");
            })
            .onDone(code -> SwingUtilities.invokeLater(() -> handleDone(code)))
            .onPrompt(prompt -> SwingUtilities.invokeLater(() ->
                    consolePanel.appendPrompt(prompt)))
            .run(scriptPath);
    }

    public void stop() {
        runner.stop();
        consolePanel.setWaitingForInput(false);
        consolePanel.appendMessage("\n[Execution stopped by user.]\n");
        if (onRunFinished != null) SwingUtilities.invokeLater(onRunFinished);
    }

    private String resolveScriptPath() {
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

    private void handleDone(int exitCode) {
        consolePanel.appendInfo("─".repeat(50) + "\n");

        if (exitCode == 0) {
            consolePanel.appendSuccess("✔  Exited successfully (code 0)\n");
        } else if (exitCode == -1) {
            consolePanel.appendError("⚠  Scilab not found — check SCILAB_HOME.\n");
        } else {
            consolePanel.appendError("✘  Exited with code " + exitCode + "\n");
        }

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

        consolePanel.setWaitingForInput(false);
        if (onRunFinished != null) onRunFinished.run();
        statusBar.flash(exitCode == 0 ? "Execution complete." : "Execution failed.");

        if (tempFile != null) { tempFile.delete(); tempFile = null; }
    }
}