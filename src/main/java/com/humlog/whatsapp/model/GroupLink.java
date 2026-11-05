package com.humlog.whatsapp.model;

/** A validated WhatsApp group/community invite, ready for the CSV. */
public record GroupLink(
        String inviteUrl,
        String groupName,
        String postUrl,
        String author,
        String postAge,
        String status,
        boolean relevant) {
}
