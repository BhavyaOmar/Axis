package com.axiseditor.editor.scilab;

import org.fife.ui.autocomplete.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.*;
import java.util.regex.*;

/**
 * ScilabAutoComplete — installs an AutoCompletion popup on the editor.
 *
 * Completion sources:
 *   1. All Scilab keywords (for, if, while, …)
 *   2. All built-in functions (disp, sqrt, size, …)
 *   3. User-defined variables detected in the current document
 *      (simple regex scan for  name = … and  function [out] = name(…) )
 *
 * Triggers automatically after 1 character; also via Ctrl+Space.
 */
public class ScilabAutoComplete {

    // ── Keyword and builtin lists (mirrors ScilabTokenMaker) ─────────────────

    private static final String[] KEYWORDS = {
        "for", "while", "if", "else", "elseif", "then", "do",
        "end", "endfunction", "break", "continue", "return",
        "select", "case", "otherwise", "try", "catch",
        "function", "global", "clear", "clc",
        "%t", "%f", "%pi", "%e", "%i", "%inf", "%nan", "%eps"
    };

    private static final String[][] BUILTINS_WITH_DESC = {
        {"disp",    "disp(x) — display value"},
        {"printf",  "printf(fmt, ...) — formatted print"},
        {"fprintf", "fprintf(fid, fmt, ...) — print to file"},
        {"sprintf", "sprintf(fmt, ...) — format to string"},
        {"size",    "size(A) — return dimensions of A"},
        {"length",  "length(x) — number of elements"},
        {"zeros",   "zeros(m, n) — zero matrix"},
        {"ones",    "ones(m, n) — matrix of ones"},
        {"eye",     "eye(n) — identity matrix"},
        {"rand",    "rand(m, n) — uniform random matrix"},
        {"linspace","linspace(a, b, n) — linearly spaced vector"},
        {"sqrt",    "sqrt(x) — square root"},
        {"abs",     "abs(x) — absolute value"},
        {"exp",     "exp(x) — e^x"},
        {"log",     "log(x) — natural logarithm"},
        {"sin",     "sin(x) — sine"},
        {"cos",     "cos(x) — cosine"},
        {"max",     "max(x) — maximum value"},
        {"min",     "min(x) — minimum value"},
        {"sum",     "sum(x) — sum of elements"},
        {"mean",    "mean(x) — mean value"},
        {"mod",     "mod(a, b) — a mod b"},
        {"floor",   "floor(x) — round toward -inf"},
        {"ceil",    "ceil(x) — round toward +inf"},
        {"round",   "round(x) — round to nearest"},
        {"num2str", "num2str(x) — convert number to string"},
        {"str2num", "str2num(s) — convert string to number"},
        {"strcat",  "strcat(a, b) — concatenate strings"},
        {"strsplit", "strsplit(s, delim) — split string"},
        {"isempty",  "isempty(x) — true if empty"},
        {"isnan",    "isnan(x) — true if NaN"},
        {"error",    "error(msg) — throw error"},
        {"plot",     "plot(x, y) — 2D line plot"},
        {"xlabel",   "xlabel(s) — x-axis label"},
        {"ylabel",   "ylabel(s) — y-axis label"},
        {"title",    "title(s) — plot title"},
        {"figure",   "figure() — new figure window"},
        {"clf",      "clf() — clear figure"},
    };

    // Pattern to find user-defined variable names:  name = ...  or function ... = name(
    private static final Pattern VAR_PATTERN =
            Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*=");
    private static final Pattern FUNC_PATTERN =
            Pattern.compile("function\\s+.*?=\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

    // ── Install ────────────────────────────────────────────────────────────────

    /**
     * Attach auto-completion to the given text area.
     * Returns the AutoCompletion instance so the caller can hold a reference.
     */
    public static AutoCompletion install(RSyntaxTextArea ta) {
        DefaultCompletionProvider provider = buildProvider();
        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(300);   // ms after last keystroke
        ac.setShowDescWindow(true);
        ac.install(ta);

        // Re-scan the document for user variables whenever it changes
        DocumentScanner scanner = new DocumentScanner(ta, provider, ac);
        ta.getDocument().addDocumentListener(scanner);

        return ac;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static DefaultCompletionProvider buildProvider() {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();

        // Keywords
        for (String kw : KEYWORDS) {
            provider.addCompletion(new BasicCompletion(provider, kw));
        }

        // Built-ins with descriptions
        for (String[] pair : BUILTINS_WITH_DESC) {
            FunctionCompletion fc = new FunctionCompletion(provider, pair[0], null);
            fc.setShortDescription(pair[1]);
            provider.addCompletion(fc);
        }

        return provider;
    }

    /**
     * Scans the document text to find user-defined identifiers and adds them
     * to the provider (removing stale ones first).
     */
    private static class DocumentScanner implements DocumentListener {
        private final RSyntaxTextArea ta;
        private final DefaultCompletionProvider provider;
        private final AutoCompletion ac;
        private final Set<String> addedUserVars = new HashSet<>();

        DocumentScanner(RSyntaxTextArea ta, DefaultCompletionProvider provider, AutoCompletion ac) {
            this.ta       = ta;
            this.provider = provider;
            this.ac       = ac;
        }

        @Override public void insertUpdate(DocumentEvent e)  { scan(); }
        @Override public void removeUpdate(DocumentEvent e)  { scan(); }
        @Override public void changedUpdate(DocumentEvent e) { /* ignore style changes */ }

        private void scan() {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    String text = ta.getDocument().getText(0, ta.getDocument().getLength());
                    Set<String> found = new HashSet<>();
                    Matcher m;

                    m = VAR_PATTERN.matcher(text);
                    while (m.find()) found.add(m.group(1));

                    m = FUNC_PATTERN.matcher(text);
                    while (m.find()) found.add(m.group(1));

                    // Remove variables that no longer exist
                    Set<String> toRemove = new HashSet<>(addedUserVars);
                    toRemove.removeAll(found);
                    for (String old : toRemove) {
                        // getCompletionByInputText returns a List — remove each entry
                        java.util.List<org.fife.ui.autocomplete.Completion> completions =
                                provider.getCompletionByInputText(old);
                        if (completions != null) {
                            for (org.fife.ui.autocomplete.Completion c : completions) {
                                provider.removeCompletion(c);
                            }
                        }
                        addedUserVars.remove(old);
                    }

                    // Add new variables
                    for (String v : found) {
                        if (!addedUserVars.contains(v)) {
                            provider.addCompletion(
                                new BasicCompletion(provider, v, "(user variable)"));
                            addedUserVars.add(v);
                        }
                    }
                } catch (Exception ignored) { }
            });
        }
    }
}
