package com.axiseditor.editor.scilab;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * BracketColorizer — paints rainbow-depth colours on matching bracket pairs
 * and quote pairs directly inside the RSyntaxTextArea using a custom
 * Highlighter.
 *
 * Supported pairs:
 *   ( )   [ ]   { }   ' '  (single-quoted string)   " "  (double-quoted)
 *
 * Each nesting level cycles through ScilabTheme.BRACKET_COLORS[].
 * Opening and closing bracket of the same pair share the same colour.
 *
 * How it works:
 *   After every document change the full text is re-scanned; all bracket/
 *   quote positions are collected with their depth, then a custom
 *   LayeredHighlightPainter is installed for each position.
 */
public class BracketColorizer implements DocumentListener {

    // Characters we treat as paired delimiters
    private static final char[] OPENERS  = { '(', '[', '{' };
    private static final char[] CLOSERS  = { ')', ']', '}' };

    private final RSyntaxTextArea textArea;
    private final Highlighter     highlighter;
    private final List<Object>    tags = new ArrayList<>();  // highlight handles

    public BracketColorizer(RSyntaxTextArea textArea) {
        this.textArea  = textArea;
        this.highlighter = textArea.getHighlighter();
        // Re-scan whenever the document changes
        textArea.getDocument().addDocumentListener(this);
        repaint();
    }

    // ── DocumentListener ──────────────────────────────────────────────────────
    @Override public void insertUpdate(DocumentEvent e)  { repaint(); }
    @Override public void removeUpdate(DocumentEvent e)  { repaint(); }
    @Override public void changedUpdate(DocumentEvent e) { /* style changes – ignore */ }

    // ── Core logic ────────────────────────────────────────────────────────────

    /** Remove old highlights and repaint all bracket pairs. */
    private void repaint() {
        // Must run on EDT; schedule if called from a background thread
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(this::repaint);
            return;
        }
        clearHighlights();
        try {
            String text = textArea.getDocument()
                                  .getText(0, textArea.getDocument().getLength());
            paintBrackets(text);
        } catch (BadLocationException ignored) { }
    }

    private void clearHighlights() {
        for (Object tag : tags) {
            try { highlighter.removeHighlight(tag); } catch (Exception ignored) { }
        }
        tags.clear();
    }

    /**
     * Scan the full text and highlight every bracket character at the colour
     * matching its nesting depth.  We skip bracket characters that sit inside
     * string literals so we don't colour phantom brackets.
     */
    private void paintBrackets(String text) {
        // Track nesting depth per bracket type independently so  ([ depth=1
        // and [(  depth=1  look different; actually we track a global depth.
        int     depth  = 0;
        boolean inSingleQ = false;
        boolean inDoubleQ = false;
        boolean inComment = false;

        for (int i = 0; i < text.length(); i++) {
            char c  = text.charAt(i);
            char nc = (i + 1 < text.length()) ? text.charAt(i + 1) : 0;

            // ── Comment handling ────────────────────────────────────────
            if (!inSingleQ && !inDoubleQ) {
                if (!inComment && c == '/' && nc == '/') {
                    // skip to end of line
                    while (i < text.length() && text.charAt(i) != '\n') i++;
                    continue;
                }
                if (!inComment && c == '/' && nc == '*') { inComment = true; i++; continue; }
                if (inComment && c == '*' && nc == '/')  { inComment = false; i++; continue; }
                if (inComment) continue;
            }

            // ── String handling ─────────────────────────────────────────
            if (!inDoubleQ && !inComment && c == '\'') {
                inSingleQ = !inSingleQ;
                // Colour the quote character at the current string depth
                int qDepth = depth;  // quotes inherit bracket depth
                highlightChar(i, qDepth);
                continue;
            }
            if (!inSingleQ && !inComment && c == '"') {
                inDoubleQ = !inDoubleQ;
                int qDepth = depth;
                highlightChar(i, qDepth);
                continue;
            }
            if (inSingleQ || inDoubleQ) continue;  // inside string – skip

            // ── Bracket handling ─────────────────────────────────────────
            if (isOpener(c)) {
                highlightChar(i, depth);
                depth++;
            } else if (isCloser(c)) {
                if (depth > 0) depth--;
                highlightChar(i, depth);
            }
        }
    }

    private void highlightChar(int pos, int depth) {
        Color color = ScilabTheme.BRACKET_COLORS[depth % ScilabTheme.BRACKET_COLORS.length];
        Highlighter.HighlightPainter painter = new BracketPainter(color);
        try {
            Object tag = highlighter.addHighlight(pos, pos + 1, painter);
            tags.add(tag);
        } catch (BadLocationException ignored) { }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isOpener(char c) {
        for (char o : OPENERS) if (c == o) return true;
        return false;
    }

    private static boolean isCloser(char c) {
        for (char cl : CLOSERS) if (c == cl) return true;
        return false;
    }

    // ── Painter ───────────────────────────────────────────────────────────────

    /**
     * A lightweight painter that draws a coloured underline + slightly tinted
     * background under a single bracket character.
     */
    private static class BracketPainter implements Highlighter.HighlightPainter {
        private final Color color;
        BracketPainter(Color color) { this.color = color; }

        @Override
        public void paint(Graphics g, int offs0, int offs1,
                          Shape bounds, JTextComponent c) {
            try {
                Rectangle r = c.modelToView2D(offs0).getBounds();
                if (r == null) return;

                // Tinted background
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g.fillRect(r.x, r.y, r.width == 0 ? 8 : r.width, r.height);

                // Bold underline
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f));
                int y = r.y + r.height - 2;
                g2.drawLine(r.x, y, r.x + (r.width == 0 ? 8 : r.width), y);
            } catch (BadLocationException ignored) { }
        }
    }
}
