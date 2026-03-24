package com.axiseditor.ui.filetree;

import com.axiseditor.filemanager.FileManager;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * FileExplorerPanel — left sidebar showing a lazy-loaded file tree.
 *
 * Phase 5 features:
 *   • Shows the current working directory (or a chosen root folder)
 *   • Lazy-loads children only when a node is expanded
 *   • Double-click a .sce / .sci file → opens it in the editor
 *   • Right-click context menu: Open, Refresh, Set as Root
 *   • "Browse…" button to pick a new root directory
 *   • Only shows folders and Scilab files (.sce, .sci)
 *   • Dark theme to match the rest of the IDE
 */
public class FileExplorerPanel extends JPanel {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(30, 30, 35);
    private static final Color FG          = new Color(200, 200, 200);
    private static final Color SEL_BG      = new Color(40, 80, 130);
    private static final Color FOLDER_COL  = new Color(220, 180,  80);
    private static final Color SCILAB_COL  = new Color( 80, 200, 150);
    private static final Color OTHER_COL   = new Color(160, 160, 160);

    private final FileManager   fileManager;
    private final JTree         tree;
    private final DefaultTreeModel treeModel;
    private       File          rootDir;

    public FileExplorerPanel(FileManager fileManager) {
        this.fileManager = fileManager;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        setMinimumSize(new Dimension(180, 0));
        setPreferredSize(new Dimension(220, 0));

        // ── Tree setup ────────────────────────────────────────────────────
        FileNode root = new FileNode(new File(System.getProperty("user.home")));
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setBackground(BG);
        tree.setForeground(FG);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);   // single click expands
        tree.setCellRenderer(new FileTreeCellRenderer());
        tree.putClientProperty("JTree.lineStyle", "None");

        // Lazy load children on expand
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                FileNode node = (FileNode) e.getPath().getLastPathComponent();
                node.loadChildren(treeModel);
            }
            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {}
        });

        // Double-click → open file
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    FileNode node = (FileNode) path.getLastPathComponent();
                    if (node.file.isFile() && isScilabFile(node.file)) {
                        fileManager.openFile(node.file);
                    }
                }
                // Right-click context menu
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        FileNode node = (FileNode) path.getLastPathComponent();
                        showContextMenu(node, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setBackground(BG);

        // ── Header bar ────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setBackground(new Color(40, 40, 48));
        header.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 4));

        JLabel titleLabel = new JLabel("Files");
        titleLabel.setFont(UIConstants.LABEL_FONT.deriveFont(Font.BOLD));
        titleLabel.setForeground(FG);
        header.add(titleLabel, BorderLayout.WEST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btnRow.setBackground(new Color(40, 40, 48));

        JButton btnBrowse  = iconButton("…",  "Choose root folder");
        JButton btnRefresh = iconButton("↺",  "Refresh tree");

        btnBrowse.addActionListener(e -> browseForRoot());
        btnRefresh.addActionListener(e -> refresh());

        btnRow.add(btnRefresh);
        btnRow.add(btnBrowse);
        header.add(btnRow, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);

        // Titled border
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 70)),
                "Explorer",
                TitledBorder.LEFT, TitledBorder.TOP,
                UIConstants.LABEL_FONT
        );
        border.setTitleColor(new Color(160, 160, 180));
        setBorder(border);

        // Default root: current working directory
        setRoot(new File(System.getProperty("user.dir")));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Set a new root directory for the file tree. */
    public void setRoot(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        this.rootDir = dir;
        FileNode root = new FileNode(dir);
        root.loadChildren(treeModel);
        treeModel.setRoot(root);
        treeModel.reload();
    }

    /** Refresh the current root. */
    public void refresh() {
        if (rootDir != null) setRoot(rootDir);
    }

    /** Expand to show a specific file in the tree (called after file open). */
    public void revealFile(File file) {
        if (file == null || rootDir == null) return;
        // Best-effort: just refresh so the file appears if it's in the tree
        refresh();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void browseForRoot() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(rootDir != null ? rootDir : new File(System.getProperty("user.home")));
        chooser.setDialogTitle("Choose Project Folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setRoot(chooser.getSelectedFile());
        }
    }

    private void showContextMenu(FileNode node, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        if (node.file.isFile() && isScilabFile(node.file)) {
            JMenuItem openItem = new JMenuItem("Open");
            openItem.addActionListener(e -> fileManager.openFile(node.file));
            menu.add(openItem);
            menu.addSeparator();
        }

        if (node.file.isDirectory()) {
            JMenuItem setRootItem = new JMenuItem("Set as Root");
            setRootItem.addActionListener(e -> setRoot(node.file));
            menu.add(setRootItem);
            menu.addSeparator();
        }

        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.addActionListener(e -> refresh());
        menu.add(refreshItem);

        menu.show(tree, x, y);
    }

    private static boolean isScilabFile(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".sce") || name.endsWith(".sci");
    }

    private JButton iconButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFont(UIConstants.BUTTON_FONT);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setBackground(new Color(55, 55, 65));
        btn.setForeground(FG);
        return btn;
    }

    // ── Tree node ─────────────────────────────────────────────────────────────

    static class FileNode extends DefaultMutableTreeNode {
        final File file;
        boolean loaded = false;

        FileNode(File file) {
            super(file.getName().isEmpty() ? file.getAbsolutePath() : file.getName());
            this.file = file;
            if (file.isDirectory()) {
                // Add a dummy child so the expand arrow appears
                add(new DefaultMutableTreeNode("Loading…"));
            }
        }

        void loadChildren(DefaultTreeModel model) {
            if (loaded) return;
            loaded = true;
            removeAllChildren();

            File[] children = file.listFiles();
            if (children == null) { model.nodeStructureChanged(this); return; }

            // Sort: directories first, then files, both alphabetically
            Arrays.sort(children, Comparator
                    .<File, Boolean>comparing(f -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase()));

            for (File child : children) {
                if (child.isHidden()) continue;
                if (child.isFile() && !isScilabFile(child)) continue;
                add(new FileNode(child));
            }
            model.nodeStructureChanged(this);
        }

        private static boolean isScilabFile(File f) {
            String name = f.getName().toLowerCase();
            return name.endsWith(".sce") || name.endsWith(".sci");
        }
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private class FileTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            setBackground(selected ? SEL_BG : BG);
            setBackgroundNonSelectionColor(BG);
            setBackgroundSelectionColor(SEL_BG);
            setBorderSelectionColor(SEL_BG);

            if (value instanceof FileNode fn) {
                setText(fn.file.getName().isEmpty()
                        ? fn.file.getAbsolutePath() : fn.file.getName());

                if (fn.file.isDirectory()) {
                    setForeground(FOLDER_COL);
                    setIcon(expanded
                            ? UIManager.getIcon("Tree.openIcon")
                            : UIManager.getIcon("Tree.closedIcon"));
                } else if (isScilabFile(fn.file)) {
                    setForeground(SCILAB_COL);
                    setIcon(UIManager.getIcon("Tree.leafIcon"));
                } else {
                    setForeground(OTHER_COL);
                }
            }

            if (!selected) setBackground(BG);
            setOpaque(true);
            return this;
        }
    }
}
