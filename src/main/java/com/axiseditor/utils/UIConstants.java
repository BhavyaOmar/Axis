package com.axiseditor.utils;

import java.awt.*;

/**
 * UIConstants — centralised styling tokens for the Axis IDE.
 *
 * Change values here to restyle the entire application.
 */
public final class UIConstants {

    private UIConstants() {} // utility class — no instances

    // ── Application metadata ────────────────────────────────────────────────
    public static final String APP_TITLE   = "Axis Editor — Scilab IDE";
    public static final int    DEFAULT_WIDTH  = 1200;
    public static final int    DEFAULT_HEIGHT = 750;

    // ── Fonts ───────────────────────────────────────────────────────────────

    /** Monospaced font used in the editor (falls back to Monospaced). */
    public static final Font EDITOR_FONT = loadFont("JetBrains Mono", "Monospaced", 14);

    /** Monospaced font used in the console. */
    public static final Font CONSOLE_FONT = loadFont("Consolas", "Monospaced", 13);

    /** Small sans-serif font for labels and titled borders. */
    public static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    /** Font for toolbar buttons. */
    public static final Font BUTTON_FONT = new Font("SansSerif", Font.PLAIN, 12);

    // ── Colors ──────────────────────────────────────────────────────────────

    public static final Color TOOLBAR_BG   = new Color(245, 245, 248);
    public static final Color STATUSBAR_BG = new Color(240, 240, 244);
    public static final Color CONSOLE_BG   = new Color(30,  30,  30);   // dark terminal style
    public static final Color CONSOLE_FG   = new Color(200, 255, 200);  // soft green text

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Try to load a preferred font, fall back to a safe alternative.
     */
    private static Font loadFont(String preferred, String fallback, int size) {
        // Check if the preferred font is available on this system
        String[] available = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        for (String name : available) {
            if (name.equalsIgnoreCase(preferred)) {
                return new Font(preferred, Font.PLAIN, size);
            }
        }
        return new Font(fallback, Font.PLAIN, size);
    }
}
