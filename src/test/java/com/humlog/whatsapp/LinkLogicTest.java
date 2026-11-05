package com.humlog.whatsapp;

import com.humlog.whatsapp.LinkExtractor.Kind;
import com.humlog.whatsapp.model.Post;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkLogicTest {

    @Test
    void findsLnkdInLinksInTextAndIgnoresOthers() {
        Post p = new Post("u", "a", "7mo",
                "#UI WhatsApp group: https://lnkd.in/gdyMg8QH\nread https://example.com/x and https://lnkd.in/g2N75e3i.",
                List.of("https://www.linkedin.com/feed/", "https://lnkd.in/gUYq6EHq"));
        List<String> urls = LinkExtractor.findCandidateUrls(p);
        assertEquals(List.of("https://lnkd.in/gdyMg8QH", "https://lnkd.in/g2N75e3i", "https://lnkd.in/gUYq6EHq"), urls);
    }

    @Test
    void unwrapsLinkedInSafetyRedirect() {
        String safety = "https://www.linkedin.com/safety/go/?url=https%3A%2F%2Flnkd.in%2Fgx&urlhash=abc";
        assertEquals("https://lnkd.in/gx", LinkExtractor.unwrapSafety(safety));
        assertEquals("https://chat.whatsapp.com/ABCdef1234567",
                LinkExtractor.unwrapSafety("https://chat.whatsapp.com/ABCdef1234567"));
    }

    @Test
    void classifiesGroupsChannelsAndOthers() {
        assertEquals(Kind.GROUP, LinkExtractor.classify("https://chat.whatsapp.com/KJh3k4h5k6j7H"));
        assertEquals(Kind.GROUP, LinkExtractor.classify("https://chat.whatsapp.com/KJh3k4h5k6j7H?mode=r_t"));
        assertEquals(Kind.CHANNEL, LinkExtractor.classify("https://whatsapp.com/channel/0029VaABCdef"));
        assertEquals(Kind.OTHER, LinkExtractor.classify("https://www.linkedin.com/in/someone"));
        assertEquals(Kind.OTHER, LinkExtractor.classify("https://chat.whatsapp.com/"));
    }

    @Test
    void extractsInviteCode() {
        assertEquals("KJh3k4h5k6j7H",
                LinkExtractor.InviteCode.extract("https://chat.whatsapp.com/KJh3k4h5k6j7H?mode=r_t").orElseThrow());
    }

    @Test
    void relevanceUsesGroupNameOrPostText() {
        assertTrue(LinkValidator.isRelevant("Java Developers India", "hello"));
        assertTrue(LinkValidator.isRelevant("Friends", "Hiring AEM professionals"));
        assertFalse(LinkValidator.isRelevant("Cricket Fans", "match tonight"));
    }

    @Test
    void csvEscapesQuotesAndNewlines() {
        assertEquals("\"a \"\"b\"\" c d\"", LinkValidator.csv("a \"b\" c\nd"));
    }
}
