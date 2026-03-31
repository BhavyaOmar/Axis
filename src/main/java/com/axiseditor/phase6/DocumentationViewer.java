package com.axiseditor.phase6;

import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

public class DocumentationViewer extends JDialog {

    public static class DocEntry {
        public final String name;
        public final String category;
        public final String signature;
        public final String description;
        public final String example;

        DocEntry(String n, String c, String sig, String desc, String ex) {
            name = n;
            category = c;
            signature = sig;
            description = desc;
            example = ex;
        }

        @Override
        public String toString() { return name; }
    }

    private static final List<DocEntry> ALL_DOCS = new ArrayList<>();
    static {
        a("abs", "Math", "abs(x)", "Absolute value of x.", "abs(-5)");
        a("sqrt", "Math", "sqrt(x)", "Square root of x.", "sqrt(16)");
        a("exp", "Math", "exp(x)", "e raised to the power x.", "exp(1)");
        a("log", "Math", "log(x)", "Natural logarithm of x.", "log(%e)");
        a("log10", "Math", "log10(x)", "Base-10 logarithm of x.", "log10(100)");
        a("sin", "Math", "sin(x)", "Sine of x (x in radians).", "sin(%pi/2)");
        a("cos", "Math", "cos(x)", "Cosine of x (x in radians).", "cos(0)");
        a("tan", "Math", "tan(x)", "Tangent of x (x in radians).", "tan(%pi/4)");
        a("floor", "Math", "floor(x)", "Round x toward negative infinity.", "floor(3.7)");
        a("ceil", "Math", "ceil(x)", "Round x toward positive infinity.", "ceil(3.2)");
        a("round", "Math", "round(x)", "Round x to the nearest integer.", "round(3.5)");
        a("mod", "Math", "mod(a,b)", "Modulus: remainder after a/b.", "mod(10,3)");
        a("max", "Math", "max(x)", "Maximum value of x.", "max([3 1 4 1 5])");
        a("min", "Math", "min(x)", "Minimum value of x.", "min([3 1 4 1 5])");
        a("sum", "Math", "sum(x)", "Sum of all elements.", "sum([1 2 3 4 5])");
        a("mean", "Math", "mean(x)", "Arithmetic mean of x.", "mean([2 4 6 8])");
        a("numderivative", "Math", "numderivative(f,x)", "Numerical derivative of function f at x.", "deff('[y]=f(x)','y=x^2'); numderivative(f,3)");
        a("zeros", "Matrix", "zeros(m,n)", "m×n matrix of zeros.", "zeros(3,3)");
        a("ones", "Matrix", "ones(m,n)", "m×n matrix of ones.", "ones(2,4)");
        a("eye", "Matrix", "eye(n)", "n×n identity matrix.", "eye(3)");
        a("rand", "Matrix", "rand(m,n)", "m×n uniform random matrix [0,1].", "rand(3,3)");
        a("size", "Matrix", "size(A)", "Returns [rows, cols] of matrix A.", "size([1 2; 3 4])");
        a("length", "Matrix", "length(x)", "Number of elements (or longest dimension).", "length([1 2 3 4 5])");
        a("det", "Matrix", "det(A)", "Determinant of square matrix A.", "det([1 2; 3 4])");
        a("inv", "Matrix", "inv(A)", "Inverse of square matrix A.", "inv([2 1; 5 3])");
        a("norm", "Matrix", "norm(x)", "Euclidean norm of vector or matrix.", "norm([3 4])");
        a("linspace", "Matrix", "linspace(a,b,n)", "n evenly spaced points from a to b.", "linspace(0, 1, 5)");
        a("reshape", "Matrix", "reshape(A,m,n)", "Reshape A to m×n matrix.", "reshape(1:6, 2, 3)");
        a("diag", "Matrix", "diag(A)", "Extract diagonal or create diagonal matrix.", "diag([1 2 3])");
        a("disp", "I/O", "disp(x)", "Display value x in console.", "disp('Hello World')");
        a("mprintf", "I/O", "mprintf(fmt,...)", "Formatted print to console.", "mprintf('%d + %d = %d\\n', 2, 3, 2+3)");
        a("input", "I/O", "input(prompt,'s')", "Read a string from user input.", "name = input('Your name: ', 's')");
        a("string", "String", "string(x)", "Convert number x to string.", "string(3.14)");
        a("num2str", "String", "num2str(x)", "Convert number x to string.", "num2str(42)");
        a("strtod", "String", "strtod(s)", "Convert string s to double.", "strtod('3.14')");
        a("strcat", "String", "strcat(a,b)", "Concatenate strings a and b.", "strcat('Hello', ' World')");
        a("strsplit", "String", "strsplit(s,delim)", "Split string s by delimiter.", "strsplit('a,b,c', ',')");
        a("length", "String", "length(s)", "Number of characters in string s.", "length('Hello')");
        a("convstr", "String", "convstr(s,'u'/'l')", "Convert string to upper ('u') or lower ('l').", "convstr('Hello', 'u')");
        a("strfind", "String", "strfind(s,sub)", "Find positions of sub in s.", "strfind('abcabc', 'b')");
        a("strrep", "String", "strrep(s,old,new)", "Replace old with new in s.", "strrep('Hello World', 'World', 'Scilab')");
        a("deff", "Functions", "deff('[y]=f(x)','y=...')", "Define a function inline.", "deff('[y]=sq(x)', 'y=x^2'); sq(5)");
        a("error", "Control", "error(msg)", "Throw a runtime error with message.", "error('Something went wrong')");
        a("break", "Control", "break", "Exit the current loop.", "for i=1:10; if i==5 then break; end; end");
        a("continue", "Control", "continue", "Skip to the next loop iteration.", "for i=1:5; if i==3 then continue; end; disp(i); end");
        a("plot", "Plot", "plot(x,y)", "Plot y versus x as a 2D line.", "x=0:0.1:2*%pi; plot(x,sin(x))");
        a("plot2d", "Plot", "plot2d(x,y)", "Advanced 2D plot with style options.", "x=linspace(0,4,100); plot2d(x,x.^2)");
        a("xlabel", "Plot", "xlabel(s)", "Set x-axis label.", "xlabel('Time (s)')");
        a("ylabel", "Plot", "ylabel(s)", "Set y-axis label.", "ylabel('Amplitude')");
        a("title", "Plot", "title(s)", "Set plot title.", "title('My Plot')");
        a("figure", "Plot", "figure()", "Open a new figure window.", "figure()");
        a("clf", "Plot", "clf()", "Clear the current figure.", "clf()");
        a("legend", "Plot", "legend(labels)", "Add a legend to the current axes.", "legend(['sin(x)'; 'cos(x)'])");
    }

