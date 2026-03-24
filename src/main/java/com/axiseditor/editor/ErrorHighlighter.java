package com.axiseditor.editor;

import com.axiseditor.execution.ErrorParser;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ErrorHighlighter — Phase 4 visual error feedback system.
 *
 * What it does for each parsed error:
 *   1. Paints a red background highlight on the error line in the editor
 *   2. Adds a red ● icon in the gutter (line-number margin) on that line
 *   3. Draws a red wavy underline under the entire error line text
 *
 * Multiple errors are all highlighted simultaneously.
 * Call clearAll() before a new run to reset.
 *
 * Usage:
 *   ErrorHighlighter eh = new ErrorHighlighter(textArea, scrollPane);
 *   eh.highlight(errors);   // after execution
 *   eh.clearAll();          // before next run
 */
public class ErrorHighlighter {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color ERROR_LINE_BG    = new Color(120, 20,  20,  80);  // translucent red
    private static final Color ERROR_UNDERLINE  = new Color(255, 80,  80);
    private static final Color ERROR_GUTTER_COL = new Color(220, 60,  60);
    private static final Color WARNING_LINE_BG  = new Color(120, 100, 0,   60);  // translucent amber

    private final RSyntaxTextArea  textArea;
    private final Gutter           gutter;

    // Track all active highlights so we can remove them later
    private final List<Object>          lineHighlightTags = new ArrayList<>();
    private final List<GutterIconInfo>  gutterIcons       = new ArrayList<>();

    public ErrorHighlighter(RSyntaxTextArea textArea, RTextScrollPane scrollPane) {
        this.textArea = textArea;
        this.gutter   = scrollPane.getGutter();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Highlight all errors in the list.
     * Call clearAll() first if re-running.
     */
    public void highlight(List<ErrorParser.ParsedError> errors) {
        clearAll();
        for (ErrorParser.ParsedError err : errors) {
            if (err.lineNumber > 0) {
                highlightLine(err.lineNumber, ERROR_LINE_BG, ERROR_UNDERLINE);
                addGutterIcon(err.lineNumber, err.message);
            }
        }
        // Scroll to the first error
        if (!errors.isEmpty() && errors.get(0).lineNumber > 0) {
            scrollToLine(errors.get(0).lineNumber);
        }
    }

    /**
     * Highlight a single line (1-based).
     * Convenience method used by ExecutionManager for quick single-error highlight.
     */
    public void highlightSingleError(int lineNumber, String message) {
        clearAll();
        highlightLine(lineNumber, ERROR_LINE_BG, ERROR_UNDERLINE);
        addGutterIcon(lineNumber, message);
        scrollToLine(lineNumber);
    }

    /** Remove all error highlights and gutter icons. */
    public void clearAll() {
        // Remove line highlights
        Highlighter h = textArea.getHighlighter();
        for (Object tag : lineHighlightTags) {
            try { h.removeHighlight(tag); } catch (Exception ignored) {}
        }
        lineHighlightTags.clear();

        // Remove gutter icons
        for (GutterIconInfo icon : gutterIcons) {
            try { gutter.removeTrackingIcon(icon); } catch (Exception ignored) {}
        }
        gutterIcons.clear();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void highlightLine(int lineNumber, Color bgColor, Color underlineColor) {
        try {
            int lineIdx   = lineNumber - 1;  // 0-based
            int lineStart = textArea.getLineStartOffset(lineIdx);
            int lineEnd   = textArea.getLineEndOffset(lineIdx);

            // 1. Background highlight
            Object bgTag = textArea.getHighlighter().addHighlight(
                    lineStart, lineEnd,
                    new LinePainter(bgColor, underlineColor)
            );
            lineHighlightTags.add(bgTag);

        } catch (BadLocationException ignored) {}
    }

    private void addGutterIcon(int lineNumber, String tooltip) {
        try {
            Icon icon = createErrorIcon();
            GutterIconInfo info = gutter.addLineTrackingIcon(
                    lineNumber - 1,   // 0-based
                    icon,
                    tooltip
            );
            gutterIcons.add(info);
        } catch (Exception ignored) {}
    }

    private void scrollToLine(int lineNumber) {
        SwingUtilities.invokeLater(() -> {
            try {
                int offset = textArea.getLineStartOffset(lineNumber - 1);
                textArea.setCaretPosition(offset);
                textArea.requestFocusInWindow();
            } catch (BadLocationException ignored) {}
        });
    }

    /** Build a small red circle icon for the gutter. */
    private static Icon createErrorIcon() {
        return new Icon() {
            @Override public int getIconWidth()  { return 12; }
            @Override public int getIconHeight() { return 12; }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                // Red filled circle
                g2.setColor(ERROR_GUTTER_COL);
                g2.fillOval(x + 1, y + 1, 10, 10);
                // White border
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawOval(x + 1, y + 1, 10, 10);
                // White "!" mark
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                g2.drawString("!", x + 4, y + 10);
                g2.dispose();
            }
        };
    }

    // ── Custom highlighter painter ────────────────────────────────────────────

    /**
     * LinePainter — draws:
     *   1. A translucent background over the full line width
     *   2. A wavy red underline at the bottom of the line
     */
    private static class LinePainter extends DefaultHighlighter.DefaultHighlightPainter {
        private final Color bgColor;
        private final Color underlineColor;

        LinePainter(Color bgColor, Color underlineColor) {
            super(bgColor);
            this.bgColor       = bgColor;
            this.underlineColor = underlineColor;
        }

        @Override
        public Shape paintLayer(Graphics g, int offs0, int offs1,
                                Shape bounds, JTextComponent c, View view) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            Rectangle alloc = bounds instanceof Rectangle
                    ? (Rectangle) bounds
                    : bounds.getBounds();

            // 1. Full-width background
            g2.setColor(bgColor);
            g2.fillRect(0, alloc.y, c.getWidth(), alloc.height);

            // 2. Wavy underline at the bottom of the line
            g2.setColor(underlineColor);
            g2.setStroke(new BasicStroke(1.5f));
            int y     = alloc.y + alloc.height - 2;
            int waveH = 2;
            int step  = 4;
            for (int x = 0; x < c.getWidth(); x += step * 2) {
                g2.drawArc(x,         y - waveH, step, waveH * 2, 0,   180);
                g2.drawArc(x + step,  y - waveH, step, waveH * 2, 180, 180);
            }

            g2.dispose();
            return alloc;
        }
    }
}
