package com.humlog.whatsapp;

import com.humlog.whatsapp.LinkExtractor.Kind;
import com.humlog.whatsapp.LinkExtractor.Stats;
import com.humlog.whatsapp.model.Candidate;
import com.humlog.whatsapp.model.DroppedLink;
import com.humlog.whatsapp.model.GroupLink;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/** Step 3: keep only group/community invites, verify they are alive, check relevance, write CSV. */
public class LinkValidator {

    private static final Logger LOG = Logger.getLogger(LinkValidator.class.getName());

    static final List<String> KEYWORDS = List.of(
            "java", "spring", "backend", "back end", "fullstack", "full stack", "developer", "dev",
            "aem", "adobe", "software", "engineer", "jobs", "job", "hiring", "career", "tech",
            "react", "angular", "vue", "javascript", "typescript", "python", "devops", "qa",
            "testing", "ui", "frontend", "front end", "it ");

    /** WhatsApp shows these generic titles when an invite is revoked or invalid. */
    private static final Set<String> GENERIC_TITLES = Set.of(
            "whatsapp group invite", "whatsapp", "whatsapp community invite", "");

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36";

    private final List<DroppedLink> dropped = new ArrayList<>();

    private void drop(Candidate c, String url, String reason) {
        dropped.add(new DroppedLink(url, reason, c.post().postUrl(), c.post().author()));
    }

    public List<GroupLink> validate(List<Candidate> candidates, Stats stats) {
        Set<String> seenCodes = new HashSet<>();
        List<GroupLink> result = new ArrayList<>();

        for (Candidate candidate : candidates) {
            Kind kind = LinkExtractor.classify(candidate.finalUrl());
            if (kind == Kind.CHANNEL) {
                stats.channelsDropped++;
                drop(candidate, candidate.finalUrl(), "channel");
                continue;
            }
            if (kind == Kind.OTHER) {
                stats.othersDropped++;
                drop(candidate, candidate.finalUrl(), "not_a_whatsapp_group");
                continue;
            }
            String code = LinkExtractor.InviteCode.extract(candidate.finalUrl()).orElseThrow();
            if (!seenCodes.add(code)) {
                stats.duplicatesRemoved++;
                drop(candidate, candidate.finalUrl(), "duplicate");
                continue;
            }

            String inviteUrl = "https://chat.whatsapp.com/" + code;
            Optional<String> name = fetchGroupName(inviteUrl);
            Humanize.pause(1000, 2000);

            if (name.isEmpty()) {
                stats.invalid++;
                drop(candidate, inviteUrl, "invalid_or_revoked");
                LOG.info("INVALID: " + inviteUrl);
                continue;
            }
            stats.valid++;
            boolean relevant = isRelevant(name.get(), candidate.post().fullText());
            result.add(new GroupLink(inviteUrl, name.get(), candidate.post().postUrl(),
                    candidate.post().author(), candidate.post().postAge(), "VALID", relevant));
            LOG.info("VALID: " + name.get() + " -> " + inviteUrl + (relevant ? "" : " (not relevant)"));
        }
        return result;
    }

    /** Returns the group name from og:title, or empty if the invite is revoked/invalid/unreachable. */
    Optional<String> fetchGroupName(String inviteUrl) {
        try {
            Document doc = Jsoup.connect(inviteUrl)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .get();
            Element og = doc.selectFirst("meta[property=og:title]");
            String title = og == null ? "" : og.attr("content").trim();
            if (GENERIC_TITLES.contains(title.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            String body = doc.text().toLowerCase(Locale.ROOT);
            if (body.contains("invite link is invalid") || body.contains("invite link was reset")
                    || body.contains("link has been revoked") || body.contains("link expired")) {
                return Optional.empty();
            }
            return Optional.of(title);
        } catch (IOException e) {
            LOG.warning("Could not fetch " + inviteUrl + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    static boolean isRelevant(String groupName, String postText) {
        String haystack = (groupName + " " + postText).toLowerCase(Locale.ROOT);
        for (String keyword : KEYWORDS) {
            if (keyword.length() <= 3) {
                if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(keyword.trim()) + "\\b")
                        .matcher(haystack).find()) {
                    return true;
                }
            } else if (haystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public void writeCsv(List<GroupLink> links, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("invite_url,group_name,post_url,author,post_age,status,relevant,members_manual_check\n");
            for (GroupLink g : links) {
                w.write(String.join(",",
                        csv(g.inviteUrl()), csv(g.groupName()), csv(g.postUrl()), csv(g.author()),
                        csv(g.postAge()), csv(g.status()), String.valueOf(g.relevant()), ""));
                w.write("\n");
            }
        }
    }

    public void writeDroppedCsv(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("url,reason,post_url,author\n");
            for (DroppedLink d : dropped) {
                w.write(String.join(",", csv(d.url()), csv(d.reason()), csv(d.postUrl()), csv(d.author())));
                w.write("\n");
            }
        }
    }

    static String csv(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
