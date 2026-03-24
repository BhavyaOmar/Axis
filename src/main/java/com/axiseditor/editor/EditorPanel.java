package com.axiseditor.editor;

import com.axiseditor.editor.scilab.*;
import com.axiseditor.execution.ErrorParser;
import com.axiseditor.utils.UIConstants;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * EditorPanel — Phase 2 + Phase 4 upgrade.
 *
 * Phase 2 features (unchanged):
 *   • Scilab syntax highlighting, VS Code dark theme
 *   • Rainbow bracket / quote colorising
 *   • Smart template insertion on Enter
 *   • Smart typing: wrap-selection, auto-close pairs, Ctrl+/
 *   • Keyword + variable auto-completion
 *   • Find / Replace (Ctrl+F / Ctrl+H)
 *   • Undo / Redo (Ctrl+Z / Ctrl+Y)
 *
 * Phase 4 additions:
 *   • ErrorHighlighter — red background + wavy underline on error lines
 *   • Red ● gutter icon on error lines with tooltip showing error message
 *   • highlightErrors(List) — highlight ALL errors at once
 *   • clearErrorHighlights() — reset before next run
 *   • highlightErrorLine(int) kept for single-error quick access
 */
public class EditorPanel extends JPanel {

    private final RSyntaxTextArea  textArea;
    private final RTextScrollPane  scrollPane;
    private       FindReplaceDialog findReplaceDialog;
    private       ErrorHighlighter  errorHighlighter;   // Phase 4

    /** Fired whenever the document changes (used by FileManager). */
    private Runnable onModifiedCallback;

    public EditorPanel() {
        setLayout(new BorderLayout());

        // ── Register custom Scilab syntax factory ────────────────────────
        AbstractTokenMakerFactory.setDefaultInstance(new ScilabTokenMakerFactory());

        textArea   = buildTextArea();
        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);

        // Style gutter to match dark theme
        Gutter gutter = scrollPane.getGutter();
        gutter.setBackground(ScilabTheme.LINE_NUMBER_BG);
        gutter.setLineNumberColor(ScilabTheme.LINE_NUMBER_FG);

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "Editor",
                TitledBorder.LEFT, TitledBorder.TOP,
                UIConstants.LABEL_FONT
        );
        border.setTitleColor(Color.LIGHT_GRAY);
        setBorder(border);
        add(scrollPane, BorderLayout.CENTER);

        // ── Phase 2 features ─────────────────────────────────────────────
        new BracketColorizer(textArea);
        SmartTypingHandler.install(textArea);
        ScilabAutoComplete.install(textArea);
        installTemplateEnterBinding();
        installKeyboardShortcuts();

        // ── Phase 4: ErrorHighlighter (needs scrollPane reference) ────────
        errorHighlighter = new ErrorHighlighter(textArea, scrollPane);

        // ── Document change listener ──────────────────────────────────────
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onDocumentChange(); }
            @Override public void removeUpdate(DocumentEvent e)  { onDocumentChange(); }
            @Override public void changedUpdate(DocumentEvent e) { onDocumentChange(); }
        });
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private RSyntaxTextArea buildTextArea() {
        RSyntaxTextArea ta = new RSyntaxTextArea();
        ta.setSyntaxEditingStyle(ScilabTokenMakerFactory.SYNTAX_STYLE_SCILAB);
        ScilabTheme.apply(ta);
        ta.setFont(UIConstants.EDITOR_FONT);
        ta.setTabSize(4);
        ta.setAutoIndentEnabled(true);
        ta.setAntiAliasingEnabled(true);
        ta.setCodeFoldingEnabled(false);
        ta.setHighlightCurrentLine(true);
        ta.setBracketMatchingEnabled(true);
        ta.setAnimateBracketMatching(false);
        ta.setMatchedBracketBGColor(ScilabTheme.BRACKET_MATCH_BG);
        ta.setMatchedBracketBorderColor(ScilabTheme.BRACKET_MATCH_BORDER);
        ta.setMargin(new Insets(4, 6, 4, 6));
        return ta;
    }

    // ── Keyboard bindings ─────────────────────────────────────────────────────

    private void installTemplateEnterBinding() {
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        textArea.getInputMap().put(enter, "axis-enter");
        textArea.getActionMap().put("axis-enter", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!ScilabTemplateEngine.handleEnter(textArea)) {
                    textArea.getActionMap().get("insert-break").actionPerformed(e);
                }
            }
        });
    }

    private void installKeyboardShortcuts() {
        // Ctrl+F → Find
        textArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "axis-find");
        textArea.getActionMap().put("axis-find", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openFindReplace(); }
        });

        // Ctrl+H → Replace
        textArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), "axis-replace");
        textArea.getActionMap().put("axis-replace", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openFindReplace(); }
        });
    }

    private void openFindReplace() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        if (findReplaceDialog == null) {
            findReplaceDialog = new FindReplaceDialog(owner, textArea);
        }
        findReplaceDialog.prefillWithSelection();
        findReplaceDialog.setVisible(true);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Replace entire editor content (file open). */
    public void setText(String content) {
        textArea.setText(content);
        textArea.setCaretPosition(0);
        textArea.discardAllEdits();
        clearErrorHighlights();          // clear old error marks on new file
    }

    /** Get current editor content. */
    public String getText() { return textArea.getText(); }

    /** True if the editor is empty. */
    public boolean isEmpty() { return textArea.getText().trim().isEmpty(); }

    /** Register modified callback (used by FileManager). */
    public void setOnModifiedCallback(Runnable callback) {
        this.onModifiedCallback = callback;
    }

    /** Expose underlying RSyntaxTextArea for other components. */
    public RSyntaxTextArea getTextArea() { return textArea; }

    // ── Phase 4 — Error Highlighting API ─────────────────────────────────────

    /**
     * Highlight ALL errors from a parsed list.
     * Red background + wavy underline + gutter icon on every error line.
     * The first error line is scrolled into view.
     */
    public void highlightErrors(List<ErrorParser.ParsedError> errors) {
        SwingUtilities.invokeLater(() -> errorHighlighter.highlight(errors));
    }

    /**
     * Highlight a single error line quickly (1-based).
     * Used by ExecutionManager for the immediate single-error case.
     */
    public void highlightErrorLine(int lineNumber) {
        SwingUtilities.invokeLater(() -> {
            errorHighlighter.highlightSingleError(lineNumber, "Error at line " + lineNumber);
        });
    }

    /**
     * Highlight a single error line with a specific message.
     */
    public void highlightErrorLine(int lineNumber, String message) {
        SwingUtilities.invokeLater(() -> {
            errorHighlighter.highlightSingleError(lineNumber, message);
        });
    }

    /** Remove all error highlights. Call before each new run. */
    public void clearErrorHighlights() {
        SwingUtilities.invokeLater(() -> errorHighlighter.clearAll());
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void onDocumentChange() {
        notifyModified();
        // Clear error highlights as soon as the user starts editing
        // (the highlighted line is no longer accurate after a keystroke)
        errorHighlighter.clearAll();
    }

    private void notifyModified() {
        if (onModifiedCallback != null) {
            onModifiedCallback.run();
        }
    }
}
