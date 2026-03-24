package com.axiseditor.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

/**
 * ErrorParser — extracts structured error information from Scilab's stderr output.
 *
 * Scilab typically prints errors in one of these formats:
 *
 *   !--error 4
 *   Undefined variable: myVar
 *   at line    12 of function foo called by :
 *   at line    5 of exec file called by :
 *
 * Or (Scilab 6+):
 *   at line 12 column 3
 *
 * We produce a list of ParsedError objects, one per detected error site.
 */
public class ErrorParser {

    // ── Error record ──────────────────────────────────────────────────────────

    public static class ParsedError {
        public final int    lineNumber;   // 1-based, -1 if unknown
        public final String message;

        public ParsedError(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message    = message;
        }

        @Override
        public String toString() {
            return lineNumber > 0
                ? "[Line " + lineNumber + "] " + message
                : message;
        }
    }

    // ── Patterns ──────────────────────────────────────────────────────────────

    // "at line   12 of ..."
    private static final Pattern AT_LINE_OF = Pattern.compile(
            "at\\s+line\\s+(\\d+)\\s+of",
            Pattern.CASE_INSENSITIVE);

    // "at line 12 column 3"
    private static final Pattern AT_LINE_COL = Pattern.compile(
            "at\\s+line\\s+(\\d+)\\s+column\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    // "!--error N"  or  "--error:"
    private static final Pattern ERROR_MARKER = Pattern.compile(
            "!--error|-->error|error\\s*:",
            Pattern.CASE_INSENSITIVE);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse a block of stderr text and return all detected errors.
     * The list may be empty if the text contains no recognisable error pattern.
     */
    public static List<ParsedError> parse(String stderr) {
        List<ParsedError> errors = new ArrayList<>();
        if (stderr == null || stderr.isBlank()) return errors;

        String[] lines = stderr.split("\n");
        String  pendingMessage = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Detect an error header
            if (ERROR_MARKER.matcher(line).find()) {
                // Grab the message from the NEXT non-empty line
                if (i + 1 < lines.length) {
                    pendingMessage = lines[i + 1].trim();
                } else {
                    pendingMessage = line;
                }
                continue;
            }

            // Detect "at line N ..."  (this tells us the line number)
            Matcher m1 = AT_LINE_OF.matcher(line);
            Matcher m2 = AT_LINE_COL.matcher(line);

            Matcher matched = null;
            if (m1.find()) matched = m1;
            else if (m2.find()) matched = m2;

            if (matched != null) {
                int lineNo = Integer.parseInt(matched.group(1));
                String msg = pendingMessage != null ? pendingMessage : line;
                errors.add(new ParsedError(lineNo, msg));
                pendingMessage = null;
            }
        }

        // If we have a pending message but never found a line number
        if (pendingMessage != null && !pendingMessage.isEmpty()) {
            errors.add(new ParsedError(-1, pendingMessage));
        }

        return errors;
    }

    /**
     * Extract the first error line number from stderr, or -1 if not found.
     * Convenience wrapper for the common single-error case.
     */
    public static int firstErrorLine(String stderr) {
        List<ParsedError> errors = parse(stderr);
        for (ParsedError e : errors) {
            if (e.lineNumber > 0) return e.lineNumber;
        }
        return -1;
    }
}
