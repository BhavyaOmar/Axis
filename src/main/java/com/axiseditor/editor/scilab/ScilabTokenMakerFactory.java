package com.axiseditor.editor.scilab;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;

/**
 * ScilabTokenMakerFactory — registers our custom ScilabTokenMaker with
 * RSyntaxTextArea so we can set:
 *
 *   textArea.setSyntaxEditingStyle("text/scilab");
 *
 * Registration is done once at startup via:
 *   AbstractTokenMakerFactory.setDefaultInstance(new ScilabTokenMakerFactory());
 */
public class ScilabTokenMakerFactory extends AbstractTokenMakerFactory {

    public static final String SYNTAX_STYLE_SCILAB = "text/scilab";

    @Override
    protected void initTokenMakerMap() {
        // Register our Scilab tokeniser under the "text/scilab" MIME type
        putMapping(SYNTAX_STYLE_SCILAB, ScilabTokenMaker.class.getName());
    }
}
