package com.humlog.whatsapp.model;

/** A post together with one resolved (final) URL found in it (output of Step 2). */
public record Candidate(Post post, String finalUrl) {
}
