package com.axiseditor.phase6;

import com.axiseditor.execution.ScilabRunner;
import com.axiseditor.utils.UIConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestRunner extends JPanel {

    private static final Color BG = new Color(22, 22, 28);
    private static final Color PASS_COL = new Color(80, 200, 100);
    private static final Color FAIL_COL = new Color(244, 100, 80);
    private static final Color SKIP_COL = new Color(160, 160, 100);
    private static final Color INFO_COL = new Color(86, 182, 194);

    public enum Status { PASS, FAIL, RUNNING, PENDING }

    public static class TestResult {
        public final String scriptName;
        public Status status;
        public String message;
        public long durationMs;
        TestResult(String name) {
            scriptName = name;
            status = Status.PENDING;
            message = "";
        }
    }

    private static final Map<String, String> BUILTIN_TESTS = new LinkedHashMap<>();
    static {
        BUILTIN_TESTS.put("test_01_math.sce",
            "// Test 01 - Math Functions\n" +
            "assert_checkequal(abs(-5), 5);\n" +
            "assert_checkalmostequal(sqrt(4), 2, %eps);\n" +
            "assert_checkalmostequal(sin(%pi/2), 1, %eps);\n" +
            "assert_checkalmostequal(cos(0), 1, %eps);\n" +
            "assert_checkequal(floor(3.9), 3);\n" +
            "assert_checkequal(ceil(3.1), 4);\n" +
            "assert_checkequal(mod(10, 3), 1);\n" +
            "disp('test_01_math: all assertions passed');\n"
        );
        BUILTIN_TESTS.put("test_02_strings.sce",
            "// Test 02 - String Operations\n" +
            "assert_checkequal(length('Hello'), 5);\n" +
            "assert_checkequal(convstr('hello','u'), 'HELLO');\n" +
            "assert_checkequal(convstr('WORLD','l'), 'world');\n" +
            "assert_checkequal(strrep('abc','b','X'), 'aXc');\n" +
            "assert_checkequal(strcat('foo','bar'), 'foobar');\n" +
            "disp('test_02_strings: all assertions passed');\n"
        );
        BUILTIN_TESTS.put("test_03_matrix.sce",
            "// Test 03 - Matrix Operations\n" +
            "A = [1 2; 3 4];\n" +
            "assert_checkequal(size(A,1), 2);\n" +
            "assert_checkequal(size(A,2), 2);\n" +
            "assert_checkalmostequal(det(A), -2, %eps);\n" +
            "assert_checkequal(sum(A), 10);\n" +
            "assert_checkequal(zeros(2,2), [0 0; 0 0]);\n" +
            "assert_checkequal(ones(1,3),  [1 1 1]);\n" +
            "disp('test_03_matrix: all assertions passed');\n"
        );
        BUILTIN_TESTS.put("test_04_loops.sce",
            "// Test 04 - Loop Logic\n" +
            "total = 0;\n" +
            "for i = 1:5\n" +
            "    total = total + i;\n" +
            "end\n" +
            "assert_checkequal(total, 15);\n" +
            "n = 1;\n" +
            "while n < 64\n" +
            "    n = n * 2;\n" +
            "end\n" +
            "assert_checkequal(n, 64);\n" +
            "disp('test_04_loops: all assertions passed');\n"
        );
    }

    private final DefaultListModel<TestResult> listModel = new DefaultListModel<>();
    private final JList<TestResult> resultList = new JList<>(listModel);
    private final JLabel summaryLabel = new JLabel("No tests run");
    private final JButton btnRun;
    private File testDir;

    public TestRunner() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);

        JPanel hdr = new JPanel(new BorderLayout(8, 0));
        hdr.setBackground(new Color(30, 30, 40));
        hdr.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        summaryLabel.setFont(UIConstants.LABEL_FONT.deriveFont(Font.BOLD));
        summaryLabel.setForeground(INFO_COL);
        hdr.add(summaryLabel, BorderLayout.WEST);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnP.setBackground(new Color(30, 30, 40));
        JButton btnGenerate = new JButton("Generate Tests");
        btnGenerate.setFont(UIConstants.BUTTON_FONT);
        btnGenerate.setFocusPainted(false);
        btnGenerate.addActionListener(e -> generateBuiltinTests());
        btnRun = new JButton("Run All Tests");
        btnRun.setFont(UIConstants.BUTTON_FONT);
        btnRun.setFocusPainted(false);
        btnRun.setForeground(new Color(80, 210, 80));
        btnRun.addActionListener(e -> runAllTests());
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.setFont(UIConstants.BUTTON_FONT);
        btnBrowse.setFocusPainted(false);
        btnBrowse.addActionListener(e -> browseTestDir());
        btnP.add(btnGenerate);
        btnP.add(btnBrowse);
        btnP.add(btnRun);
        hdr.add(btnP, BorderLayout.EAST);
        add(hdr, BorderLayout.NORTH);

        resultList.setBackground(BG);
        resultList.setFont(UIConstants.CONSOLE_FONT);
        resultList.setCellRenderer(new ResultCellRenderer());
        JScrollPane sc = new JScrollPane(resultList);
        sc.setBackground(BG);
        sc.getViewport().setBackground(BG);
        sc.setBorder(BorderFactory.createEmptyBorder());
        add(sc, BorderLayout.CENTER);

        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 70)),
            "Test Runner",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            UIConstants.LABEL_FONT
        );
        tb.setTitleColor(INFO_COL);
        setBorder(tb);

        testDir = Paths.get(System.getProperty("user.home"), ".axiseditor", "demos", "tests").toFile();
    }

    public void generateBuiltinTests() {
        try {
            Files.createDirectories(testDir.toPath());
            for (Map.Entry<String, String> e : BUILTIN_TESTS.entrySet()) {
                Path p = testDir.toPath().resolve(e.getKey());
                Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
            }
            JOptionPane.showMessageDialog(
                this,
                BUILTIN_TESTS.size() + " test scripts created in:\n" + testDir.getAbsolutePath(),
                "Tests Generated",
                JOptionPane.INFORMATION_MESSAGE
            );
            scanTestDir();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void browseTestDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(testDir);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            testDir = fc.getSelectedFile();
            scanTestDir();
        }
    }

    private void scanTestDir() {
        listModel.clear();
        if (!testDir.exists()) return;
        File[] files = testDir.listFiles(f ->
            f.isFile()
                && (f.getName().startsWith("test_") || f.getName().endsWith("_test.sce"))
                && f.getName().endsWith(".sce"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) listModel.addElement(new TestResult(f.getName()));
        summaryLabel.setText(listModel.size() + " test(s) found in " + testDir.getName());
    }

    private void runAllTests() {
        if (listModel.isEmpty()) {
            scanTestDir();
            if (listModel.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No test scripts found.\nClick 'Generate Tests' first.", "No Tests", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        btnRun.setEnabled(false);
        summaryLabel.setText("Running...");

        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) results.add(listModel.get(i));

        Thread t = new Thread(() -> {
            int pass = 0;
            int fail = 0;
            for (TestResult r : results) {
                r.status = Status.RUNNING;
                SwingUtilities.invokeLater(resultList::repaint);
                runSingle(r);
                if (r.status == Status.PASS) pass++; else fail++;
                SwingUtilities.invokeLater(resultList::repaint);
            }
            final int fp = pass;
            final int ff = fail;
            SwingUtilities.invokeLater(() -> {
                summaryLabel.setText(fp + " passed  |  " + ff + " failed");
                summaryLabel.setForeground(ff == 0 ? PASS_COL : FAIL_COL);
                btnRun.setEnabled(true);
            });
        }, "test-runner");
        t.setDaemon(true);
        t.start();
    }

    private void runSingle(TestResult r) {
        File script = new File(testDir, r.scriptName);
        if (!script.exists()) {
            r.status = Status.FAIL;
            r.message = "File not found";
            return;
        }

        long start = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();

        try {
            ProcessBuilder pb = new ProcessBuilder(ScilabRunner.detectScilabPath(), "-cli", "-quit", "-nb", "-f", script.getAbsolutePath());
            pb.environment().put("TERM", "dumb");
            pb.redirectErrorStream(false);
            Process p = pb.start();

            Thread ot = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = br.readLine()) != null) out.append(l).append("\n");
                } catch (IOException ignored) {}
            });
            Thread et = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = br.readLine()) != null) err.append(l).append("\n");
                } catch (IOException ignored) {}
            });
            ot.setDaemon(true);
            et.setDaemon(true);
            ot.start();
            et.start();

            boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            ot.join(500);
            et.join(500);

            r.durationMs = System.currentTimeMillis() - start;

            if (!finished) {
                p.destroyForcibly();
                r.status = Status.FAIL;
                r.message = "Timeout (>10s)";
                return;
            }

            int code = p.exitValue();
            String errTxt = err.toString();

            if (code == 0 && !errTxt.contains("!--error") && !errTxt.contains("error:")) {
                r.status = Status.PASS;
                r.message = String.format("%.2fs", r.durationMs / 1000.0);
            } else {
                r.status = Status.FAIL;
                String[] lines = errTxt.split("\n");
                r.message = lines.length > 0 ? lines[0].trim() : "Exit code " + code;
            }
        } catch (Exception ex) {
            r.status = Status.FAIL;
            r.message = ex.getMessage();
        }
    }

    private static class ResultCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
            TestResult r = (TestResult) v;
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(i % 2 == 0 ? new Color(28, 28, 35) : BG);
            row.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            String icon;
            Color col;
            switch (r.status) {
                case PASS -> { icon = "OK"; col = PASS_COL; }
                case FAIL -> { icon = "X"; col = FAIL_COL; }
                case RUNNING -> { icon = "..."; col = SKIP_COL; }
                default -> { icon = "o"; col = new Color(120, 120, 120); }
            }
            JLabel ico = new JLabel(icon);
            ico.setFont(UIConstants.CONSOLE_FONT.deriveFont(Font.BOLD));
            ico.setForeground(col);
            ico.setPreferredSize(new Dimension(28, 0));
            JLabel name = new JLabel(r.scriptName);
            name.setFont(UIConstants.CONSOLE_FONT);
            name.setForeground(new Color(200, 200, 200));
            JLabel msg = new JLabel(r.message);
            msg.setFont(UIConstants.LABEL_FONT);
            msg.setForeground(col);
            msg.setHorizontalAlignment(SwingConstants.RIGHT);
            row.add(ico, BorderLayout.WEST);
            row.add(name, BorderLayout.CENTER);
            row.add(msg, BorderLayout.EAST);
            return row;
        }
    }
}
