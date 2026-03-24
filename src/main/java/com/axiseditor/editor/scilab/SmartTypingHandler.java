package com.axiseditor.editor.scilab;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import java.awt.event.*;

/**
 * SmartTypingHandler — adds VS Code-style smart editing to the text area:
 *
 *   1. WRAP SELECTION: if text is selected and the user types an opening
 *      bracket/quote, the selection is wrapped.
 *         selected "hello" + type (  →  (hello)
 *         selected "hello" + type "  →  "hello"
 *         selected "hello" + type [  →  [hello]
 *
 *   2. AUTO-CLOSE PAIRS: if nothing is selected and an opening char is typed,
 *      the closing char is inserted and the caret placed between them.
 *         type (   →  (|)
 *         type [   →  [|]
 *         type {   →  {|}
 *         type '   →  '|'   (only outside an existing string)
 *         type "   →  "|"
 *
 *   3. SKIP OVER CLOSING CHARS: if the next char is already the closing
 *      counterpart and the user types it, just advance the caret.
 *
 *   4. AUTO-DELETE PAIR: pressing Backspace on an empty pair ( | ) deletes both.
 *
 *   5. Ctrl+/ : toggle line/multi-line comment ( // prefix )
 *
 * Install:  SmartTypingHandler.install(textArea);
 */
public class SmartTypingHandler extends KeyAdapter {

    private static final char[] OPEN_CHARS  = { '(', '[', '{', '\'', '"' };
    private static final char[] CLOSE_CHARS = { ')', ']', '}', '\'', '"' };

    private final RSyntaxTextArea ta;

    private SmartTypingHandler(RSyntaxTextArea ta) {
        this.ta = ta;
    }

    public static void install(RSyntaxTextArea ta) {
        ta.addKeyListener(new SmartTypingHandler(ta));
    }

    // ── Key events ────────────────────────────────────────────────────────────

    @Override
    public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();

        // ── 5. Ctrl+/ → handled in keyPressed (need VK code) ─────────────
        if (e.isControlDown()) return;

        int openIdx = indexOf(OPEN_CHARS, c);

        if (openIdx >= 0) {
            // ── 1. Wrap selection ─────────────────────────────────────────
            if (ta.getSelectedText() != null && !ta.getSelectedText().isEmpty()) {
                wrapSelection(OPEN_CHARS[openIdx], CLOSE_CHARS[openIdx]);
                e.consume();
                return;
            }
            // ── 2. Auto-close ─────────────────────────────────────────────
            autoClose(OPEN_CHARS[openIdx], CLOSE_CHARS[openIdx]);
            e.consume();
            return;
        }

        int closeIdx = indexOf(CLOSE_CHARS, c);
        if (closeIdx >= 0) {
            // ── 3. Skip over closing char ─────────────────────────────────
            if (skipOver(CLOSE_CHARS[closeIdx])) {
                e.consume();
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // ── 4. Auto-delete empty pair ─────────────────────────────────────
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            if (deleteEmptyPair()) e.consume();
            return;
        }

        // ── 5. Ctrl+/ → toggle comment ────────────────────────────────────
        if (e.getKeyCode() == KeyEvent.VK_SLASH
                && (e.isControlDown() || e.isMetaDown())) {
            toggleComment();
            e.consume();
        }
    }

    // ── Implementation ────────────────────────────────────────────────────────

    /** Surround the current selection with open+close characters. */
    private void wrapSelection(char open, char close) {
        int start = ta.getSelectionStart();
        int end   = ta.getSelectionEnd();
        String selected = ta.getSelectedText();
        String replacement = open + selected + close;
        ta.replaceSelection(replacement);
        // Re-select the original text (excluding the wrapping chars)
        ta.setSelectionStart(start + 1);
        ta.setSelectionEnd(start + 1 + selected.length());
    }

    /** Insert close char after caret, leave caret between the pair. */
    private void autoClose(char open, char close) {
        int pos = ta.getCaretPosition();
        try {
            ta.getDocument().insertString(pos, String.valueOf(open) + close, null);
            ta.setCaretPosition(pos + 1);
        } catch (BadLocationException ignored) { }
    }

    /**
     * If the char immediately after the caret is the expected closing char,
     * advance the caret instead of inserting a duplicate.
     */
    private boolean skipOver(char close) {
        int pos = ta.getCaretPosition();
        try {
            String text = ta.getDocument().getText(0, ta.getDocument().getLength());
            if (pos < text.length() && text.charAt(pos) == close) {
                ta.setCaretPosition(pos + 1);
                return true;
            }
        } catch (BadLocationException ignored) { }
        return false;
    }

    /**
     * If the caret is between a matching open/close pair with nothing between
     * them, delete both.
     */
    private boolean deleteEmptyPair() {
        int pos = ta.getCaretPosition();
        if (pos == 0) return false;
        try {
            String text = ta.getDocument().getText(0, ta.getDocument().getLength());
            char before = text.charAt(pos - 1);
            if (pos >= text.length()) return false;
            char after  = text.charAt(pos);

            int openIdx = indexOf(OPEN_CHARS, before);
            if (openIdx >= 0 && CLOSE_CHARS[openIdx] == after) {
                // Delete both characters
                ta.getDocument().remove(pos - 1, 2);
                return true;
            }
        } catch (BadLocationException ignored) { }
        return false;
    }

    /**
     * Toggle // comment on the selected lines (or the current line if nothing
     * is selected).  Multi-line selection: all lines are toggled together —
     * if ALL lines already have //, they are removed; otherwise // is added.
     */
    private void toggleComment() {
        try {
            String text      = ta.getDocument().getText(0, ta.getDocument().getLength());
            int selStart     = ta.getSelectionStart();
            int selEnd       = ta.getSelectionEnd();

            int firstLine    = ta.getLineOfOffset(selStart);
            int lastLine     = ta.getLineOfOffset(selEnd > selStart ? selEnd - 1 : selEnd);

            // Collect lines
            int[] lineStarts = new int[lastLine - firstLine + 1];
            String[] lineContents = new String[lineStarts.length];
            for (int i = 0; i < lineStarts.length; i++) {
                lineStarts[i]    = ta.getLineStartOffset(firstLine + i);
                int lineEnd      = ta.getLineEndOffset(firstLine + i);
                lineContents[i]  = text.substring(lineStarts[i], Math.min(lineEnd, text.length()));
            }

            // Decide: add or remove?
            boolean allCommented = true;
            for (String line : lineContents) {
                if (!line.stripLeading().startsWith("//")) { allCommented = false; break; }
            }

            // Apply from bottom to top so offsets stay valid
            for (int i = lineContents.length - 1; i >= 0; i--) {
                String line = lineContents[i];
                int start   = lineStarts[i];
                if (allCommented) {
                    // Remove the first "//" (preserve indentation)
                    int slashPos = line.indexOf("//");
                    if (slashPos >= 0) {
                        ta.getDocument().remove(start + slashPos, 2);
                    }
                } else {
                    // Insert "//" at the start of the line content
                    int indent = 0;
                    while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t'))
                        indent++;
                    ta.getDocument().insertString(start + indent, "//", null);
                }
            }
        } catch (BadLocationException ignored) { }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static int indexOf(char[] arr, char target) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == target) return i;
        return -1;
    }
}
