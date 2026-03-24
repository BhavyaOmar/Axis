package com.axiseditor.ui;

import com.axiseditor.editor.EditorPanel;
import com.axiseditor.execution.ErrorParser;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * ErrorPanel — Phase 4 + 5 final version.
 *
 * Structured error list below the console.
 * Each row shows: ● Line N   error message
 * Clicking a row scrolls the editor to that line and selects it.
 *
 * Phase 5 update: setEditorPanel() allows rewiring to a new tab's editor
 * when the active tab changes, so click-to-jump always works correctly.
 */
public class ErrorPanel extends JPanel {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG         = new Color(28,  20,  20);
    private static final Color ROW_BG     = new Color(38,  28,  28);
    private static final Color ROW_BG_SEL = new Color(80,  30,  30);
    private static final Color FG_LINE    = new Color(255, 100, 100);
    private static final Color FG_MSG     = new Color(220, 180, 180);

    // ── State ─────────────────────────────────────────────────────────────────
    private       EditorPanel activeEditor;   // updated on tab switch
    private final DefaultListModel<ErrorParser.ParsedError> listModel;
    private final JList<ErrorParser.ParsedError>            errorList;
    private final JLabel                                    countLabel;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * @param initialEditor may be null; call setEditorPanel() after tab wiring.
     */
    public ErrorPanel(EditorPanel initialEditor) {
        this.activeEditor = initialEditor;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(50, 30, 30));
        header.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        countLabel = new JLabel("No errors");
        countLabel.setFont(UIConstants.LABEL_FONT.deriveFont(Font.BOLD));
        countLabel.setForeground(FG_LINE);
        header.add(countLabel, BorderLayout.WEST);

        JButton btnClear = new JButton("Clear");
        btnClear.setFont(UIConstants.BUTTON_FONT);
        btnClear.setFocusPainted(false);
        btnClear.setMargin(new Insets(1, 6, 1, 6));
        btnClear.addActionListener(e -> clearErrors());
        header.add(btnClear, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // List
        listModel = new DefaultListModel<>();
        errorList = new JList<>(listModel);
        errorList.setBackground(BG);
        errorList.setForeground(FG_MSG);
        errorList.setFont(UIConstants.CONSOLE_FONT);
        errorList.setCellRenderer(new ErrorCellRenderer());
        errorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Click → jump to line
        errorList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = errorList.locationToIndex(e.getPoint());
                if (idx >= 0) jumpToLine(listModel.get(idx));
            }
        });
        errorList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    ErrorParser.ParsedError sel = errorList.getSelectedValue();
                    if (sel != null) jumpToLine(sel);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(errorList);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // Border
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 40, 40)),
            "Errors", TitledBorder.LEFT, TitledBorder.TOP, UIConstants.LABEL_FONT);
        border.setTitleColor(FG_LINE);
        setBorder(border);
        setPreferredSize(new Dimension(0, 130));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Phase 5: rewire to a new tab's editor when the active tab changes. */
    public void setEditorPanel(EditorPanel ep) {
        this.activeEditor = ep;
    }

    /** Display a list of parsed errors. */
    public void showErrors(List<ErrorParser.ParsedError> errors) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (ErrorParser.ParsedError err : errors) listModel.addElement(err);
            int n = errors.size();
            countLabel.setText(n == 0 ? "No errors"
                : n + " error" + (n > 1 ? "s" : "") + " — click to jump");
        });
    }

    /** Remove all rows. */
    public void clearErrors() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            countLabel.setText("No errors");
        });
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void jumpToLine(ErrorParser.ParsedError err) {
        if (err.lineNumber < 1 || activeEditor == null) return;
        try {
            int idx   = err.lineNumber - 1;
            int start = activeEditor.getTextArea().getLineStartOffset(idx);
            int end   = activeEditor.getTextArea().getLineEndOffset(idx);
            activeEditor.getTextArea().setCaretPosition(start);
            activeEditor.getTextArea().setSelectionStart(start);
            activeEditor.getTextArea().setSelectionEnd(end);
            activeEditor.getTextArea().requestFocusInWindow();
        } catch (Exception ignored) {}
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private class ErrorCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean selected, boolean focused) {

            ErrorParser.ParsedError err = (ErrorParser.ParsedError) value;
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(selected ? ROW_BG_SEL : (index % 2 == 0 ? ROW_BG : BG));
            row.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel lineLabel = new JLabel("● " + (err.lineNumber > 0 ? "Line " + err.lineNumber : "?"));
            lineLabel.setFont(UIConstants.CONSOLE_FONT.deriveFont(Font.BOLD));
            lineLabel.setForeground(FG_LINE);
            lineLabel.setPreferredSize(new Dimension(80, 0));
            row.add(lineLabel, BorderLayout.WEST);

            JLabel msgLabel = new JLabel(err.message);
            msgLabel.setFont(UIConstants.CONSOLE_FONT);
            msgLabel.setForeground(selected ? Color.WHITE : FG_MSG);
            row.add(msgLabel, BorderLayout.CENTER);

            return row;
        }
    }
}
