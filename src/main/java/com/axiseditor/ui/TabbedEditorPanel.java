package com.axiseditor.ui;

import com.axiseditor.editor.EditorPanel;
import com.axiseditor.execution.ErrorParser;
import com.axiseditor.filemanager.FileManager;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * TabbedEditorPanel — Phase 5 multi-tab editor container.
 *
 * Each tab holds its own EditorPanel instance with independent:
 *   • Content / undo history
 *   • File association
 *   • Modified state (shown as ● in the tab title)
 *   • Error highlights
 *
 * Features:
 *   • Ctrl+T          → new tab
 *   • Ctrl+W          → close current tab
 *   • Ctrl+Tab        → cycle to next tab
 *   • Ctrl+Shift+Tab  → cycle to previous tab
 *   • X button on each tab header → close that tab
 *   • Middle-click tab → close that tab
 *   • Tab title shows filename + ● when modified
 *   • Double-click tab bar empty space → new tab
 */
public class TabbedEditorPanel extends JPanel {

    private final JTabbedPane tabbedPane;
    private final FileManager fileManager;
    private final List<TabState> tabs = new ArrayList<>();

    // Callbacks so MainWindow can rewire FileManager when active tab changes
    private java.util.function.Consumer<EditorPanel> onTabChanged;

    // ── Tab state record ──────────────────────────────────────────────────────

    public static class TabState {
        public final EditorPanel editorPanel;
        public       File        file;          // null = Untitled
        public       boolean     modified;
        public       int         tabIndex;

        TabState(EditorPanel editorPanel) {
            this.editorPanel = editorPanel;
            this.file        = null;
            this.modified    = false;
        }

        public String getTitle() {
            String name = file != null ? file.getName() : "Untitled";
            return modified ? name + " ●" : name;
        }

