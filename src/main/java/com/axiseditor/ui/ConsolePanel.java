package com.axiseditor.ui;

import com.axiseditor.execution.ScilabRunner;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

/**
 * ConsolePanel — output area + interactive stdin input bar.
 *
 * Phase 3 (original):
 *   • Coloured output: white=normal, red=error, green=success, cyan=info
 *
 * NEW (stdin support):
 *   • Input bar at the bottom — shown only while a script is running
 *   • When Scilab calls input(), the prompt text appears in the console
 *     and the input bar is enabled so the user can type a response
 *   • Pressing Enter (or clicking Send) calls ScilabRunner.sendInput()
 *   • The typed text is echoed into the console in yellow so it's visible
 *   • setRunner(runner) wires this panel to the active ScilabRunner
 *   • setWaitingForInput(true/false) enables/disables the input bar
 */
public class ConsolePanel extends JPanel {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COL_NORMAL  = new Color(212, 212, 212);
    private static final Color COL_ERROR   = new Color(244, 135, 113);
    private static final Color COL_SUCCESS = new Color(106, 200, 100);
    private static final Color COL_INFO    = new Color( 86, 182, 194);
    private static final Color COL_INPUT   = new Color(255, 220,  80);  // NEW: user typed text
    private static final Color COL_PROMPT  = new Color(180, 180, 255);  // NEW: input() prompt

    private final JTextPane    outputPane;
    private final StyledDocument doc;

    // NEW: input bar components
    private final JTextField   inputField;
    private final JButton      sendButton;
    private final JPanel       inputBar;
    private       ScilabRunner runner;   // reference set by ExecutionManager

    // ── Style names ───────────────────────────────────────────────────────────
    private static final String STYLE_NORMAL  = "normal";
    private static final String STYLE_ERROR   = "error";
    private static final String STYLE_SUCCESS = "success";
    private static final String STYLE_INFO    = "info";
    private static final String STYLE_INPUT   = "input";   // NEW
    private static final String STYLE_PROMPT  = "prompt";  // NEW

    public ConsolePanel() {
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(220, 0));

        // ── Output pane ───────────────────────────────────────────────────
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

        // ── NEW: Input bar (shown at bottom while script runs) ────────────
        inputField = new JTextField();
        inputField.setFont(UIConstants.CONSOLE_FONT);
        inputField.setBackground(new Color(40, 40, 50));
        inputField.setForeground(COL_INPUT);
        inputField.setCaretColor(COL_INPUT);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 160), 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        inputField.setEnabled(false);
        inputField.setToolTipText("Type your input here and press Enter");

        sendButton = new JButton("Send");
        sendButton.setFont(UIConstants.BUTTON_FONT);
        sendButton.setFocusPainted(false);
        sendButton.setEnabled(false);
        sendButton.setBackground(new Color(60, 80, 140));
        sendButton.setForeground(Color.WHITE);

        // Clear button (existing)
        JButton btnClear = new JButton("Clear");
        btnClear.setFont(UIConstants.BUTTON_FONT);
        btnClear.setFocusPainted(false);
        btnClear.addActionListener(e -> clear());

        inputBar = new JPanel(new BorderLayout(4, 0));
        inputBar.setBackground(new Color(30, 30, 40));
        inputBar.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

        JLabel inputLabel = new JLabel("Input:");
        inputLabel.setForeground(COL_PROMPT);
        inputLabel.setFont(UIConstants.LABEL_FONT);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(sendButton);
        rightButtons.add(btnClear);

        inputBar.add(inputLabel,  BorderLayout.WEST);
        inputBar.add(inputField,  BorderLayout.CENTER);
        inputBar.add(rightButtons, BorderLayout.EAST);

        add(inputBar, BorderLayout.SOUTH);

        // ── Wire Enter key and Send button ────────────────────────────────
        ActionListener submitAction = e -> submitInput();
        inputField.addActionListener(submitAction);
        sendButton.addActionListener(submitAction);

        appendInfo("Axis IDE — Console ready.\n");
    }

    // ── NEW: Wire to the active ScilabRunner ──────────────────────────────────

    /**
     * Call this from ExecutionManager whenever a new run starts.
     * Also wire onPrompt so Scilab's input() prompts enable the input bar.
     */
    /**
     * Called once from ExecutionManager constructor.
     * Gives ConsolePanel a reference to the runner so submitInput()
     * can call runner.sendInput(). Does NOT register onPrompt here —
     * ExecutionManager.run() owns that callback to avoid overwrite conflicts.
     */
    public void setRunner(ScilabRunner r) {
        this.runner = r;
    }

    /**
     * Enable or disable the input bar.
     * Call with true when Scilab is waiting; false when it's computing or done.
     */
    public void setWaitingForInput(boolean waiting) {
        SwingUtilities.invokeLater(() -> {
            inputField.setEnabled(waiting);
            sendButton.setEnabled(waiting);
            if (waiting) {
                inputField.requestFocusInWindow();
            } else {
                inputField.setText("");
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void appendMessage(String text) { append(text, STYLE_NORMAL);  }
    public void appendError(String text)   { append(text, STYLE_ERROR);   }
    public void appendSuccess(String text) { append(text, STYLE_SUCCESS); }
    public void appendInfo(String text)    { append(text, STYLE_INFO);    }
    public void appendPrompt(String text)  { append(text, STYLE_PROMPT);  }  // NEW

    public void clear() {
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) { }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Called when the user presses Enter or clicks Send. */
    private void submitInput() {
        String text = inputField.getText();
        if (text == null || text.trim().isEmpty()) return;
        inputField.setText("");

        // Echo the typed text in yellow so the user can see what was entered
        appendInput(text + "\n");

        // Send to the Scilab process stdin
        if (runner != null) {
            runner.sendInput(text);
        }

        // Keep input bar enabled — the script may need more inputs.
        // It will be disabled automatically by ExecutionManager when the
        // script finishes (handleDone / stop).
        inputField.requestFocusInWindow();
    }

    private void appendInput(String text) { append(text, STYLE_INPUT); }

    private void append(String text, String styleName) {
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
        addStyle(STYLE_INPUT,   base, COL_INPUT);   // NEW
        addStyle(STYLE_PROMPT,  base, COL_PROMPT);  // NEW
    }

    private void addStyle(String name, Style parent, Color fg) {
        Style s = doc.addStyle(name, parent);
        StyleConstants.setFontFamily(s, UIConstants.CONSOLE_FONT.getFamily());
        StyleConstants.setFontSize(s,   UIConstants.CONSOLE_FONT.getSize());
        StyleConstants.setForeground(s, fg);
        StyleConstants.setBackground(s, UIConstants.CONSOLE_BG);
    }
}