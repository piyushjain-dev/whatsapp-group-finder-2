package com.humlog.whatsapp;

import com.humlog.whatsapp.LinkExtractor.Stats;
import com.humlog.whatsapp.model.Candidate;
import com.humlog.whatsapp.model.GroupLink;
import com.humlog.whatsapp.model.Post;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

/**
 * Runs Step 1 -> 2 -> 3 and writes output/groups.csv. Never joins any group.
 *
 * Default: Step 1 drives your normal logged-in Google Chrome (macOS).
 * Optional: --posts <file.json> skips Step 1 and reads posts from a file (see collect-posts.js).
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final Path OUTPUT = Paths.get("output", "groups.csv");
    private static final Path DROPPED = Paths.get("output", "dropped.csv");

    public static void main(String[] args) throws IOException {
        Stats stats = new Stats();

        // Step 1 - search LinkedIn and collect posts
        Path postsFile = argValue(args, "--posts");
        PostSearcher searcher = new PostSearcher();
        Path minutes = argValue(args, "--max-minutes");
        if (minutes != null) {
            searcher.setMaxMinutes(Long.parseLong(minutes.toString()));
        }
        List<Post> posts = postsFile != null
                ? PostsFile.load(postsFile)
                : searcher.collect(new ChromeController());
        stats.postsScanned = posts.size();

        // Step 2 - find WhatsApp URLs and resolve redirects
        List<Candidate> candidates = new LinkExtractor().extract(posts, stats);

        // Step 3 - keep groups/communities only, validate, check relevance, write CSV
        LinkValidator validator = new LinkValidator();
        List<GroupLink> groups = validator.validate(candidates, stats);
        validator.writeCsv(groups, OUTPUT);
        validator.writeDroppedCsv(DROPPED);

        printSummary(stats, groups);
    }

    private static Path argValue(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return Paths.get(args[i + 1]);
            }
        }
        return null;
    }

    private static void printSummary(Stats s, List<GroupLink> groups) {
        long relevant = groups.stream().filter(GroupLink::relevant).count();
        LOG.info("""

                ===== SUMMARY =====
                Posts scanned      : %d
                Links found        : %d
                Valid groups       : %d (relevant: %d)
                Invalid / revoked  : %d
                Channels dropped   : %d
                Other links dropped: %d
                Duplicates removed : %d
                Output             : %s
                Dropped links      : %s
                Fill 'members_manual_check' yourself after opening each group invite.
                """.formatted(s.postsScanned, s.linksFound, s.valid, relevant, s.invalid,
                s.channelsDropped, s.othersDropped, s.duplicatesRemoved, OUTPUT.toAbsolutePath(),
                DROPPED.toAbsolutePath()));
    }
}
