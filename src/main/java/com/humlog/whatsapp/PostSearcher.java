package com.humlog.whatsapp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.humlog.whatsapp.model.Post;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Step 1: search LinkedIn (in your normal Chrome) and collect posts with their full text and links. */
public class PostSearcher {

    private static final Logger LOG = Logger.getLogger(PostSearcher.class.getName());

    static final List<String> KEYWORDS = List.of(
            "#watsapp join React",
            "#whatsapp join React",
            "whatsapp group react developers",
            "join whatsapp group react jobs",
            "whatsapp group hiring react");

    private static final int MAX_SCROLLS = 40;
    private static final int MAX_IDLE_SCROLLS = 5;
    private static final int MAX_EXPANDS_PER_ROUND = 50;

    private static final Pattern AGE = Pattern.compile("(?m)^\\s*(\\d+\\s?(?:s|m|h|d|w|mo|yr)s?)\\b");
    private static final Pattern BLOCKED_URL = Pattern.compile(
            "checkpoint|authwall|/login|/uas/|captcha", Pattern.CASE_INSENSITIVE);

    /** Thrown when LinkedIn shows a captcha / security check / login wall. */
    public static class BlockedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BlockedException(String message) {
            super(message);
        }
    }

    private long maxMinutes = 2;
    private long deadlineNanos = Long.MAX_VALUE;

    /** Total time budget for Step 1; after this the search stops and Steps 2-3 run with what was found. */
    public void setMaxMinutes(long minutes) {
        this.maxMinutes = minutes;
    }

    private boolean timeUp() {
        return System.nanoTime() > deadlineNanos;
    }

    /** Collects posts for all keyword variants. Returns what was collected even if LinkedIn blocks us midway. */
    public List<Post> collect(ChromeController chrome) {
        Map<String, Post> posts = new LinkedHashMap<>();
        deadlineNanos = System.nanoTime() + maxMinutes * 60_000_000_000L;
        try {
            for (String keyword : KEYWORDS) {
                if (timeUp()) {
                    LOG.info("Time limit of " + maxMinutes + " min reached, skipping remaining searches.");
                    break;
                }
                searchOne(chrome, keyword, posts);
            }
        } catch (BlockedException e) {
            LOG.severe("STOPPED: " + e.getMessage()
                    + " Resolve it manually in Chrome and run again later.");
        }
        LOG.info("Step 1 done: " + posts.size() + " unique posts collected.");
        return new ArrayList<>(posts.values());
    }

    private void searchOne(ChromeController chrome, String keyword, Map<String, Post> posts) {
        String url = "https://www.linkedin.com/search/results/content/?keywords="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8).replace("+", "%20")
                + "&origin=SWITCH_SEARCH_VERTICAL";
        LOG.info("Searching: " + keyword);
        chrome.openUrl(url);
        waitForPageLoad(chrome);
        Humanize.pause(3000, 5000);
        assertNotBlocked(chrome);

        int idle = 0;
        for (int i = 1; i <= MAX_SCROLLS; i++) {
            if (timeUp()) {
                LOG.info("  time limit reached, stopping this search.");
                break;
            }
            expandMoreButtons(chrome);
            int before = posts.size();
            readPosts(chrome, posts);
            idle = posts.size() == before ? idle + 1 : 0;
            LOG.info("  scroll " + i + "/" + MAX_SCROLLS + " -> " + posts.size() + " posts");
            if (idle >= MAX_IDLE_SCROLLS) {
                LOG.info("  no new posts for " + MAX_IDLE_SCROLLS + " scrolls, moving on.");
                break;
            }
            if ("1".equals(chrome.executeJs(ChromeController.script("load-more.js")))) {
                LOG.info("  clicked 'Load more'");
                Humanize.pause(3000, 5000);
            }
            chrome.executeJs(ChromeController.script("scroll.js"));
            Humanize.pause(2000, 5000);
            assertNotBlocked(chrome);
        }
    }

    private void waitForPageLoad(ChromeController chrome) {
        for (int i = 0; i < 40; i++) {
            Humanize.pause(500, 700);
            try {
                JsonObject status = status(chrome);
                if ("complete".equals(status.get("ready").getAsString())
                        && status.get("url").getAsString().contains("linkedin.com")) {
                    return;
                }
            } catch (RuntimeException e) {
                // page is still navigating; try again
            }
        }
    }

    private void expandMoreButtons(ChromeController chrome) {
        for (int i = 0; i < MAX_EXPANDS_PER_ROUND; i++) {
            if (!"1".equals(chrome.executeJs(ChromeController.script("expand-one.js")))) {
                return;
            }
            Humanize.pause(300, 800);
        }
    }

    private void readPosts(ChromeController chrome, Map<String, Post> posts) {
        String json = chrome.executeJs(ChromeController.script("read-posts.js"));
        if (json.isBlank() || json.equals("missing value")) {
            return;
        }
        for (Post post : PostsFile.parse(json)) {
            String key = post.postUrl().isEmpty() ? Integer.toHexString(post.fullText().hashCode()) : post.postUrl();
            posts.merge(key, post,
                    (old, fresh) -> fresh.fullText().length() >= old.fullText().length() ? fresh : old);
        }
    }

    Post buildPost(String postUrl, String text, List<String> hrefs) {
        String author = "";
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.equalsIgnoreCase("Feed post")) {
                author = t;
                break;
            }
        }
        Matcher age = AGE.matcher(text);
        return new Post(postUrl, author, age.find() ? age.group(1).replace(" ", "") : "", text, List.copyOf(hrefs));
    }

    private JsonObject status(ChromeController chrome) {
        return JsonParser.parseString(chrome.executeJs(ChromeController.script("status.js"))).getAsJsonObject();
    }

    private void assertNotBlocked(ChromeController chrome) {
        JsonObject status = status(chrome);
        String url = status.get("url").getAsString();
        if (BLOCKED_URL.matcher(url).find()) {
            throw new BlockedException("LinkedIn redirected to " + url
                    + " (login wall, captcha or security check). Make sure you are logged in to LinkedIn in Chrome.");
        }
        String body = status.get("body").getAsString().toLowerCase();
        if (body.contains("security verification") || body.contains("quick security check")
                || body.contains("unusual activity")) {
            throw new BlockedException("LinkedIn is showing a security check / unusual-activity warning.");
        }
    }
}