    private static void a(String n, String c, String sig, String desc, String ex) {
        ALL_DOCS.add(new DocEntry(n, c, sig, desc, ex));
    }

    private final JTextField searchField = new JTextField();
    private final DefaultListModel<DocEntry> listModel = new DefaultListModel<>();
    private final JList<DocEntry> docList = new JList<>(listModel);
    private final JLabel sigLabel = new JLabel(" ");
    private final JTextArea descArea = new JTextArea(3, 30);
    private final JTextArea exArea = new JTextArea(3, 30);
    private Runnable onInsertExample;

    public DocumentationViewer(Frame owner) {
        super(owner, "Scilab Function Reference", false);
        buildUI();
        populateList("");
        pack();
        setSize(680, 520);
        setLocationRelativeTo(owner);
    }

    public void setOnInsertExample(Runnable r) { onInsertExample = r; }

    public String getSelectedExample() {
        DocEntry e = docList.getSelectedValue();
        return e != null ? e.example : "";
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchField.setFont(UIConstants.LABEL_FONT);
        searchField.putClientProperty("JTextField.placeholderText", "Search functions...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            void update() { populateList(searchField.getText().trim()); }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });
        JLabel lbl = new JLabel("?");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchRow.add(lbl, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        root.add(searchRow, BorderLayout.NORTH);

        docList.setFont(UIConstants.CONSOLE_FONT);
        docList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        docList.setCellRenderer(new DocCellRenderer());
        docList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showEntry(docList.getSelectedValue());
        });
        JScrollPane listScroll = new JScrollPane(docList);
        listScroll.setPreferredSize(new Dimension(220, 0));

        sigLabel.setFont(UIConstants.CONSOLE_FONT.deriveFont(Font.BOLD));
        sigLabel.setForeground(new Color(86, 156, 214));
        sigLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        descArea.setFont(UIConstants.LABEL_FONT);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBackground(new Color(245, 245, 245));

        exArea.setFont(UIConstants.CONSOLE_FONT);
        exArea.setEditable(false);
        exArea.setBackground(new Color(30, 30, 30));
        exArea.setForeground(new Color(200, 220, 200));
        exArea.setMargin(new Insets(4, 6, 4, 6));

        TitledBorder descBorder = BorderFactory.createTitledBorder("Description");
        TitledBorder exBorder = BorderFactory.createTitledBorder("Example");

        JPanel detail = new JPanel(new BorderLayout(0, 6));
        detail.add(sigLabel, BorderLayout.NORTH);
        JPanel mid = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBorder(descBorder);
        descPanel.add(new JScrollPane(descArea), BorderLayout.CENTER);
        JPanel exPanel = new JPanel(new BorderLayout());
        exPanel.setBorder(exBorder);
        exPanel.add(new JScrollPane(exArea), BorderLayout.CENTER);
        mid.add(descPanel);
        mid.add(exPanel);
        detail.add(mid, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton copyBtn = new JButton("Copy Example");
        copyBtn.setFont(UIConstants.BUTTON_FONT);
        copyBtn.setFocusPainted(false);
        copyBtn.addActionListener(e -> {
            DocEntry d = docList.getSelectedValue();
            if (d != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(d.example), null);
            }
        });
        JButton insertBtn = new JButton("Insert into Editor");
        insertBtn.setFont(UIConstants.BUTTON_FONT);
        insertBtn.setFocusPainted(false);
        insertBtn.addActionListener(e -> { if (onInsertExample != null) onInsertExample.run(); });
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(UIConstants.BUTTON_FONT);
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> setVisible(false));
        btnRow.add(copyBtn);
        btnRow.add(insertBtn);
        btnRow.add(closeBtn);
        detail.add(btnRow, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detail);
        split.setDividerLocation(200);
        split.setResizeWeight(0.0);
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);
        getRootPane().registerKeyboardAction(e -> setVisible(false),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void populateList(String query) {
        listModel.clear();
        String q = query.toLowerCase();
        for (DocEntry e : ALL_DOCS) {
            if (q.isEmpty()
                || e.name.toLowerCase().contains(q)
                || e.category.toLowerCase().contains(q)
                || e.description.toLowerCase().contains(q)) {
                listModel.addElement(e);
            }
        }
        if (listModel.size() > 0) docList.setSelectedIndex(0);
    }

    private void showEntry(DocEntry e) {
        if (e == null) {
            sigLabel.setText(" ");
            descArea.setText("");
            exArea.setText("");
            return;
        }
        sigLabel.setText(e.signature + "   [" + e.category + "]");
        descArea.setText(e.description);
        exArea.setText(e.example);
    }

    private static class DocCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
            super.getListCellRendererComponent(l, v, i, sel, foc);
            DocEntry e = (DocEntry) v;
            setText("<html><b>" + e.name + "</b>  <font color='#888888'>" + e.category + "</font></html>");
            setFont(UIConstants.LABEL_FONT);
            return this;
        }
    }
}
