package com.axiseditor.editor.scilab;

import org.fife.ui.rsyntaxtextarea.*;
import javax.swing.text.Segment;

/**
 * ScilabTokenMaker — teaches RSyntaxTextArea to tokenise Scilab source code.
 *
 * Token categories used:
 *   RESERVED_WORD    → for, while, if, else, elseif, end, function, return, ...
 *   RESERVED_WORD_2  → end-keywords that close blocks: end, endfunction, ...
 *   FUNCTION         → built-in Scilab functions: disp, printf, size, zeros, ...
 *   LITERAL_NUMBER_*  → integers, floats, complex
 *   LITERAL_STRING   → 'single' and "double" quoted strings
 *   COMMENT_EOL      → // line comments
 *   COMMENT_MULTILINE → /* block comments (Scilab 6+)
 *   OPERATOR         → + - * / ^ = == ~= < > & | : , ; ( ) [ ] { }
 *   SEPARATOR        → , ;
 *   IDENTIFIER       → variable names, user functions
 *   WHITESPACE       → spaces and tabs
 */
public class ScilabTokenMaker extends AbstractTokenMaker {

    // ── Keyword sets ─────────────────────────────────────────────────────────

    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        // Control flow
        "for", "while", "if", "else", "elseif", "then", "do",
        "end", "endfunction", "break", "continue", "return", "select",
        "case", "otherwise", "try", "catch",
        // Declaration
        "function", "global", "clear", "clc",
        // Boolean literals
        "true", "false", "%t", "%f",
        // Special constants
        "%pi", "%e", "%i", "%inf", "%nan", "%eps"
    ));

    private static final java.util.Set<String> BUILTINS = new java.util.HashSet<>(java.util.Arrays.asList(
        // I/O
        "disp", "printf", "fprintf", "sprintf", "input", "print",
        // Math
        "abs", "sqrt", "exp", "log", "log2", "log10", "sin", "cos", "tan",
        "asin", "acos", "atan", "sinh", "cosh", "tanh", "floor", "ceil",
        "round", "mod", "rem", "sign", "max", "min", "sum", "prod", "mean",
        "cumsum", "cumprod", "diff",
        // Matrix
        "zeros", "ones", "eye", "rand", "randn", "size", "length", "numel",
        "reshape", "transpose", "inv", "det", "trace", "norm", "rank",
        "linspace", "colon", "cross", "dot", "kron",
        // String
        "string", "num2str", "str2num", "str2double", "strcat", "strsplit",
        "strtrim", "upper", "lower", "strrep", "strfind", "regexp",
        "length", "part",
        // Type / util
        "type", "typeof", "class", "isa", "isnumeric", "ischar", "islogical",
        "isvector", "ismatrix", "isempty", "isnan", "isinf",
        "error", "warning", "assert",
        // File
        "open", "close", "read", "write", "mopen", "mclose", "mgetl", "mputl",
        // Plot
        "plot", "plot2d", "plot3d", "xlabel", "ylabel", "title", "legend",
        "figure", "clf", "hold", "grid", "subplot", "scatter",
        // Misc
        "pause", "sleep", "timer", "tic", "toc", "exec", "exit", "quit"
    ));

    // ── RSyntaxTextArea API ──────────────────────────────────────────────────

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap map = new TokenMap(true); // case-sensitive
        for (String kw : KEYWORDS) {
            map.put(kw, Token.RESERVED_WORD);
        }
        for (String fn : BUILTINS) {
            map.put(fn, Token.FUNCTION);
        }
        return map;
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();

        char[]  array  = text.array;
        int     offset = text.offset;
        int     count  = text.count;
        int     end    = offset + count;
        int     newStartOffset = startOffset - offset;

        int     currentTokenStart  = offset;
        int     currentTokenType   = initialTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {

                // ── No active token ──────────────────────────────────────
                case Token.NULL: {
                    currentTokenStart = i;

                    if (c == '/' && i + 1 < end && array[i + 1] == '/') {
                        currentTokenType = Token.COMMENT_EOL;
                    } else if (c == '/' && i + 1 < end && array[i + 1] == '*') {
                        currentTokenType = Token.COMMENT_MULTILINE;
                    } else if (c == '/' && i + 1 < end && array[i + 1] == '/') {
                        currentTokenType = Token.COMMENT_EOL;
                    } else if (c == '\'') {
                        currentTokenType = Token.LITERAL_CHAR;               // single-quoted string
                    } else if (c == '"') {
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE; // double-quoted string
                    } else if (RSyntaxUtilities.isDigit(c) ||
                               (c == '.' && i + 1 < end && RSyntaxUtilities.isDigit(array[i + 1]))) {
                        currentTokenType = Token.LITERAL_NUMBER_FLOAT;
                    } else if (RSyntaxUtilities.isLetter(c) || c == '_' || c == '%') {
                        currentTokenType = Token.IDENTIFIER;
                    } else if (c == ' ' || c == '\t') {
                        currentTokenType = Token.WHITESPACE;
                    } else {
                        // Operators and separators
                        currentTokenType = Token.OPERATOR;
                        addToken(text, currentTokenStart, i, Token.OPERATOR, newStartOffset + currentTokenStart);
                        currentTokenStart = i + 1;
                        currentTokenType  = Token.NULL;
                    }
                    break;
                }

                // ── EOL comment ──────────────────────────────────────────
                case Token.COMMENT_EOL:
                    // continues to end of line — handled after loop
                    break;

                // ── Block comment ─────────────────────────────────────────
                case Token.COMMENT_MULTILINE:
                    if (c == '*' && i + 1 < end && array[i + 1] == '/') {
                        i++; // consume the '/'
                        addToken(text, currentTokenStart, i, Token.COMMENT_MULTILINE, newStartOffset + currentTokenStart);
                        currentTokenStart = i + 1;
                        currentTokenType  = Token.NULL;
                    }
                    break;

                // ── Single-quoted string ──────────────────────────────────
                case Token.LITERAL_CHAR:
                    if (c == '\'') {
                        addToken(text, currentTokenStart, i, Token.LITERAL_CHAR, newStartOffset + currentTokenStart);
                        currentTokenStart = i + 1;
                        currentTokenType  = Token.NULL;
                    }
                    break;

                // ── Double-quoted string ──────────────────────────────────
                case Token.LITERAL_STRING_DOUBLE_QUOTE:
                    if (c == '"') {
                        addToken(text, currentTokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart);
                        currentTokenStart = i + 1;
                        currentTokenType  = Token.NULL;
                    }
                    break;

                // ── Number literal ────────────────────────────────────────
                case Token.LITERAL_NUMBER_FLOAT:
                    if (!RSyntaxUtilities.isDigit(c) && c != '.' && c != 'e' && c != 'E'
                            && c != '+' && c != '-' && c != 'i') {
                        addToken(text, currentTokenStart, i - 1, Token.LITERAL_NUMBER_FLOAT, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType  = Token.NULL;
                        i--; // reprocess this character
                    }
                    break;

                // ── Identifier / keyword ──────────────────────────────────
                case Token.IDENTIFIER:
                    if (!RSyntaxUtilities.isLetterOrDigit(c) && c != '_' && c != '%') {
                        int tokenType = getWordTokenType(text, currentTokenStart, i - 1);
                        addToken(text, currentTokenStart, i - 1, tokenType, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType  = Token.NULL;
                        i--; // reprocess
                    }
                    break;

                // ── Whitespace ────────────────────────────────────────────
                case Token.WHITESPACE:
                    if (c != ' ' && c != '\t') {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType  = Token.NULL;
                        i--;
                    }
                    break;

            } // end switch
        } // end for

        // ── Flush whatever token was in progress ─────────────────────────
        switch (currentTokenType) {
            case Token.NULL              -> { /* nothing */ }
            case Token.IDENTIFIER        -> {
                int tokenType = getWordTokenType(text, currentTokenStart, end - 1);
                addToken(text, currentTokenStart, end - 1, tokenType, newStartOffset + currentTokenStart);
            }
            case Token.COMMENT_MULTILINE -> addToken(text, currentTokenStart, end - 1, Token.COMMENT_MULTILINE, newStartOffset + currentTokenStart);
            default                      -> addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
        }

        // EOL comments and unfinished strings persist to next line as NULL
        addNullToken();
        return firstToken;
    }

    /** Look up a completed identifier against the keyword / builtin maps. */
    private int getWordTokenType(Segment text, int start, int end) {
        if (end < start) return Token.IDENTIFIER;
        // Build string from the segment's backing array
        StringBuilder sb = new StringBuilder(end - start + 1);
        for (int i = start; i <= end; i++) {
            sb.append(text.array[i]);
        }
        String word = sb.toString();
        if (KEYWORDS.contains(word)) return Token.RESERVED_WORD;
        if (BUILTINS.contains(word)) return Token.FUNCTION;
        return Token.IDENTIFIER;
    }

    @Override
    public boolean getShouldIndentNextLineAfter(Token token) {
        if (token != null && token.getType() == Token.RESERVED_WORD) {
            String s = token.getLexeme();
            if (s == null) return false;
            switch (s) {
                case "for": case "while": case "if": case "else": case "elseif":
                case "function": case "do": case "try": case "catch":
                case "select": case "case": case "otherwise":
                    return true;
            }
        }
        return false;
    }
}
