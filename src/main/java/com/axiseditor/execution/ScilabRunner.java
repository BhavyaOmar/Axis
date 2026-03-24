package com.axiseditor.execution;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ScilabRunner — executes a Scilab script file via the Scilab CLI and
 * streams stdout/stderr back to the caller through callbacks.
 *
 * Execution model:
 *   1. The caller provides the path to a saved .sce script.
 *   2. ScilabRunner launches:  scilab-cli -quit -nb -f <path>
 *   3. A background thread streams stdout → onOutput callback.
 *   4. A background thread streams stderr → onError callback.
 *   5. On completion, onDone(exitCode) is called on the EDT.
 *
 * Scilab CLI flags used:
 *   -cli   : no GUI (command-line interface)
 *   -quit  : exit after execution
 *   -nb    : no splash / banner
 *   -f     : specify script file
 *
 * Callers call stop() to terminate a running process.
 */
public class ScilabRunner {

    // ── Scilab executable detection ─────────────────────────────────────────

    /**
     * Attempt to find the Scilab CLI executable.
     * Checks in order:
     *   1. SCILAB_HOME env variable
     *   2. Common install locations on Windows / Linux / macOS
     *   3. Just "scilab-cli" (assumes it's on PATH)
     */
    public static String detectScilabPath() {
        // Check env variable
        String scilabHome = System.getenv("SCILAB_HOME");
        if (scilabHome != null) {
            File candidate = new File(scilabHome, "bin/scilab-cli");
            if (candidate.exists()) return candidate.getAbsolutePath();
            candidate = new File(scilabHome, "bin/scilab-cli.exe");
            if (candidate.exists()) return candidate.getAbsolutePath();
        }

        // Platform-specific default locations
        String os = System.getProperty("os.name").toLowerCase();
        List<String> candidates = new ArrayList<>();
        if (os.contains("windows")) {
            // Scilab 6.x uses WScilex-cli.exe or Scilex.exe
            candidates.add("C:\\Program Files\\scilab-6.1.1\\bin\\WScilex-cli.exe");
            candidates.add("C:\\Program Files\\scilab-6.1.1\\bin\\Scilex.exe");
            candidates.add("C:\\Program Files\\scilab-6.1.1\\bin\\scilab-cli.exe");
            // Scilab 2024.x
            candidates.add("C:\\Program Files\\scilab-2024.1.0\\bin\\WScilex-cli.exe");
            candidates.add("C:\\Program Files\\scilab-2024.1.0\\bin\\scilab-cli.exe");
            candidates.add("C:\\Program Files\\scilab-2023.0.0\\bin\\WScilex-cli.exe");
            candidates.add("C:\\Program Files\\scilab-2023.0.0\\bin\\scilab-cli.exe");
            candidates.add("C:\\Program Files (x86)\\scilab-6.1.1\\bin\\WScilex-cli.exe");
        } else if (os.contains("mac")) {
            candidates.add("/Applications/scilab-2024.1.0.app/Contents/MacOS/scilab-cli");
            candidates.add("/usr/local/bin/scilab-cli");
        } else {
            // Linux
            candidates.add("/usr/bin/scilab-cli");
            candidates.add("/usr/local/bin/scilab-cli");
            candidates.add("/opt/scilab/bin/scilab-cli");
        }

        for (String path : candidates) {
            if (new File(path).exists()) return path;
        }

        return "WScilex-cli";  // hope it's on PATH
    }

    // ── Instance fields ─────────────────────────────────────────────────────

    private final String         scilabPath;
    private       Process        process;
    private       Thread         stdoutThread;
    private       Thread         stderrThread;

    // Callbacks (all called from background threads — callers must use SwingUtilities.invokeLater if touching UI)
    private Consumer<String>  onOutput  = s -> {};
    private Consumer<String>  onError   = s -> {};
    private Consumer<Integer> onDone    = code -> {};

    public ScilabRunner() {
        this(detectScilabPath());
    }

    public ScilabRunner(String scilabPath) {
        this.scilabPath = scilabPath;
    }

    // ── Callback setters ─────────────────────────────────────────────────────

    public ScilabRunner onOutput(Consumer<String> cb) { this.onOutput = cb; return this; }
    public ScilabRunner onError(Consumer<String>  cb) { this.onError  = cb; return this; }
    public ScilabRunner onDone(Consumer<Integer>  cb) { this.onDone   = cb; return this; }

    // ── Execute ───────────────────────────────────────────────────────────────

    /**
     * Launch the script in a background thread.
     *
     * @param scriptPath absolute path to the saved .sce / .sci file
     */
    public void run(String scriptPath) {
        if (process != null && process.isAlive()) {
            stop();   // kill any previous run
        }

        Thread runner = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        scilabPath, "-cli", "-quit", "-nb", "-f", scriptPath
                );
                pb.environment().put("TERM", "dumb");   // suppress colour codes
                pb.redirectErrorStream(false);           // keep stdout / stderr separate

                process = pb.start();

                // Stream stdout
                stdoutThread = new Thread(() -> pipeStream(
                        process.getInputStream(), onOutput));
                stdoutThread.setDaemon(true);
                stdoutThread.start();

                // Stream stderr
                stderrThread = new Thread(() -> pipeStream(
                        process.getErrorStream(), onError));
                stderrThread.setDaemon(true);
                stderrThread.start();

                int exitCode = process.waitFor();
                stdoutThread.join(2000);
                stderrThread.join(2000);
                onDone.accept(exitCode);

            } catch (IOException e) {
                onError.accept("Could not launch Scilab.\n" +
                        "Make sure Scilab is installed and SCILAB_HOME is set.\n" +
                        "Tried: " + scilabPath + "\n" +
                        "Error: " + e.getMessage() + "\n");
                onDone.accept(-1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onDone.accept(-2);
            }
        }, "scilab-runner");
        runner.setDaemon(true);
        runner.start();
    }

    /** Terminate the running process. */
    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /** True if a process is currently executing. */
    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void pipeStream(InputStream is, Consumer<String> consumer) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line + "\n");
            }
        } catch (IOException ignored) { }
    }
}
