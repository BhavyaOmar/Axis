package com.axiseditor.ui;

import com.axiseditor.execution.ExecutionManager;
import com.axiseditor.filemanager.FileManager;
import com.axiseditor.ui.filetree.FileExplorerPanel;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * MainWindow — Phase 5 final layout.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Toolbar: New · Open · Recent▾ · Save · SaveAs · Run · Stop │
 * ├──────────────┬──────────────────────────┬───────────────────┤
 * │              │  Tab1 │ Tab2 │ + Tab      │  Console Output   │
 * │  File        ├──────────────────────────┼───────────────────┤
 * │  Explorer    │  Editor (active tab)      │  Error Panel      │
 * ├──────────────┴──────────────────────────┴───────────────────┤
 * │  filename ●          Ln 1, Col 1              Scilab .sce    │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Global shortcuts:
 *   Ctrl+N          New file / new tab
 *   Ctrl+O          Open file
 *   Ctrl+S          Save
 *   Ctrl+Shift+S    Save As
 *   Ctrl+T          New tab
 *   Ctrl+W          Close current tab
 *   Ctrl+R          Run script
 */
public class MainWindow extends JFrame {

    private TabbedEditorPanel tabbedEditor;
    private FileExplorerPanel fileExplorer;
    private ConsolePanel      consolePanel;
    private ErrorPanel        errorPanel;
    private ToolbarPanel      toolbarPanel;
    private StatusBar         statusBar;
    private FileManager       fileManager;
    private ExecutionManager  executionManager;

    public MainWindow() {
        super(UIConstants.APP_TITLE);
        initComponents();
        setupLayout();
        setupWindowBehaviour();
        setupGlobalShortcuts();
    }

    // ── Component wiring ──────────────────────────────────────────────────────

    private void initComponents() {
        // ── Core services ─────────────────────────────────────────────────
        fileManager  = new FileManager(this);
        statusBar    = new StatusBar();
        consolePanel = new ConsolePanel();

        // ── Tabbed editor ─────────────────────────────────────────────────
        tabbedEditor = new TabbedEditorPanel(fileManager);
        fileManager.setTabbedEditor(tabbedEditor);
        fileManager.setStatusBar(statusBar);
        fileManager.setConsolePanel(consolePanel);

        // ── Error panel (starts with null editor — rewired on tab change) ──
        errorPanel = new ErrorPanel(null);

        // ── File explorer sidebar ─────────────────────────────────────────
        fileExplorer = new FileExplorerPanel(fileManager);

        // ── Toolbar ───────────────────────────────────────────────────────
        toolbarPanel = new ToolbarPanel(fileManager, null, null);
        toolbarPanel.setTabbedEditor(tabbedEditor);

        // ── Execution manager ─────────────────────────────────────────────
        executionManager = new ExecutionManager(
                tabbedEditor.getCurrentEditor(),
                consolePanel, fileManager, statusBar);
        executionManager.setErrorPanel(errorPanel);
        executionManager.setOnRunStarted(()  -> toolbarPanel.setRunning(true));
        executionManager.setOnRunFinished(() -> toolbarPanel.setRunning(false));
        toolbarPanel.wireExecutionActions(
                e -> runCurrentScript(),
                e -> executionManager.stop());

        // ── Tab-change callback — single registration ──────────────────────
        // Updates: StatusBar Ln/Col · FileManager active tab ·
        //          ExecutionManager editor ref · ErrorPanel editor ref
        tabbedEditor.setOnTabChanged(ep -> {
            statusBar.attachToEditor(ep.getTextArea());
            fileManager.attachToTab(tabbedEditor.currentState());
            executionManager.updateEditorPanel(ep);
            errorPanel.setEditorPanel(ep);
        });

        // Attach status bar to the initial tab
        if (tabbedEditor.getCurrentEditor() != null) {
            statusBar.attachToEditor(tabbedEditor.getCurrentEditor().getTextArea());
            errorPanel.setEditorPanel(tabbedEditor.getCurrentEditor());
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void setupLayout() {
        setLayout(new BorderLayout());
        add(toolbarPanel, BorderLayout.NORTH);
        add(statusBar,    BorderLayout.SOUTH);

        // Right column: console (top) + error panel (bottom)
        JSplitPane rightCol = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, consolePanel, errorPanel);
        rightCol.setResizeWeight(0.68);
        rightCol.setDividerSize(5);
        rightCol.setContinuousLayout(true);

        // Middle area: tabbed editor (left) + right column (right)
        JSplitPane midRow = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, tabbedEditor, rightCol);
        midRow.setResizeWeight(0.72);
        midRow.setDividerSize(5);
        midRow.setContinuousLayout(true);

        // Full area: file explorer (left) + middle area (right)
        JSplitPane outerRow = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, fileExplorer, midRow);
        outerRow.setDividerLocation(220);
        outerRow.setDividerSize(5);
        outerRow.setContinuousLayout(true);

        add(outerRow, BorderLayout.CENTER);

        setSize(UIConstants.DEFAULT_WIDTH, UIConstants.DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(960, 580));
        setLocationRelativeTo(null);
    }

    // ── Window behaviour ──────────────────────────────────────────────────────

    private void setupWindowBehaviour() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (executionManager != null) executionManager.stop();
                fileManager.promptSaveBeforeAction("Exit", MainWindow.this::dispose);
            }
        });
    }

    // ── Global keyboard shortcuts ─────────────────────────────────────────────

    private void setupGlobalShortcuts() {
        int C  = InputEvent.CTRL_DOWN_MASK;
        int CS = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

        bind(KeyEvent.VK_N, C,  "new",     () -> fileManager.newFile());
        bind(KeyEvent.VK_O, C,  "open",    () -> fileManager.openFile());
        bind(KeyEvent.VK_S, C,  "save",    () -> fileManager.saveFile());
        bind(KeyEvent.VK_S, CS, "saveAs",  () -> fileManager.saveFileAs());
        bind(KeyEvent.VK_R, C,  "run",     this::runCurrentScript);
        bind(KeyEvent.VK_T, C,  "newTab",  () -> tabbedEditor.newTab());
        bind(KeyEvent.VK_W, C,  "closeTab",() -> tabbedEditor.closeCurrentTab());
    }

    private void bind(int key, int mods, String id, Runnable action) {
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(key, mods), id);
        rp.getActionMap().put(id, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    private void runCurrentScript() {
        if (tabbedEditor.getCurrentEditor() != null)
            executionManager.updateEditorPanel(tabbedEditor.getCurrentEditor());
        executionManager.run();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public TabbedEditorPanel getTabbedEditor()     { return tabbedEditor;    }
    public FileExplorerPanel getFileExplorer()     { return fileExplorer;    }
    public ConsolePanel      getConsolePanel()     { return consolePanel;    }
    public ErrorPanel        getErrorPanel()       { return errorPanel;      }
    public StatusBar         getStatusBar()        { return statusBar;       }
    public ExecutionManager  getExecutionManager() { return executionManager; }
}
