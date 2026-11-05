package com.humlog.whatsapp.model;

import java.util.List;

/** A LinkedIn post collected in Step 1. */
public record Post(String postUrl, String author, String postAge, String fullText, List<String> hrefs) {
}
