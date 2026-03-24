package com.axiseditor.ui;

import com.axiseditor.utils.UIConstants;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.*;

/**
 * StatusBar — Phase 5 upgrade.
 *
 * Left section:  file path (or "Untitled") + ● unsaved indicator
 * Centre section: line : column display (live as caret moves)
 * Right section:  file type badge ("Scilab .sce")
 *
 * Call attachToEditor(RSyntaxTextArea) after tab switches to keep
 * line/col live for the active editor.
 */
public class StatusBar extends JPanel {

    private final JLabel fileLabel;
    private final JLabel modifiedLabel;
    private final JLabel lineColLabel;
    private final JLabel fileTypeLabel;

    private RSyntaxTextArea attachedEditor;
    private CaretListener   caretListener;

    public StatusBar() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(55, 55, 65)),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        setBackground(UIConstants.STATUSBAR_BG);

        // ── Left: file path + modified ─────────────────────────────────────
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        fileLabel = new JLabel("No file open");
        fileLabel.setFont(UIConstants.LABEL_FONT);

        modifiedLabel = new JLabel("");
        modifiedLabel.setFont(UIConstants.LABEL_FONT.deriveFont(Font.BOLD));
        modifiedLabel.setForeground(new Color(220, 120, 40));

        leftPanel.add(fileLabel);
        leftPanel.add(modifiedLabel);
        add(leftPanel, BorderLayout.WEST);

        // ── Centre: line / column ──────────────────────────────────────────
        lineColLabel = new JLabel("Ln 1, Col 1");
        lineColLabel.setFont(UIConstants.LABEL_FONT);
        lineColLabel.setForeground(new Color(120, 120, 140));
        lineColLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(lineColLabel, BorderLayout.CENTER);

        // ── Right: file type ───────────────────────────────────────────────
        fileTypeLabel = new JLabel("Scilab");
        fileTypeLabel.setFont(UIConstants.LABEL_FONT);
        fileTypeLabel.setForeground(new Color(80, 180, 140));
        fileTypeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(fileTypeLabel, BorderLayout.EAST);

        setFilePath(null, false);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Update the file path and modified indicator.
     * Called by FileManager after open/save/tab-switch.
     */
    public void setFilePath(String path, boolean modified) {
        if (path != null) {
            // Show only the filename; tooltip shows full path
            String name = new java.io.File(path).getName();
            fileLabel.setText(name);
            fileLabel.setToolTipText(path);

            // Update file type badge
            if (path.endsWith(".sci")) {
                fileTypeLabel.setText("Scilab .sci");
            } else if (path.endsWith(".sce")) {
                fileTypeLabel.setText("Scilab .sce");
            } else {
                fileTypeLabel.setText("Text");
            }
        } else {
            fileLabel.setText("Untitled");
            fileLabel.setToolTipText(null);
            fileTypeLabel.setText("Scilab");
        }
        setModified(modified);
    }

    /** Flip the modified ● indicator without changing the path. */
    public void setModified(boolean modified) {
        modifiedLabel.setText(modified ? "  ●" : "");
    }

    /** Show a temporary message in the file label (reverts after 3 seconds). */
    public void flash(String message) {
        String original = fileLabel.getText();
        Color  originalColor = fileLabel.getForeground();
        fileLabel.setText(message);
        fileLabel.setForeground(new Color(80, 180, 120));
        Timer t = new Timer(3000, e -> {
            fileLabel.setText(original);
            fileLabel.setForeground(originalColor);
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * Attach a caret listener to the given RSyntaxTextArea so
     * line/column updates live as the cursor moves.
     * Call this whenever the active tab changes.
     */
    public void attachToEditor(RSyntaxTextArea editor) {
        // Detach from previous editor
        if (attachedEditor != null && caretListener != null) {
            attachedEditor.removeCaretListener(caretListener);
        }
        attachedEditor = editor;
        if (editor == null) {
            lineColLabel.setText("Ln 1, Col 1");
            return;
        }
        caretListener = (CaretEvent e) -> updateLineCol(editor);
        editor.addCaretListener(caretListener);
        updateLineCol(editor);  // show immediately
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void updateLineCol(RSyntaxTextArea editor) {
        try {
            int caret = editor.getCaretPosition();
            int line  = editor.getLineOfOffset(caret);         // 0-based
            int col   = caret - editor.getLineStartOffset(line); // 0-based
            lineColLabel.setText("Ln " + (line + 1) + ", Col " + (col + 1));
        } catch (Exception ignored) {
            lineColLabel.setText("Ln ?, Col ?");
        }
    }
}
