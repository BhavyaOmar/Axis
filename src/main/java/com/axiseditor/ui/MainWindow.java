package com.axiseditor.ui;

import com.axiseditor.execution.ExecutionManager;
import com.axiseditor.filemanager.FileManager;
import com.axiseditor.phase6.DemoScriptManager;
import com.axiseditor.phase6.DocumentationViewer;
import com.axiseditor.phase6.TestRunner;
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
    private DocumentationViewer docViewer;
    private JDialog testRunnerDialog;

    public MainWindow() {
        super(UIConstants.APP_TITLE);
        initComponents();
        setupLayout();
        setupMenuBar();
        setupWindowBehaviour();
        setupGlobalShortcuts();
        initPhase6();
    }

    // ── Component wiring ──────────────────────────────────────────────────────

    private void initComponents() {
        fileManager  = new FileManager(this);
        statusBar    = new StatusBar();
        consolePanel = new ConsolePanel();

        tabbedEditor = new TabbedEditorPanel(fileManager);
        fileManager.setTabbedEditor(tabbedEditor);
        fileManager.setStatusBar(statusBar);
        fileManager.setConsolePanel(consolePanel);

        errorPanel = new ErrorPanel(null);
        fileExplorer = new FileExplorerPanel(fileManager);
        toolbarPanel = new ToolbarPanel(fileManager, null, null);
        toolbarPanel.setTabbedEditor(tabbedEditor);

        executionManager = new ExecutionManager(
                tabbedEditor.getCurrentEditor(),
                consolePanel, fileManager, statusBar);
        executionManager.setErrorPanel(errorPanel);
        executionManager.setOnRunStarted(()  -> toolbarPanel.setRunning(true));
        executionManager.setOnRunFinished(() -> toolbarPanel.setRunning(false));
        toolbarPanel.wireExecutionActions(
                e -> runCurrent(),
                e -> executionManager.stop());

        tabbedEditor.setOnTabChanged(ep -> {
            statusBar.attachToEditor(ep.getTextArea());
            fileManager.attachToTab(tabbedEditor.currentState());
            executionManager.updateEditorPanel(ep);
            errorPanel.setEditorPanel(ep);
        });

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

        JSplitPane rightCol = new JSplitPane(JSplitPane.VERTICAL_SPLIT, consolePanel, errorPanel);
        rightCol.setResizeWeight(0.68);rightCol.setDividerSize(5);rightCol.setContinuousLayout(true);

        JSplitPane midRow = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedEditor, rightCol);
        midRow.setResizeWeight(0.72);midRow.setDividerSize(5);midRow.setContinuousLayout(true);

        JSplitPane outerRow = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileExplorer, midRow);
        outerRow.setDividerLocation(220);outerRow.setDividerSize(5);outerRow.setContinuousLayout(true);

        add(outerRow, BorderLayout.CENTER);

        setSize(UIConstants.DEFAULT_WIDTH, UIConstants.DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(960, 580));
        setLocationRelativeTo(null);
    }

    private void setupMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(mi("New", "Ctrl+N", e -> fileManager.newFile()));
        file.add(mi("Open...", "Ctrl+O", e -> fileManager.openFile()));
        file.addSeparator();

        JMenu recent = new JMenu("Recent Files");
        file.add(recent);
        file.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                recent.removeAll();
                var rf = fileManager.getRecentFilesManager().getRecentFiles();
                if (rf.isEmpty()) {
                    JMenuItem em = new JMenuItem("No recent files");
                    em.setEnabled(false);
                    recent.add(em);
                } else {
                    for (java.io.File f : rf) {
                        JMenuItem it = new JMenuItem(f.getName());
                        it.setToolTipText(f.getAbsolutePath());
                        it.addActionListener(ae -> fileManager.openFile(f));
                        recent.add(it);
                    }
                    recent.addSeparator();
                    JMenuItem cl = new JMenuItem("Clear");
                    cl.addActionListener(ae -> fileManager.getRecentFilesManager().clear());
                    recent.add(cl);
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });

        file.addSeparator();
        file.add(mi("Save", "Ctrl+S", e -> fileManager.saveFile()));
        file.add(mi("Save As...", "Ctrl+Shift+S", e -> fileManager.saveFileAs()));
        file.addSeparator();
        file.add(mi("New Tab", "Ctrl+T", e -> tabbedEditor.newTab()));
        file.add(mi("Close Tab", "Ctrl+W", e -> tabbedEditor.closeCurrentTab()));
        file.addSeparator();
        file.add(mi("Exit", null, e -> {
            executionManager.stop();
            fileManager.promptSaveBeforeAction("Exit", this::dispose);
        }));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.setMnemonic('E');
        edit.add(mi("Undo", "Ctrl+Z", e -> editorAction("undo")));
        edit.add(mi("Redo", "Ctrl+Y", e -> editorAction("redo")));
        edit.addSeparator();
        edit.add(mi("Find...", "Ctrl+F", e -> editorAction("axis-find")));
        edit.add(mi("Find & Replace...", "Ctrl+H", e -> editorAction("axis-rep")));
        edit.addSeparator();
        edit.add(mi("Toggle Comment", "Ctrl+/", e -> toggleComment()));
        bar.add(edit);

        JMenu run = new JMenu("Run");
        run.setMnemonic('R');
        run.add(mi("Run Script", "Ctrl+R", e -> runCurrent()));
        run.add(mi("Stop", null, e -> executionManager.stop()));
        bar.add(run);

        JMenu tools = new JMenu("Tools");
        tools.setMnemonic('T');
        tools.add(mi("Test Runner", null, e -> openTestRunner()));
        tools.add(mi("Generate Demo Scripts", null, e -> DemoScriptManager.generateDemos(consolePanel)));
        tools.add(mi("Open Demos Folder", null, e -> {
            java.io.File d = DemoScriptManager.generateDemos(consolePanel);
            fileExplorer.setRoot(d);
        }));
        bar.add(tools);

        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(mi("Scilab Function Reference", "F1", e -> openDocViewer()));
        help.addSeparator();
        help.add(mi("About Axis IDE", null, e -> showAbout()));
        bar.add(help);

        setJMenuBar(bar);
    }

    private void openDocViewer() {
        if (docViewer == null) {
            docViewer = new DocumentationViewer(this);
            docViewer.setOnInsertExample(() -> {
                String ex = docViewer.getSelectedExample();
                if (!ex.isEmpty() && tabbedEditor.getCurrentEditor() != null) {
                    tabbedEditor.getCurrentEditor().getTextArea().replaceSelection(ex);
                }
            });
        }
        docViewer.setVisible(true);
    }

    private void openTestRunner() {
        if (testRunnerDialog == null) {
            TestRunner tr = new TestRunner();
            testRunnerDialog = new JDialog(this, "Test Runner", false);
            testRunnerDialog.setContentPane(tr);
            testRunnerDialog.setSize(700, 440);
            testRunnerDialog.setLocationRelativeTo(this);
        }
        testRunnerDialog.setVisible(true);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
            "Axis Editor and IDE\n" +
            "A Scilab Script Editor — Java Programming Lab Project\n\n" +
            "Phases 1–6 complete.\n\n" +
            "Built with:\n" +
            "  • Java Swing\n" +
            "  • RSyntaxTextArea 3.4.0\n" +
            "  • Apache Maven\n",
            "About Axis IDE", JOptionPane.INFORMATION_MESSAGE);
    }

    private void initPhase6() {
        DemoScriptManager.generateDemos(null);
        getRootPane().registerKeyboardAction(
            e -> openDocViewer(),
            KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    // ── Window behaviour ──────────────────────────────────────────────────────

    private void setupWindowBehaviour() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (executionManager != null) executionManager.stop();
                fileManager.promptSaveBeforeAction("Exit", MainWindow.this::dispose);
            }
        });
    }

    // ── Global keyboard shortcuts ─────────────────────────────────────────────

    private void setupGlobalShortcuts() {
        int C = InputEvent.CTRL_DOWN_MASK, CS = C | InputEvent.SHIFT_DOWN_MASK;
        bind(KeyEvent.VK_N, C, "new", () -> fileManager.newFile());
        bind(KeyEvent.VK_O, C, "open", () -> fileManager.openFile());
        bind(KeyEvent.VK_S, C, "save", () -> fileManager.saveFile());
        bind(KeyEvent.VK_S, CS, "saveAs", () -> fileManager.saveFileAs());
        bind(KeyEvent.VK_R, C, "run", this::runCurrent);
        bind(KeyEvent.VK_T, C, "newTab", () -> tabbedEditor.newTab());
        bind(KeyEvent.VK_W, C, "closeTab", () -> tabbedEditor.closeCurrentTab());
    }

    private void bind(int key, int mods, String id, Runnable action) {
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, mods), id);
        rp.getActionMap().put(id, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void runCurrent() {
        if (tabbedEditor.getCurrentEditor() != null)
            executionManager.updateEditorPanel(tabbedEditor.getCurrentEditor());
        executionManager.run();
    }

    private void editorAction(String key) {
        if (tabbedEditor.getCurrentEditor() == null) return;
        Action a = tabbedEditor.getCurrentEditor().getTextArea().getActionMap().get(key);
        if (a != null) a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, key));
        tabbedEditor.getCurrentEditor().getTextArea().requestFocusInWindow();
        KeyStroke ks = null;
        if ("axis-find".equals(key)) ks = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        if ("axis-rep".equals(key)) ks = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
        if (ks != null) {
            Action act = tabbedEditor.getCurrentEditor().getTextArea().getActionMap().get(key);
            if (act != null) act.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, key));
        }
    }

    private void toggleComment() {
        if (tabbedEditor.getCurrentEditor() == null) return;
        tabbedEditor.getCurrentEditor().getTextArea().requestFocusInWindow();
        KeyEvent ke = new KeyEvent(tabbedEditor.getCurrentEditor().getTextArea(),
                KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_SLASH, '/');
        tabbedEditor.getCurrentEditor().getTextArea().dispatchEvent(ke);
    }

    private JMenuItem mi(String label, String accel, ActionListener action) {
        JMenuItem item = new JMenuItem(label);
        if (accel != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accel.replace("Ctrl", "control").replace("Shift", "shift").replace("+", " ")));
        }
        item.addActionListener(action);
        return item;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public TabbedEditorPanel getTabbedEditor()     { return tabbedEditor;    }
    public FileExplorerPanel getFileExplorer()     { return fileExplorer;    }
    public ConsolePanel      getConsolePanel()     { return consolePanel;    }
    public ErrorPanel        getErrorPanel()       { return errorPanel;      }
    public StatusBar         getStatusBar()        { return statusBar;       }
    public ExecutionManager  getExecutionManager() { return executionManager; }
}
