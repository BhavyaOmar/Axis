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
        // I/O
        {"disp",           "disp(x) — display value in console"},
        {"printf",         "printf(fmt, ...) — formatted print to console"},
        {"mprintf",        "mprintf(fmt, ...) — formatted print (Scilab native)"},
        {"msprintf",       "msprintf(fmt, ...) — format to string"},
        {"fprintf",        "fprintf(fid, fmt, ...) — print to file"},
        {"sprintf",        "sprintf(fmt, ...) — format to string"},
        {"input",          "input(prompt) — read value from user; input(prompt,'string') for text"},
        // Function definition
        {"deff",           "deff('[y]=f(x)', 'y = expr') — define inline function"},
        {"execstr",        "execstr(str) — execute a string as Scilab code"},
        // Calculus / numerical
        {"numderivative",  "numderivative(f, x) — numerical derivative of f at x"},
        {"derivative",     "derivative(f, x) — alias for numderivative"},
        {"integrate",      "integrate(expr, var, a, b) — symbolic integration"},
        {"intg",           "intg(a, b, f) — numerical integration of f from a to b"},
        {"ode",            "ode(y0, t0, t, f) — solve ODE"},
        {"fsolve",         "fsolve(x0, f) — solve f(x)=0 starting from x0"},
        {"roots",          "roots(p) — roots of polynomial p"},
        {"poly",           "poly(r, var) — build polynomial from roots"},
        // Math
        {"abs",            "abs(x) — absolute value"},
        {"sqrt",           "sqrt(x) — square root"},
        {"exp",            "exp(x) — e^x"},
        {"log",            "log(x) — natural logarithm"},
        {"log2",           "log2(x) — base-2 logarithm"},
        {"log10",          "log10(x) — base-10 logarithm"},
        {"sin",            "sin(x) — sine (radians)"},
        {"cos",            "cos(x) — cosine (radians)"},
        {"tan",            "tan(x) — tangent"},
        {"asin",           "asin(x) — arc sine"},
        {"acos",           "acos(x) — arc cosine"},
        {"atan",           "atan(x) or atan(y,x) — arc tangent"},
        {"floor",          "floor(x) — round toward -inf"},
        {"ceil",           "ceil(x) — round toward +inf"},
        {"round",          "round(x) — round to nearest integer"},
        {"fix",            "fix(x) — round toward zero"},
        {"mod",            "mod(a, b) — a modulo b"},
        {"sign",           "sign(x) — sign of x (-1, 0, or 1)"},
        {"max",            "max(x) — maximum value"},
        {"min",            "min(x) — minimum value"},
        {"sum",            "sum(x) — sum of all elements"},
        {"prod",           "prod(x) — product of all elements"},
        {"mean",           "mean(x) — arithmetic mean"},
        {"cumsum",         "cumsum(x) — cumulative sum"},
        {"diff",           "diff(x) — differences between consecutive elements"},
        {"factorial",      "factorial(n) — n!"},
        {"real",           "real(z) — real part of complex number"},
        {"imag",           "imag(z) — imaginary part"},
        {"conj",           "conj(z) — complex conjugate"},
        // Matrix
        {"zeros",          "zeros(m, n) — m×n zero matrix"},
        {"ones",           "ones(m, n) — m×n matrix of ones"},
        {"eye",            "eye(n) — n×n identity matrix"},
        {"rand",           "rand(m, n) — uniform random matrix [0,1)"},
        {"randn",          "randn(m, n) — standard normal random matrix"},
        {"size",           "size(A) — dimensions [rows, cols] of A"},
        {"length",         "length(x) — largest dimension of x"},
        {"numel",          "numel(A) — total number of elements"},
        {"linspace",       "linspace(a, b, n) — n evenly spaced points from a to b"},
        {"logspace",       "logspace(a, b, n) — n log-spaced points"},
        {"reshape",        "reshape(A, m, n) — reshape A to m×n"},
        {"transpose",      "transpose(A) — matrix transpose (same as A')"},
        {"inv",            "inv(A) — matrix inverse"},
        {"det",            "det(A) — determinant"},
        {"trace",          "trace(A) — sum of diagonal elements"},
        {"norm",           "norm(A) — matrix or vector norm"},
        {"rank",           "rank(A) — matrix rank"},
        {"diag",           "diag(A) — extract diagonal or create diagonal matrix"},
        {"eig",            "eig(A) — eigenvalues and eigenvectors"},
        {"svd",            "svd(A) — singular value decomposition"},
        {"find",           "find(x) — indices of nonzero elements"},
        {"sort",           "sort(x) — sort in ascending order"},
        // String
        {"string",         "string(x) — convert to string"},
        {"num2str",        "num2str(x) — number to string"},
        {"str2num",        "str2num(s) — string to number"},
        {"strcat",         "strcat(a, b) — concatenate strings"},
        {"strsplit",       "strsplit(s, delim) — split string by delimiter"},
        {"strtrim",        "strtrim(s) — remove leading/trailing whitespace"},
        {"upper",          "upper(s) — convert to uppercase"},
        {"lower",          "lower(s) — convert to lowercase"},
        {"strrep",         "strrep(s, old, new) — replace substring"},
        {"strfind",        "strfind(s, pattern) — find pattern in string"},
        {"regexp",         "regexp(s, pattern) — regular expression match"},
        // Type checks
        {"isempty",        "isempty(x) — true if x is empty"},
        {"isnan",          "isnan(x) — true if x is NaN"},
        {"isinf",          "isinf(x) — true if x is infinite"},
        {"isnumeric",      "isnumeric(x) — true if x is numeric"},
        {"ischar",         "ischar(x) — true if x is a string"},
        // Error handling
        {"error",          "error(msg) — throw an error with message"},
        {"warning",        "warning(msg) — display a warning"},
        // Graphics
        {"plot",           "plot(x, y) — 2D line plot"},
        {"plot2d",         "plot2d(x, y) — 2D plot with more options"},
        {"plot3d",         "plot3d(x, y, z) — 3D surface plot"},
        {"bar",            "bar(x) — bar chart"},
        {"hist",           "hist(x, n) — histogram with n bins"},
        {"histplot",       "histplot(n, x) — normalized histogram"},
        {"scatter",        "scatter(x, y) — scatter plot"},
        {"xlabel",         "xlabel(s) — set x-axis label"},
        {"ylabel",         "ylabel(s) — set y-axis label"},
        {"zlabel",         "zlabel(s) — set z-axis label"},
        {"title",          "title(s) — set plot title"},
        {"legend",         "legend(s1, s2, ...) — add legend"},
        {"figure",         "figure() — open new figure window"},
        {"clf",            "clf() — clear current figure"},
        {"hold",           "hold('on') or hold('off') — overlay plots"},
        {"grid",           "grid('on') or grid('off') — show/hide grid"},
        {"subplot",        "subplot(m, n, i) — create subplot grid"},
        {"xs2png",         "xs2png(fig, filename) — save figure as PNG"},
        // Timing
        {"tic",            "tic() — start timer"},
        {"toc",            "toc() — stop timer and return elapsed time"},
        // Misc
        {"clc",            "clc() — clear console"},
        {"clear",          "clear var — remove variable from workspace"},
        {"who",            "who() — list all variables"},
        {"whos",           "whos() — list variables with details"},
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