package com.humlog.whatsapp.model;

/** A link that was found but not kept, with the reason (written to output/dropped.csv). */
public record DroppedLink(String url, String reason, String postUrl, String author) {
}
