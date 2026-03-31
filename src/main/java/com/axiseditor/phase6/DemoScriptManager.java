package com.axiseditor.phase6;

import com.axiseditor.ui.ConsolePanel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DemoScriptManager — Phase 6.
 *
 * Writes a set of ready-to-run Scilab demo scripts into
 * <user.home>/.axiseditor/demos/
 */
public class DemoScriptManager {

    private static final String DEMOS_DIR = ".axiseditor/demos";

    private static final Map<String, String> DEMOS = new LinkedHashMap<>();
    static {
        DEMOS.put("01_hello_world.sce",
            "// Demo 01 — Hello World\n" +
            "// Run with Ctrl+R\n" +
            "clc;\n\n" +
            "disp('Hello from Axis IDE!');\n" +
            "disp('Scilab is running correctly.');\n\n" +
            "name = 'Scilab';\n" +
            "version_msg = 'Welcome to ' + name + ' scripting.';\n" +
            "disp(version_msg);\n\n" +
            "mprintf('Pi is approximately: %0.6f\\n', %pi);\n" +
            "mprintf('e  is approximately: %0.6f\\n', %e);\n"
        );

        DEMOS.put("02_basic_math.sce",
            "// Demo 02 — Basic Math Operations\n" +
            "clc;\n\n" +
            "disp('=== Arithmetic ===');\n" +
            "a = 15; b = 4;\n" +
            "mprintf('%d + %d = %d\\n', a, b, a+b);\n" +
            "mprintf('%d - %d = %d\\n', a, b, a-b);\n" +
            "mprintf('%d * %d = %d\\n', a, b, a*b);\n" +
            "mprintf('%d / %d = %0.4f\\n', a, b, a/b);\n" +
            "mprintf('%d ^ %d = %d\\n', a, b, a^b);\n\n" +
            "disp('=== Trigonometry ===');\n" +
            "x = %pi / 4;\n" +
            "mprintf('sin(%0.4f) = %0.6f\\n', x, sin(x));\n" +
            "mprintf('cos(%0.4f) = %0.6f\\n', x, cos(x));\n" +
            "mprintf('tan(%0.4f) = %0.6f\\n', x, tan(x));\n\n" +
            "disp('=== Square root and log ===');\n" +
            "mprintf('sqrt(2)   = %0.6f\\n', sqrt(2));\n" +
            "mprintf('log(1)    = %0.6f\\n', log(1));\n" +
            "mprintf('exp(1)    = %0.6f\\n', exp(1));\n"
        );

        DEMOS.put("03_newton_raphson.sce",
            "// Demo 03 — Newton-Raphson Root Finding\n" +
            "// Uses input() — type your values in the input bar below\n" +
            "clc;\n\n" +
            "func = input('Enter function in x (e.g. x^3 - x - 2) : ', 's');\n" +
            "deff('[y]=f(x)', 'y = ' + func);\n" +
            "deff('[y]=df(x)', 'y = numderivative(f,x)');\n\n" +
            "x0  = input('Enter initial guess : ');\n" +
            "tol = input('Enter tolerance     : ');\n\n" +
            "disp(' Iter          x              f(x)');\n" +
            "i = 1; max_iter = 20;\n\n" +
            "while i <= max_iter\n" +
            "    x1 = x0 - f(x0)/df(x0);\n" +
            "    mprintf('%4d   %14.10f   %14.10f\\n', i, x1, f(x1));\n" +
            "    if abs(x1 - x0) < tol then break; end\n" +
            "    x0 = x1;\n" +
            "    i  = i + 1;\n" +
            "end\n\n" +
            "disp(' ');\n" +
            "mprintf('Approximate Root : %0.10f\\n', x1);\n"
        );

        DEMOS.put("04_plotting.sce",
            "// Demo 04 — 2D Plotting\n" +
            "clc;\n\n" +
            "x = linspace(0, 2*%pi, 200);\n" +
            "y1 = sin(x);\n" +
            "y2 = cos(x);\n\n" +
            "figure();\n" +
            "plot(x, y1, 'b-');\n" +
            "plot(x, y2, 'r--');\n" +
            "xlabel('x (radians)');\n" +
            "ylabel('Amplitude');\n" +
            "title('Sine and Cosine Waves');\n" +
            "legend(['sin(x)'; 'cos(x)']);\n" +
            "disp('Plot generated.');\n"
        );

        DEMOS.put("05_string_operations.sce",
            "// Demo 05 — String Operations\n" +
            "clc;\n\n" +
            "s = 'Hello, Scilab!';\n" +
            "mprintf('Original  : %s\\n', s);\n" +
            "mprintf('Upper     : %s\\n', convstr(s, 'u'));\n" +
            "mprintf('Lower     : %s\\n', convstr(s, 'l'));\n" +
            "mprintf('Length    : %d\\n', length(s));\n\n" +
            "parts = strsplit(s, ', ');\n" +
            "mprintf('Part 1    : %s\\n', parts(1));\n" +
            "mprintf('Part 2    : %s\\n', parts(2));\n\n" +
            "n = 3.14159;\n" +
            "mprintf('Number to string: %s\\n', string(n));\n" +
            "mprintf('String to number: %0.5f\\n', strtod('2.71828'));\n"
        );

        DEMOS.put("06_loops_and_conditions.sce",
            "// Demo 06 — Loops and Conditions\n" +
            "clc;\n\n" +
            "disp('=== for loop ===');\n" +
            "total = 0;\n" +
            "for i = 1:10\n" +
            "    total = total + i;\n" +
            "end\n" +
            "mprintf('Sum 1..10 = %d\\n', total);\n\n" +
            "disp('=== while loop ===');\n" +
            "n = 1;\n" +
            "while n < 100\n" +
            "    n = n * 2;\n" +
            "end\n" +
            "mprintf('First power of 2 >= 100 : %d\\n', n);\n\n" +
            "disp('=== if / elseif / else ===');\n" +
            "x = 42;\n" +
            "if x < 0 then\n" +
            "    disp('Negative');\n" +
            "elseif x == 0 then\n" +
            "    disp('Zero');\n" +
            "elseif x < 100 then\n" +
            "    mprintf('%d is between 0 and 100\\n', x);\n" +
            "else\n" +
            "    disp('Large number');\n" +
            "end\n"
        );

        DEMOS.put("07_functions.sce",
            "// Demo 07 — User-Defined Functions\n" +
            "clc;\n\n" +
            "function [result] = square(x)\n" +
            "    result = x ^ 2;\n" +
            "endfunction\n\n" +
            "function [area, perimeter] = circle(r)\n" +
            "    area      = %pi * r^2;\n" +
            "    perimeter = 2 * %pi * r;\n" +
            "endfunction\n\n" +
            "function [result] = factorial(n)\n" +
            "    if n <= 1 then\n" +
            "        result = 1;\n" +
            "    else\n" +
            "        result = n * factorial(n - 1);\n" +
            "    end\n" +
            "endfunction\n\n" +
            "mprintf('square(7)       = %d\\n', square(7));\n" +
            "[a, p] = circle(5);\n" +
            "mprintf('circle(5) area  = %0.4f\\n', a);\n" +
            "mprintf('circle(5) perim = %0.4f\\n', p);\n" +
            "mprintf('factorial(10)   = %d\\n', factorial(10));\n"
        );

        DEMOS.put("08_matrix_operations.sce",
            "// Demo 08 — Matrix Operations\n" +
            "clc;\n\n" +
            "A = [1 2 3; 4 5 6; 7 8 9];\n" +
            "B = [9 8 7; 6 5 4; 3 2 1];\n\n" +
            "disp('Matrix A:');disp(A);\n" +
            "disp('Matrix B:');disp(B);\n" +
            "disp('A + B:');disp(A+B);\n" +
            "disp('A * B:');disp(A*B);\n\n" +
            "C = [2 1; 5 3];\n" +
            "mprintf('det(C)  = %0.4f\\n', det(C));\n" +
            "disp('inv(C):');disp(inv(C));\n\n" +
            "v = [3; 1; 4; 1; 5];\n" +
            "mprintf('norm(v) = %0.6f\\n', norm(v));\n" +
            "mprintf('sum(v)  = %d\\n',    sum(v));\n" +
            "mprintf('max(v)  = %d\\n',    max(v));\n"
        );
    }

    public static File generateDemos(ConsolePanel console) {
        Path demosPath = Paths.get(System.getProperty("user.home"), DEMOS_DIR);
        try {
            Files.createDirectories(demosPath);
            int written = 0;
            for (Map.Entry<String, String> entry : DEMOS.entrySet()) {
                Path target = demosPath.resolve(entry.getKey());
                if (!Files.exists(target)) {
                    Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                    written++;
                }
            }
            if (written > 0 && console != null) {
                console.appendSuccess("Demo scripts created in: " + demosPath + "\n");
                console.appendInfo("Open the demos folder in the File Explorer to browse them.\n");
            }
        } catch (IOException e) {
            if (console != null) {
                console.appendError("Could not create demo scripts: " + e.getMessage() + "\n");
            }
        }
        return demosPath.toFile();
    }

    public static File getDemosDir() {
        return Paths.get(System.getProperty("user.home"), DEMOS_DIR).toFile();
    }

    public static List<String> getDemoNames() {
        return new ArrayList<>(DEMOS.keySet());
    }
}
