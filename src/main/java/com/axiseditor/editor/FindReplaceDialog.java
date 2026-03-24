package com.axiseditor.editor;

import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * FindReplaceDialog — a non-modal find/replace dialog.
 *
 * Features:
 *   • Find Next / Find Previous
 *   • Replace / Replace All
 *   • Case-sensitive toggle
 *   • Whole-word toggle
 *   • Regular-expression toggle
 *   • Wrap-around (always on)
 *   • Match count shown in status label
 *   • Escape closes the dialog
 *
 * Open via:  new FindReplaceDialog(frame, textArea).setVisible(true);
 * or keep one instance and call setVisible(true) each time.
 */
public class FindReplaceDialog extends JDialog {

    private final RTextArea textArea;

    // ── Controls ──────────────────────────────────────────────────────────────
    private final JTextField findField    = new JTextField(30);
    private final JTextField replaceField = new JTextField(30);
    private final JCheckBox  cbCase       = new JCheckBox("Match case");
    private final JCheckBox  cbWholeWord  = new JCheckBox("Whole word");
    private final JCheckBox  cbRegex      = new JCheckBox("Regex");
    private final JLabel     statusLabel  = new JLabel(" ");

    public FindReplaceDialog(Frame owner, RTextArea textArea) {
        super(owner, "Find / Replace", false);   // non-modal
        this.textArea = textArea;
        buildUI();
        pack();
        setLocationRelativeTo(owner);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ── Fields grid ──────────────────────────────────────────────────
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0;
        fieldsPanel.add(new JLabel("Find:"), gc);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;
        fieldsPanel.add(findField, gc);

        gc.gridx = 0; gc.gridy = 1; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        fieldsPanel.add(new JLabel("Replace:"), gc);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;
        fieldsPanel.add(replaceField, gc);

        root.add(fieldsPanel, BorderLayout.NORTH);

        // ── Options row ───────────────────────────────────────────────────
        JPanel optPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        optPanel.add(cbCase);
        optPanel.add(cbWholeWord);
        optPanel.add(cbRegex);
        root.add(optPanel, BorderLayout.CENTER);

        // ── Buttons column ────────────────────────────────────────────────
        JPanel btnPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        btnPanel.setBorder(new EmptyBorder(0, 8, 0, 0));

        JButton btnFindNext    = btn("Find Next",    e -> find(true));
        JButton btnFindPrev    = btn("Find Prev",    e -> find(false));
        JButton btnReplace     = btn("Replace",      e -> replace());
        JButton btnReplaceAll  = btn("Replace All",  e -> replaceAll());
        JButton btnClose       = btn("Close",        e -> setVisible(false));

        btnPanel.add(btnFindNext);
        btnPanel.add(btnFindPrev);
        btnPanel.add(btnReplace);
        btnPanel.add(btnReplaceAll);
        btnPanel.add(Box.createVerticalStrut(6));
        btnPanel.add(btnClose);
        root.add(btnPanel, BorderLayout.EAST);

        // ── Status bar ────────────────────────────────────────────────────
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(Color.GRAY);
        root.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(root);

        // ESC closes the dialog
        getRootPane().registerKeyboardAction(
                e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Enter in find field → find next
        findField.addActionListener(e -> find(true));

        // Make "Find Next" the default button
        getRootPane().setDefaultButton(btnFindNext);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void find(boolean forward) {
        String query = findField.getText();
        if (query.isEmpty()) { status("Enter text to search."); return; }

        SearchContext ctx = buildContext(query);
        ctx.setSearchForward(forward);
        SearchResult result = SearchEngine.find(textArea, ctx);
        if (!result.wasFound()) status("Not found: \"" + query + "\"");
        else                    status("Found match.");
    }

    private void replace() {
        String query = findField.getText();
        if (query.isEmpty()) { status("Enter text to search."); return; }

        SearchContext ctx  = buildContext(query);
        ctx.setReplaceWith(replaceField.getText());
        SearchResult result = SearchEngine.replace(textArea, ctx);
        if (!result.wasFound()) status("Not found.");
        else                    status("Replaced 1 occurrence.");
    }

    private void replaceAll() {
        String query = findField.getText();
        if (query.isEmpty()) { status("Enter text to search."); return; }

        SearchContext ctx  = buildContext(query);
        ctx.setReplaceWith(replaceField.getText());
        SearchResult result = SearchEngine.replaceAll(textArea, ctx);
        int count = result.getCount();
        status(count > 0 ? "Replaced " + count + " occurrence(s)."
                         : "No occurrences found.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SearchContext buildContext(String query) {
        SearchContext ctx = new SearchContext(query);
        ctx.setMatchCase(cbCase.isSelected());
        ctx.setWholeWord(cbWholeWord.isSelected());
        ctx.setRegularExpression(cbRegex.isSelected());
        ctx.setSearchWrap(true);
        return ctx;
    }

    private void status(String msg) {
        statusLabel.setText(msg);
    }

    /** Focus the find field and optionally pre-fill with selected text. */
    public void prefillWithSelection() {
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty()) findField.setText(sel);
        findField.selectAll();
        findField.requestFocusInWindow();
    }

    private JButton btn(String label, ActionListener action) {
        JButton b = new JButton(label);
        b.addActionListener(action);
        b.setFocusPainted(false);
        return b;
    }
}
