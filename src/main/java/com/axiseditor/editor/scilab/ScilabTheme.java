package com.axiseditor.editor.scilab;

import org.fife.ui.rsyntaxtextarea.*;

import java.awt.Color;

/**
 * ScilabTheme — applies a VS Code Dark+-inspired color scheme to an
 * RSyntaxTextArea configured for Scilab.
 *
 * Bracket depth colours (rainbow brackets):
 *   depth 0 → yellow   #FFD700
 *   depth 1 → cyan     #4EC9B0
 *   depth 2 → violet   #C586C0
 *   depth 3 → orange   #CE9178
 *   (cycles back to yellow for deeper nesting)
 *
 * This class also sets matching-bracket highlight colours.
 */
public class ScilabTheme {

    // ── Background / general ────────────────────────────────────────────────
    public static final Color BG              = new Color(30,  30,  30);
    public static final Color BG_CURRENT_LINE = new Color(40,  40,  40);
    public static final Color FG              = new Color(212, 212, 212);
    public static final Color SELECTION_BG    = new Color(38,  79,  120);
    public static final Color LINE_NUMBER_FG  = new Color(133, 133, 133);
    public static final Color LINE_NUMBER_BG  = new Color(30,  30,  30);

    // ── Token colours ────────────────────────────────────────────────────────
    public static final Color KEYWORD         = new Color(86,  156, 214); // blue   – control flow
    public static final Color BUILTIN         = new Color(220, 220, 170); // yellow – built-in functions
    public static final Color STRING          = new Color(206, 145, 120); // orange – string literals
    public static final Color NUMBER          = new Color(181, 206, 168); // green  – numeric literals
    public static final Color COMMENT         = new Color(106, 153,  85); // green (muted) – comments
    public static final Color OPERATOR        = new Color(212, 212, 212); // white  – operators
    public static final Color IDENTIFIER      = new Color(156, 220, 254); // light-blue – variables

    // ── Bracket depth colours (used by BracketColorizer) ────────────────────
    public static final Color[] BRACKET_COLORS = {
        new Color(255, 215,   0),  // depth 0 – gold
        new Color( 78, 201, 176),  // depth 1 – teal
        new Color(197, 134, 192),  // depth 2 – violet
        new Color(206, 145, 120),  // depth 3 – salmon
    };

    public static final Color BRACKET_MATCH_BG     = new Color(50, 50, 90);
    public static final Color BRACKET_MATCH_BORDER  = new Color(100, 100, 180);

    // ── Apply all settings to an RSyntaxTextArea ─────────────────────────────

    public static void apply(RSyntaxTextArea ta) {
        // ── Editor background & foreground
        ta.setBackground(BG);
        ta.setForeground(FG);
        ta.setCaretColor(FG);
        ta.setCurrentLineHighlightColor(BG_CURRENT_LINE);
        ta.setSelectionColor(SELECTION_BG);
        ta.setHighlightCurrentLine(true);

        // ── Bracket matching highlight
        ta.setBracketMatchingEnabled(true);
        ta.setAnimateBracketMatching(false);   // instant highlight, no animation lag
        ta.setMatchedBracketBGColor(BRACKET_MATCH_BG);
        ta.setMatchedBracketBorderColor(BRACKET_MATCH_BORDER);

        // ── Token colour scheme
        SyntaxScheme scheme = ta.getSyntaxScheme();

        scheme.getStyle(Token.RESERVED_WORD).foreground               = KEYWORD;
        scheme.getStyle(Token.RESERVED_WORD_2).foreground             = KEYWORD;
        scheme.getStyle(Token.FUNCTION).foreground                    = BUILTIN;
        scheme.getStyle(Token.LITERAL_CHAR).foreground                = STRING;
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = STRING;
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground  = NUMBER;
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground        = NUMBER;
        scheme.getStyle(Token.COMMENT_EOL).foreground                 = COMMENT;
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground           = COMMENT;
        scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground       = COMMENT;
        scheme.getStyle(Token.OPERATOR).foreground                    = OPERATOR;
        scheme.getStyle(Token.SEPARATOR).foreground                   = OPERATOR;
        scheme.getStyle(Token.IDENTIFIER).foreground                  = IDENTIFIER;
        scheme.getStyle(Token.WHITESPACE).foreground                  = FG;

        ta.setSyntaxScheme(scheme);
        ta.revalidate();
        ta.repaint();
    }
}
