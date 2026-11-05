package com.humlog.whatsapp;

import com.google.gson.Gson;
import com.humlog.whatsapp.model.Post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Turns the JSON produced by the page scripts (read-posts.js / collect-posts.js) into {@link Post} objects. */
public final class PostsFile {

    private static final Logger LOG = Logger.getLogger(PostsFile.class.getName());

    private PostsFile() {
    }

    /** Shape of one entry in the JSON. */
    private static class Raw {
        String postUrl;
        String fullText;
        List<String> hrefs;
    }

    public static List<Post> parse(String json) {
        List<Post> posts = new ArrayList<>();
        Raw[] raws = new Gson().fromJson(json, Raw[].class);
        if (raws == null) {
            return posts;
        }
        PostSearcher builder = new PostSearcher();
        for (Raw raw : raws) {
            if (raw == null || raw.fullText == null) {
                continue;
            }
            posts.add(builder.buildPost(
                    raw.postUrl == null ? "" : raw.postUrl,
                    raw.fullText,
                    raw.hrefs == null ? List.of() : raw.hrefs));
        }
        return posts;
    }

    public static List<Post> load(Path file) throws IOException {
        List<Post> posts = parse(Files.readString(file, StandardCharsets.UTF_8));
        LOG.info("Loaded " + posts.size() + " posts from " + file.toAbsolutePath());
        return posts;
    }
}
