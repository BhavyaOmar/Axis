package com.axiseditor.ui;

import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;

/**
 * ConsolePanel — output area for file events (Phase 1) and script execution (Phase 3).
 *
 * Phase 3 upgrades:
 *   • JTextPane replaces JTextArea to support coloured text runs.
 *   • appendMessage()  → white text  (normal output)
 *   • appendError()    → red text    (stderr / errors)
 *   • appendSuccess()  → green text  (success messages)
 *   • appendInfo()     → cyan text   (separators, meta-messages)
 */
public class ConsolePanel extends JPanel {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COL_NORMAL  = new Color(212, 212, 212);  // light grey
    private static final Color COL_ERROR   = new Color(244, 135, 113);  // salmon red
    private static final Color COL_SUCCESS = new Color(106, 200, 100);  // green
    private static final Color COL_INFO    = new Color( 86, 182, 194);  // cyan

    private final JTextPane outputPane;
    private final StyledDocument doc;

    // ── Style constants ───────────────────────────────────────────────────────
    private static final String STYLE_NORMAL  = "normal";
    private static final String STYLE_ERROR   = "error";
    private static final String STYLE_SUCCESS = "success";
    private static final String STYLE_INFO    = "info";

    public ConsolePanel() {
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(220, 0));

        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setFont(UIConstants.CONSOLE_FONT);
        outputPane.setBackground(UIConstants.CONSOLE_BG);
        outputPane.setForeground(COL_NORMAL);
        outputPane.setMargin(new Insets(6, 8, 6, 8));
        doc = outputPane.getStyledDocument();
        defineStyles();

        JScrollPane scrollPane = new JScrollPane(outputPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.getViewport().setBackground(UIConstants.CONSOLE_BG);

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70), 1),
                "Console Output",
                TitledBorder.LEFT, TitledBorder.TOP,
                UIConstants.LABEL_FONT
        );
        border.setTitleColor(Color.LIGHT_GRAY);
        setBorder(border);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom bar: Clear button
        JButton btnClear = new JButton("Clear");
        btnClear.setFont(UIConstants.BUTTON_FONT);
        btnClear.setFocusPainted(false);
        btnClear.addActionListener(e -> clear());
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        bottomBar.setBackground(new Color(40, 40, 40));
        bottomBar.add(btnClear);
        add(bottomBar, BorderLayout.SOUTH);

        appendInfo("Axis IDE — Console ready.\n");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Normal output line — light grey. */
    public void appendMessage(String text) {
        append(text, STYLE_NORMAL);
    }

    /** Error output — red. */
    public void appendError(String text) {
        append(text, STYLE_ERROR);
    }

    /** Success message — green. */
    public void appendSuccess(String text) {
        append(text, STYLE_SUCCESS);
    }

    /** Info / meta message — cyan. */
    public void appendInfo(String text) {
        append(text, STYLE_INFO);
    }

    /** Clear all console content. */
    public void clear() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) { }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void append(String text, String styleName) {
        // Always safe to call from any thread
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> append(text, styleName));
            return;
        }
        try {
            doc.insertString(doc.getLength(), text, doc.getStyle(styleName));
            scrollToBottom();
        } catch (BadLocationException ignored) { }
    }

    private void scrollToBottom() {
        outputPane.setCaretPosition(doc.getLength());
    }

    private void defineStyles() {
        Style base = StyleContext.getDefaultStyleContext()
                .getStyle(StyleContext.DEFAULT_STYLE);

        addStyle(STYLE_NORMAL,  base, COL_NORMAL);
        addStyle(STYLE_ERROR,   base, COL_ERROR);
        addStyle(STYLE_SUCCESS, base, COL_SUCCESS);
        addStyle(STYLE_INFO,    base, COL_INFO);
    }

    private void addStyle(String name, Style parent, Color fg) {
        Style s = doc.addStyle(name, parent);
        StyleConstants.setFontFamily(s, UIConstants.CONSOLE_FONT.getFamily());
        StyleConstants.setFontSize(s,   UIConstants.CONSOLE_FONT.getSize());
        StyleConstants.setForeground(s, fg);
        StyleConstants.setBackground(s, UIConstants.CONSOLE_BG);
    }
}
