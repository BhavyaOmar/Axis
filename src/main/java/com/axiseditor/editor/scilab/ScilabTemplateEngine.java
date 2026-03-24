package com.axiseditor.editor.scilab;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ScilabTemplateEngine — detects when the user types a block-opening keyword
 * and inserts the complete boilerplate with smart indentation.
 *
 * Trigger: KeyEvent.VK_ENTER pressed after one of the trigger words.
 *
 * Templates:
 *   for      →  for i = 1:n\n    \nend
 *   while    →  while condition\n    \nend
 *   if       →  if condition then\n    \nend
 *   function →  function [out] = name(args)\n    \nendfunction
 *   select   →  select expr\n  case val\n    \notherwise\n    \nend
 *   try      →  try\n    \ncatch\n    \nend
 *
 * After insertion the caret is placed at the first customisable slot (│).
 *
 * Usage:
 *   Call ScilabTemplateEngine.handleEnter(textArea) from the ENTER key binding.
 *   Returns true if a template was inserted (caller should consume the event).
 */
public class ScilabTemplateEngine {

    // ── Template registry ─────────────────────────────────────────────────────
    // Each value is the text to insert AFTER the keyword line.
    // The marker "|CARET|" indicates where to place the caret.
    // The marker "|KEYWORD|" is replaced by the actual typed keyword.

    private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATES.put("for",
            " i = 1:n\n" +
            "    |CARET|\n" +
            "end");

        TEMPLATES.put("while",
            " condition\n" +
            "    |CARET|\n" +
            "end");

        TEMPLATES.put("if",
            " condition then\n" +
            "    |CARET|\n" +
            "end");

        TEMPLATES.put("elseif",
            " condition then\n" +
            "    |CARET|");

        TEMPLATES.put("else",
            "\n    |CARET|\n" +
            "end");

        TEMPLATES.put("function",
            " [out] = functionName(args)\n" +
            "    |CARET|\n" +
            "endfunction");

        TEMPLATES.put("select",
            " expression\n" +
            "  case value1\n" +
            "    |CARET|\n" +
            "  otherwise\n" +
            "    \n" +
            "end");

        TEMPLATES.put("try",
            "\n" +
            "    |CARET|\n" +
            "catch\n" +
            "    \n" +
            "end");
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Call this when ENTER is pressed.
     *
     * @return true if a template was inserted and the caller should consume the event
     */
    public static boolean handleEnter(RSyntaxTextArea ta) {
        try {
            int caretPos = ta.getCaretPosition();
            int lineIdx  = ta.getLineOfOffset(caretPos);
            int lineStart = ta.getLineStartOffset(lineIdx);
            String lineText = ta.getDocument()
                    .getText(lineStart, caretPos - lineStart).trim();

            // Look for a matching template keyword
            for (Map.Entry<String, String> entry : TEMPLATES.entrySet()) {
                String keyword = entry.getKey();
                if (lineText.equals(keyword) || lineText.startsWith(keyword + " ")
                        || lineText.startsWith(keyword + "(")) {
                    insertTemplate(ta, caretPos, lineStart, entry.getValue());
                    return true;
                }
            }
        } catch (BadLocationException ignored) { }
        return false;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static void insertTemplate(RSyntaxTextArea ta,
                                       int caretPos,
                                       int lineStart,
                                       String template) throws BadLocationException {
        // Calculate current indentation of the trigger line
        String fullLine = ta.getDocument().getText(lineStart, caretPos - lineStart);
        String indent   = leadingWhitespace(fullLine);

        // Replace generic indentation in template with the actual indent
        String indented = template
                .replace("\n    ", "\n" + indent + "    ")
                .replace("\n  ", "\n" + indent + "  ")
                .replace("\n", "\n" + indent);

        // Find where the caret should land
        int caretMarkerIdx = indented.indexOf("|CARET|");
        String cleaned = indented.replace("|CARET|", "");

        // Insert the template after the current cursor position
        ta.getDocument().insertString(caretPos, cleaned, null);

        // Position the caret at the |CARET| marker
        if (caretMarkerIdx >= 0) {
            ta.setCaretPosition(caretPos + caretMarkerIdx);
        }
    }

    /** Extract leading spaces/tabs from a string. */
    private static String leadingWhitespace(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        return s.substring(0, i);
    }
}
