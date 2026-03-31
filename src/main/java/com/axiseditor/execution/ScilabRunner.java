package com.axiseditor.execution;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ScilabRunner {

    // Strips ANSI escape sequences and carriage returns from output
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[^A-Za-z]*[A-Za-z]|\u001B[^\\[\\]]|\r");

    // ── PTY wrapper (written fresh every run) ────────────────────────────────

    private static String getPtyWrapperPath() {
        try {
            File f = new File(System.getProperty("user.home"), ".axis_pty.py");
            writePtyWrapper(f);
            return f.getAbsolutePath();
        } catch (IOException e) {
            log("wrapper write failed: " + e.getMessage());
            return null;
        }
    }

    private static void writePtyWrapper(File f) throws IOException {
        // Key design:
        // - Disables ECHO on slave PTY so scilab doesn't echo typed chars
        // - fwd_in reads from stdin and writes directly to PTY (no extra \n)
        // - fwd_out reads from PTY and writes to stdout for Java to consume
        String s =
            "#!/usr/bin/env python3\n"
          + "import sys,os,pty,select,threading,subprocess,tty\n"
          + "scilab=sys.argv[1]; script=sys.argv[2]\n"
          + "m,sl=pty.openpty()\n"
          + "tty.setraw(sl)\n"
          + "p=subprocess.Popen([scilab,'-quit','-nb','-f',script],"
          +                     "stdin=sl,stdout=sl,stderr=sl,close_fds=True)\n"
          + "os.close(sl)\n"
          + "def out():\n"
          + " while True:\n"
          + "  try:\n"
          + "   r,_,_=select.select([m],[],[],0.05)\n"
          + "   if r:\n"
          + "    d=os.read(m,4096)\n"
          + "    if not d:break\n"
          + "    sys.stdout.buffer.write(d);sys.stdout.buffer.flush()\n"
          + "  except OSError:break\n"
          + "def inp():\n"
          + " while True:\n"
          + "  try:\n"
          + "   r,_,_=select.select([0],[],[],0.05)\n"
          + "   if r:\n"
          + "    d=os.read(0,4096)\n"
          + "    if not d:break\n"
          + "    os.write(m,d)\n"   // NO extra \n - Java already sends \n via println
          + "  except OSError:break\n"
          + "threading.Thread(target=out,daemon=True).start()\n"
          + "threading.Thread(target=inp,daemon=True).start()\n"
          + "p.wait()\n"
          + "try:os.close(m)\n"
          + "except:pass\n"
          + "sys.exit(p.returncode)\n";
        Files.writeString(f.toPath(), s);
        f.setExecutable(true);
        log("wrote PTY wrapper: " + f);
    }

    // ── Scilab path detection ─────────────────────────────────────────────────

    public static String detectScilabPath() {
        String home = System.getenv("SCILAB_HOME");
        if (home != null) {
            for (String rel : new String[]{"bin/scilab-cli","bin/scilab-cli.exe","bin/scilab"}) {
                File f = new File(home, rel);
                if (f.exists()) { log("SCILAB_HOME hit: "+f); return f.getAbsolutePath(); }
            }
        }
        List<String> c = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            c.add("C:\\Program Files\\scilab-2026.0.0\\bin\\WScilex-cli.exe");
            c.add("C:\\Program Files\\scilab-2024.1.0\\bin\\WScilex-cli.exe");
            c.add("C:\\Program Files\\scilab-2024.1.0\\bin\\scilab-cli.exe");
            c.add("C:\\Program Files\\scilab-6.1.1\\bin\\WScilex-cli.exe");
        } else if (os.contains("mac")) {
            c.add("/usr/local/bin/scilab-cli");
            c.add("/usr/local/bin/scilab");
        } else {
            c.add("/usr/bin/scilab-cli");
            c.add("/usr/bin/scilab");
            c.add("/usr/local/bin/scilab-cli");
            c.add("/usr/local/bin/scilab");
            c.add("/opt/scilab/bin/scilab-cli");
            String dl = System.getProperty("user.home") + "/Downloads";
            c.add(dl+"/scilab-2026.0.0.bin.x86_64-linux-gnu/scilab-2026.0.0/bin/scilab-cli");
        }
        for (String p : c) if (new File(p).exists()) { log("found: "+p); return p; }
        log("fallback scilab-cli");
        return "scilab-cli";
    }

    private static String findPython() {
        for (String p : new String[]{"/usr/bin/python3","/usr/local/bin/python3",
                                      "/usr/bin/python","/usr/local/bin/python"})
            if (new File(p).exists()) return p;
        return null;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String      scilabPath;
    private       Process     process;
    private       Thread      stdoutThread;
    private       Thread      stderrThread;
    private       PrintWriter stdinWriter;

    private Consumer<String>  onOutput = s -> {};
    private Consumer<String>  onError  = s -> {};
    private Consumer<Integer> onDone   = code -> {};
    private Consumer<String>  onPrompt = s -> {};

    public ScilabRunner()                  { this(detectScilabPath()); }
    public ScilabRunner(String scilabPath) { this.scilabPath = scilabPath; }

    public ScilabRunner onOutput(Consumer<String>  cb) { onOutput=cb; return this; }
    public ScilabRunner onError(Consumer<String>   cb) { onError=cb;  return this; }
    public ScilabRunner onDone(Consumer<Integer>   cb) { onDone=cb;   return this; }
    public ScilabRunner onPrompt(Consumer<String>  cb) { onPrompt=cb; return this; }

    // ── Run ───────────────────────────────────────────────────────────────────

    public void run(String scriptPath) {
        if (process != null && process.isAlive()) stop();

        Thread t = new Thread(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                String os = System.getProperty("os.name").toLowerCase();

                if (!os.contains("windows")) {
                    String py = findPython();
                    String wr = getPtyWrapperPath();
                    if (py != null && wr != null) {
                        cmd.add(py); cmd.add(wr);
                        cmd.add(scilabPath); cmd.add(scriptPath);
                        log("PTY cmd: "+String.join(" ",cmd));
                    }
                }
                if (cmd.isEmpty()) {
                    cmd.add(scilabPath);
                    cmd.add("-quit"); cmd.add("-nb");
                    cmd.add("-f"); cmd.add(scriptPath);
                    log("direct cmd: "+String.join(" ",cmd));
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.environment().put("TERM","xterm");
                pb.redirectErrorStream(false);
                process = pb.start();
                log("started, alive="+process.isAlive());

                stdinWriter = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(),
                        StandardCharsets.UTF_8)), true);

                stdoutThread = new Thread(
                    ()->pipeWithPrompt(process.getInputStream(),onOutput,onPrompt),
                    "scilab-out");
                stdoutThread.setDaemon(true); stdoutThread.start();

                stderrThread = new Thread(
                    ()->pipeLines(process.getErrorStream(),onError),
                    "scilab-err");
                stderrThread.setDaemon(true); stderrThread.start();

                int code = process.waitFor();
                log("exit="+code);
                stdoutThread.join(2000); stderrThread.join(2000);
                onDone.accept(code);

            } catch (IOException e) {
                onError.accept("Cannot launch Scilab.\nTried: "+scilabPath
                    +"\nError: "+e.getMessage()+"\n");
                onDone.accept(-1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); onDone.accept(-2);
            }
        }, "scilab-runner");
        t.setDaemon(true); t.start();
    }

    public void sendInput(String text) {
        if (stdinWriter!=null && process!=null && process.isAlive()) {
            log("sendInput: '"+text+"'");
            stdinWriter.println(text);
            stdinWriter.flush();
        }
    }

    public void stop() {
        if (process!=null && process.isAlive()) { log("stop"); process.destroyForcibly(); }
        stdinWriter = null;
    }

    public boolean isRunning() { return process!=null && process.isAlive(); }

    // ── stdout: blocking queue + idle-timeout prompt detection ───────────────

    private static final int PROMPT_MS = 300;

    private void pipeWithPrompt(InputStream is,
                                Consumer<String> outCb,
                                Consumer<String> promptCb) {
        final int EOF = -999;
        BlockingQueue<Integer> q = new ArrayBlockingQueue<>(16384);

        // Dedicated blocking reader — is.read() blocks correctly on pipes/PTYs
        Thread reader = new Thread(() -> {
            try {
                int b;
                while ((b = is.read()) != -1) q.put(b);
            } catch (IOException|InterruptedException ignored) {}
            try { q.put(EOF); } catch (InterruptedException ignored) {}
        }, "scilab-bytes");
        reader.setDaemon(true); reader.start();

        StringBuilder buf = new StringBuilder();
        try {
            while (true) {
                Integer b = q.poll(PROMPT_MS, TimeUnit.MILLISECONDS);

                if (b == null) {
                    // Nothing for PROMPT_MS ms
                    if (buf.length() > 0 && process != null && process.isAlive()) {
                        // Idle with buffered content = input() waiting for user
                        String raw = buf.toString();
                        buf.setLength(0);
                        String clean = ANSI.matcher(raw).replaceAll("").trim();
                        // Ignore the scilab --> interactive prompt
                        if (!clean.isEmpty() && !clean.equals("-->")
                                && !clean.startsWith("--> ")) {
                            log("PROMPT detected: '"+clean+"'");
                            promptCb.accept(clean);
                        }
                    } else if (process == null || !process.isAlive()) {
                        break;
                    }
                    continue;
                }

                if (b == EOF) {
                    // Flush remainder
                    if (buf.length() > 0) {
                        String clean = ANSI.matcher(buf.toString()).replaceAll("").trim();
                        if (!clean.isEmpty()) outCb.accept(clean + "\n");
                    }
                    break;
                }

                char c = (char)(b & 0xFF);
                buf.append(c);

                if (c == '\n') {
                    String raw = buf.toString();
                    buf.setLength(0);
                    String clean = ANSI.matcher(raw).replaceAll("").trim();
                    log("out: "+clean);
                    // Filter --> scilab prompts from PTY interactive mode
                    if (!clean.isEmpty() && !clean.equals("-->")
                            && !clean.startsWith("--> ")) {
                        outCb.accept(clean + "\n");
                    }
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log("stdout reader done");
    }

    private void pipeLines(InputStream is, Consumer<String> cb) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String clean = ANSI.matcher(line).replaceAll("").trim();
                log("err: "+clean);
                if (!clean.isEmpty()) cb.accept(clean + "\n");
            }
        } catch (IOException ignored) {}
    }

    // ── Log ──────────────────────────────────────────────────────────────────

    private static final String LOG =
        System.getProperty("user.home") + "/axis_runner.log";

    private static void log(String m) {
        try (PrintWriter w = new PrintWriter(new FileWriter(LOG, true))) {
            w.println("["+java.time.LocalTime.now()+"] "+m);
        } catch (IOException ignored) {}
    }
}