package com.humlog.whatsapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Drives your NORMAL, already logged-in Google Chrome on macOS through AppleScript.
 * No automation flags, no debug port, no login: LinkedIn just sees your usual browser.
 *
 * One-time setup in Chrome: menu View > Developer > Allow JavaScript from Apple Events.
 * The first run, macOS asks whether your Terminal/Trae may control Google Chrome: click OK.
 */
public class ChromeController {

    private static final String APPLE_QUOTES_UTF8 = "«class utf8»";

    /** Navigates the front tab of Chrome to the given URL. */
    public void openUrl(String url) {
        String safe = url.replace("\\", "\\\\").replace("\"", "\\\"");
        runAppleScript("tell application \"Google Chrome\"\n"
                + "  activate\n"
                + "  if (count of windows) = 0 then make new window\n"
                + "  set URL of active tab of front window to \"" + safe + "\"\n"
                + "end tell");
    }

    /** Runs a JavaScript expression in the front tab and returns its (string) result. */
    public String executeJs(String js) {
        Path jsFile = null;
        try {
            jsFile = Files.createTempFile("chrome-js-", ".js");
            Files.writeString(jsFile, js, StandardCharsets.UTF_8);
            return runAppleScript("set js to read (POSIX file \"" + jsFile.toAbsolutePath() + "\") as " + APPLE_QUOTES_UTF8 + "\n"
                    + "tell application \"Google Chrome\"\n"
                    + "  return (execute active tab of front window javascript js)\n"
                    + "end tell");
        } catch (IOException e) {
            throw new IllegalStateException("Could not write temp script: " + e.getMessage(), e);
        } finally {
            if (jsFile != null) {
                try {
                    Files.deleteIfExists(jsFile);
                } catch (IOException ignored) {
                    // temp file, nothing to do
                }
            }
        }
    }

    /** Loads a bundled script from src/main/resources/js/. */
    public static String script(String name) {
        try (InputStream in = ChromeController.class.getResourceAsStream("/js/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled script: js/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read js/" + name, e);
        }
    }

    private String runAppleScript(String appleScript) {
        Path file = null;
        try {
            file = Files.createTempFile("chrome-ctl-", ".applescript");
            Files.writeString(file, appleScript, StandardCharsets.UTF_8);
            Process p = new ProcessBuilder("osascript", file.toAbsolutePath().toString()).start();
            byte[] out = p.getInputStream().readAllBytes();
            byte[] err = p.getErrorStream().readAllBytes();
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("osascript timed out");
            }
            String stdout = new String(out, StandardCharsets.UTF_8).strip();
            String stderr = new String(err, StandardCharsets.UTF_8).strip();
            if (p.exitValue() != 0) {
                throw new IllegalStateException(explain(stderr));
            }
            return stdout;
        } catch (IOException e) {
            throw new IllegalStateException("Could not run osascript (this needs macOS): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // temp file, nothing to do
                }
            }
        }
    }

    private static String explain(String stderr) {
        String lower = stderr.toLowerCase();
        if (lower.contains("javascript from apple events") || lower.contains("javascript through applescript")) {
            return "Chrome blocks this. In Chrome open menu View > Developer > Allow JavaScript from Apple Events, then run again.";
        }
        if (lower.contains("-1743") || lower.contains("not authorized")) {
            return "macOS blocked control of Chrome. Open System Settings > Privacy & Security > Automation, "
                    + "allow your Terminal (or Trae) to control Google Chrome, then run again.";
        }
        return "AppleScript failed: " + stderr;
    }
}
