package com.humlog.whatsapp;

import com.humlog.whatsapp.model.Candidate;
import com.humlog.whatsapp.model.Post;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Step 2: find WhatsApp-related URLs inside posts and resolve redirects to the final URL. */
public class LinkExtractor {

    private static final Logger LOG = Logger.getLogger(LinkExtractor.class.getName());

    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s<>\"')\\]]+");
    private static final Pattern WA_IN_BODY =
            Pattern.compile("https?://(?:chat\\.whatsapp\\.com|whatsapp\\.com)/[A-Za-z0-9/_\\-]+");
    private static final int MAX_HOPS = 6;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Candidate URLs of interest inside one post (text + hrefs), de-duplicated, in order. */
    public static List<String> findCandidateUrls(Post post) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = URL_IN_TEXT.matcher(post.fullText());
        while (m.find()) {
            found.add(stripTrailingPunctuation(m.group()));
        }
        found.addAll(post.hrefs());
        List<String> result = new ArrayList<>();
        for (String url : found) {
            if (isInteresting(url)) {
                result.add(url);
            }
        }
        return result;
    }

    static boolean isInteresting(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        return host.equals("lnkd.in")
                || host.equals("chat.whatsapp.com")
                || host.equals("whatsapp.com") || host.equals("www.whatsapp.com")
                || (host.endsWith("linkedin.com") && url.contains("/safety/go"));
    }

    /** linkedin.com/safety/go/?url=<encoded> -> decoded target, or the input unchanged. */
    public static String unwrapSafety(String url) {
        String host = hostOf(url);
        if (host == null || !host.endsWith("linkedin.com") || !url.contains("/safety/go")) {
            return url;
        }
        int idx = url.indexOf("url=");
        if (idx < 0) {
            return url;
        }
        String value = url.substring(idx + 4);
        int amp = value.indexOf('&');
        if (amp >= 0) {
            value = value.substring(0, amp);
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public enum Kind { GROUP, CHANNEL, OTHER }

    public static Kind classify(String finalUrl) {
        String host = hostOf(finalUrl);
        if (host == null) {
            return Kind.OTHER;
        }
        if (host.equals("chat.whatsapp.com") && InviteCode.extract(finalUrl).isPresent()) {
            return Kind.GROUP;
        }
        if ((host.equals("whatsapp.com") || host.equals("www.whatsapp.com")) && finalUrl.contains("/channel/")) {
            return Kind.CHANNEL;
        }
        return Kind.OTHER;
    }

    /** Extracts the invite code from a chat.whatsapp.com URL. */
    public static final class InviteCode {
        private static final Pattern CODE =
                Pattern.compile("^https?://chat\\.whatsapp\\.com/(?:invite/)?([A-Za-z0-9]{10,})");

        private InviteCode() {
        }

        public static Optional<String> extract(String url) {
            Matcher m = CODE.matcher(url);
            return m.find() ? Optional.of(m.group(1)) : Optional.empty();
        }
    }

    /** Resolves a candidate URL to its final destination, following lnkd.in and LinkedIn safety redirects. */
    public String resolve(String startUrl) {
        String current = unwrapSafety(startUrl);
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            String host = hostOf(current);
            if (host == null || host.contains("whatsapp.com")) {
                return current;
            }
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(current))
                                .header("User-Agent", USER_AGENT)
                                .timeout(Duration.ofSeconds(15))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                Optional<String> location = response.headers().firstValue("Location");
                if (code >= 300 && code < 400 && location.isPresent()) {
                    current = unwrapSafety(URI.create(current).resolve(location.get()).toString());
                    continue;
                }
                Matcher inBody = WA_IN_BODY.matcher(response.body());
                return inBody.find() ? inBody.group() : current;
            } catch (IOException | IllegalArgumentException e) {
                LOG.warning("Could not resolve " + current + ": " + e.getMessage());
                return current;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return current;
            }
        }
        return current;
    }

    /** Runs Step 2 over all posts. Posts without any WhatsApp-related URL are skipped. */
    public List<Candidate> extract(List<Post> posts, Stats stats) {
        List<Candidate> candidates = new ArrayList<>();
        for (Post post : posts) {
            List<String> urls = findCandidateUrls(post);
            if (urls.isEmpty()) {
                continue;
            }
            Set<String> seenInPost = new LinkedHashSet<>();
            for (String url : urls) {
                String finalUrl = resolve(url);
                if (!seenInPost.add(finalUrl)) {
                    continue;
                }
                stats.linksFound++;
                candidates.add(new Candidate(post, finalUrl));
                Humanize.pause(300, 900);
            }
        }
        LOG.info("Step 2 done: " + candidates.size() + " links resolved from " + posts.size() + " posts.");
        return candidates;
    }

    private static String stripTrailingPunctuation(String url) {
        return url.replaceAll("[.,;:!?]+$", "");
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Simple counters printed in the final summary. */
    public static class Stats {
        public int postsScanned;
        public int linksFound;
        public int valid;
        public int invalid;
        public int channelsDropped;
        public int othersDropped;
        public int duplicatesRemoved;
    }
}
