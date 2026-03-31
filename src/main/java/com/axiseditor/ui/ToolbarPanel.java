package com.axiseditor.ui;

import com.axiseditor.filemanager.FileManager;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * ToolbarPanel — Phase 5 final version.
 *
 * Buttons (left to right):
 *   New · Open · Open Recent ▾ · Save · Save As  |  + Tab · × Tab  |  ▶ Run · ■ Stop
 *
 * "Open Recent ▾" shows a dropdown of the last 10 opened files.
 * All buttons carry small colour-coded icons drawn at runtime (no image files needed).
 * Keyboard shortcuts are shown in tooltips.
 */
public class ToolbarPanel extends JPanel {

    private final FileManager fileManager;

    private JButton btnNew;
    private JButton btnOpen;
    private JButton btnOpenRecent;
    private JButton btnSave;
    private JButton btnSaveAs;
    private JButton btnNewTab;
    private JButton btnCloseTab;
    private JButton btnRun;
    private JButton btnStop;

    private TabbedEditorPanel tabbedEditor;  // set after construction

    public ToolbarPanel(FileManager fileManager,
                        Object ignoredEditorPanel,   // kept for API compat — not used
                        Object ignoredConsolePanel) {
        this.fileManager = fileManager;
        initUI();
    }

    private void initUI() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 3, 4));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 55, 65)));
        setBackground(UIConstants.TOOLBAR_BG);

        // ── File group ────────────────────────────────────────────────────
        btnNew        = btn("New",       "✦", new Color(100, 180, 255), "New file  (Ctrl+N)",       e -> fileManager.newFile());
        btnOpen       = btn("Open",      "⏏", new Color(160, 200, 255), "Open file  (Ctrl+O)",      e -> fileManager.openFile());
        btnOpenRecent = btn("Recent ▾",  "◷", new Color(160, 160, 210), "Open recent file",          null);
        btnSave       = btn("Save",      "▼", new Color(100, 220, 140), "Save  (Ctrl+S)",            e -> fileManager.saveFile());
        btnSaveAs     = btn("Save As",   "▼", new Color( 70, 180, 110), "Save As  (Ctrl+Shift+S)",  e -> fileManager.saveFileAs());

        btnOpenRecent.addActionListener(e -> showRecentMenu());

        add(btnNew);
        add(btnOpen);
        add(btnOpenRecent);
        add(btnSave);
        add(btnSaveAs);
        addSep();

        // ── Tab group ─────────────────────────────────────────────────────
        
        // ── Execution group ───────────────────────────────────────────────
        btnRun  = btn(" Run",  "▶", new Color( 80, 210,  80), "Run script  (Ctrl+R)", null);
        btnStop = btn(" Stop", "■", new Color(220,  80,  80), "Stop execution",        null);
        btnRun.setEnabled(false);
        btnStop.setEnabled(false);
        add(btnRun);
        add(btnStop);
    }

    // ── Phase 3/5 wiring ─────────────────────────────────────────────────────

    /** Wire Run and Stop actions once ExecutionManager exists. */
    public void wireExecutionActions(ActionListener runAction, ActionListener stopAction) {
        btnRun.addActionListener(runAction);
        btnStop.addActionListener(stopAction);
        btnRun.setEnabled(true);
    }

    /** Swap button states during script execution. */
    public void setRunning(boolean running) {
        btnRun.setEnabled(!running);
        btnStop.setEnabled(running);
    }

    /** Phase 5: wire the tabbed editor so + Tab / × Tab buttons work. */
    public void setTabbedEditor(TabbedEditorPanel te) {
        this.tabbedEditor = te;
    }

    // ── Recent Files popup ────────────────────────────────────────────────────

    private void showRecentMenu() {
        JPopupMenu menu = new JPopupMenu();
        List<File> recents = fileManager.getRecentFilesManager().getRecentFiles();

        if (recents.isEmpty()) {
            JMenuItem empty = new JMenuItem("No recent files");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (File f : recents) {
                String label = f.getName() + "   —   " + abbreviatePath(f);
                JMenuItem item = new JMenuItem(label);
                item.setFont(UIConstants.LABEL_FONT);
                item.setToolTipText(f.getAbsolutePath());
                item.addActionListener(e -> fileManager.openFile(f));
                menu.add(item);
            }
            menu.addSeparator();
            JMenuItem clear = new JMenuItem("Clear Recent Files");
            clear.setForeground(new Color(180, 80, 80));
            clear.addActionListener(e -> fileManager.getRecentFilesManager().clear());
            menu.add(clear);
        }
        menu.show(btnOpenRecent, 0, btnOpenRecent.getHeight());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addSep() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(6, 26));
        add(sep);
    }

    private JButton btn(String text, String iconChar, Color iconColor,
                        String tooltip, ActionListener action) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setFont(UIConstants.BUTTON_FONT);
        b.setIcon(makeIcon(iconChar, iconColor));
        b.setIconTextGap(4);
        if (action != null) b.addActionListener(action);
        return b;
    }

    /** Paint a tiny rounded square with a character symbol inside it. */
    private Icon makeIcon(String sym, Color col) {
        final int S = 14;
        return new Icon() {
            @Override public int getIconWidth()  { return S; }
            @Override public int getIconHeight() { return S; }
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                // Tinted background square
                g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 55));
                g2.fillRoundRect(x, y, S, S, 4, 4);
                // Symbol
                g2.setColor(col);
                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                FontMetrics fm = g2.getFontMetrics();
                int sw = fm.stringWidth(sym);
                g2.drawString(sym, x + (S - sw) / 2, y + S - (S - fm.getAscent()) / 2 - 1);
                g2.dispose();
            }
        };
    }

    /** Show only "…/parentFolder" instead of the full absolute path. */
    private String abbreviatePath(File f) {
        File parent = f.getParentFile();
        if (parent == null) return "";
        File grand  = parent.getParentFile();
        return grand == null ? parent.getName() : "…" + File.separator + parent.getName();
    }
}