        public String getTooltip() {
            return file != null ? file.getAbsolutePath() : "Untitled — unsaved";
        }
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public TabbedEditorPanel(FileManager fileManager) {
        this.fileManager = fileManager;
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setBackground(new Color(30, 30, 35));
        tabbedPane.setForeground(new Color(200, 200, 200));
        tabbedPane.setFont(UIConstants.LABEL_FONT);

        // Tab change listener — rewire FileManager to the newly selected tab
        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx >= 0 && idx < tabs.size()) {
                TabState state = tabs.get(idx);
                fileManager.attachToTab(state);
                if (onTabChanged != null) onTabChanged.accept(state.editorPanel);
            }
        });

        // Double-click on tab bar → new tab
        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2
                        && tabbedPane.indexAtLocation(e.getX(), e.getY()) < 0) {
                    newTab();
                }
                // Middle-click → close
                if (e.getButton() == MouseEvent.BUTTON2) {
                    int idx = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (idx >= 0) closeTab(idx);
                }
            }
        });

        add(tabbedPane, BorderLayout.CENTER);

        // Install keyboard shortcuts on this panel
        installShortcuts();

        // Open with one blank tab
        newTab();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create a new blank tab and switch to it. */
    public void newTab() {
        EditorPanel ep = new EditorPanel();
        TabState state = new TabState(ep);
        tabs.add(state);

        int idx = tabbedPane.getTabCount();
        state.tabIndex = idx;

        tabbedPane.addTab("Untitled", ep);
        tabbedPane.setTabComponentAt(idx, new TabHeader(state));
        tabbedPane.setToolTipTextAt(idx, state.getTooltip());
        tabbedPane.setSelectedIndex(idx);

        // Hook modified callback
        ep.setOnModifiedCallback(() -> {
            state.modified = true;
            refreshTabTitle(state);
            fileManager.markModifiedExternal();
        });
    }

    /** Open a file in a new tab (or switch to existing tab if already open). */
    public void openFileInTab(File file, String content) {
        // Check if already open
        for (int i = 0; i < tabs.size(); i++) {
            TabState s = tabs.get(i);
            if (file.equals(s.file)) {
                tabbedPane.setSelectedIndex(i);
                return;
            }
        }
        // Open in a new tab
        newTab();
        TabState state = tabs.get(tabbedPane.getSelectedIndex());
        state.file     = file;
        state.modified = false;
        state.editorPanel.setText(content);
        refreshTabTitle(state);
        tabbedPane.setToolTipTextAt(tabbedPane.getSelectedIndex(), state.getTooltip());
    }

    /** Mark the current tab as saved (clears ● indicator). */
    public void markCurrentTabSaved(File file) {
        TabState state = currentState();
        if (state == null) return;
        state.file     = file;
        state.modified = false;
        refreshTabTitle(state);
        tabbedPane.setToolTipTextAt(tabbedPane.getSelectedIndex(), state.getTooltip());
    }

    /** Close the currently active tab. Prompts to save if modified. */
    public void closeCurrentTab() {
        closeTab(tabbedPane.getSelectedIndex());
    }

    /** Close a specific tab by index. */
    public void closeTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) return;
        TabState state = tabs.get(idx);

        if (state.modified) {
            String name = state.getTitle();
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "\"" + name + "\" has unsaved changes. Save before closing?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice == JOptionPane.CANCEL_OPTION) return;
            if (choice == JOptionPane.YES_OPTION) fileManager.saveFile();
        }

        tabs.remove(idx);
        tabbedPane.removeTabAt(idx);

        // Always keep at least one tab open
        if (tabbedPane.getTabCount() == 0) newTab();
    }

    /** Get the EditorPanel of the currently selected tab. */
    public EditorPanel getCurrentEditor() {
        TabState s = currentState();
        return s != null ? s.editorPanel : null;
    }

    /** Get the current tab state. */
    public TabState currentState() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0 || idx >= tabs.size()) return null;
        return tabs.get(idx);
    }

    /** Register callback fired when the active tab changes. */
    public void setOnTabChanged(java.util.function.Consumer<EditorPanel> cb) {
        this.onTabChanged = cb;
    }

    /** Highlight errors on the current tab's editor. */
    public void highlightErrors(List<ErrorParser.ParsedError> errors) {
        EditorPanel ep = getCurrentEditor();
        if (ep != null) ep.highlightErrors(errors);
    }

    /** Clear error highlights on the current tab's editor. */
    public void clearErrorHighlights() {
        EditorPanel ep = getCurrentEditor();
        if (ep != null) ep.clearErrorHighlights();
    }

    /** Total number of open tabs. */
    public int getTabCount() { return tabs.size(); }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void refreshTabTitle(TabState state) {
        int idx = tabs.indexOf(state);
        if (idx < 0) return;
        SwingUtilities.invokeLater(() -> {
            if (idx < tabbedPane.getTabCount()) {
                tabbedPane.setTabComponentAt(idx, new TabHeader(state));
                tabbedPane.revalidate();
            }
        });
    }

    private void installShortcuts() {
        // Ctrl+T → new tab
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), "newTab");
        getActionMap().put("newTab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { newTab(); }
        });

        // Ctrl+W → close current tab
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "closeTab");
        getActionMap().put("closeTab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { closeCurrentTab(); }
        });

        // Ctrl+Tab → next tab
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), "nextTab");
        getActionMap().put("nextTab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int next = (tabbedPane.getSelectedIndex() + 1) % tabbedPane.getTabCount();
                tabbedPane.setSelectedIndex(next);
            }
        });

        // Ctrl+Shift+Tab → previous tab
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "prevTab");
        getActionMap().put("prevTab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int count = tabbedPane.getTabCount();
                int prev  = (tabbedPane.getSelectedIndex() - 1 + count) % count;
                tabbedPane.setSelectedIndex(prev);
            }
        });
    }

    // ── Tab header component (title + X button) ───────────────────────────────

    private class TabHeader extends JPanel {
        TabHeader(TabState state) {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));

            JLabel titleLabel = new JLabel(state.getTitle());
            titleLabel.setFont(UIConstants.LABEL_FONT);
            titleLabel.setForeground(state.modified
                    ? new Color(255, 160, 60)    // orange when modified
                    : new Color(200, 200, 200));  // grey when clean

            JButton closeBtn = new JButton("×");
            closeBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
            closeBtn.setForeground(new Color(160, 160, 160));
            closeBtn.setMargin(new Insets(0, 3, 0, 3));
            closeBtn.setFocusPainted(false);
            closeBtn.setBorderPainted(false);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addActionListener(e -> closeTab(tabs.indexOf(state)));
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    closeBtn.setForeground(new Color(255, 80, 80));
                }
                @Override public void mouseExited(MouseEvent e) {
                    closeBtn.setForeground(new Color(160, 160, 160));
                }
            });

            add(titleLabel);
            add(closeBtn);
        }
    }
}
