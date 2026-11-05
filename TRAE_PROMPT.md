# Paste this into Trae Agent

PROJECT CONTEXT
Humlog is building a job-automation project. This module collects tech-related WhatsApp GROUP / COMMUNITY invite links
from LinkedIn posts (search: "#watsapp join Java"). WhatsApp CHANNELS are NOT wanted. Never join any group.

THE 3 AUTOMATIONS (already implemented, do not redesign)
1. PostSearcher + ChromeController: drives my NORMAL, logged-in Google Chrome on macOS via AppleScript (osascript +
   "execute javascript"). Opens the LinkedIn post search for "#watsapp join Java", clicks "...more", scrolls slowly,
   returns full post text + links. JS helpers live in src/main/resources/js/. Stops on captcha/security check.
2. LinkExtractor: finds lnkd.in, linkedin.com/safety/go/?url=, chat.whatsapp.com, whatsapp.com URLs; follows redirects to the final URL;
   posts without WhatsApp URLs are skipped.
3. LinkValidator: keeps only https://chat.whatsapp.com/<inviteCode>; drops channels (whatsapp.com/channel/*) and other links;
   reads og:title with Jsoup (revoked/invalid -> excluded); keyword relevance; dedupe by invite code.
   Output: output/groups.csv (invite_url, group_name, post_url, author, post_age, status, relevant, members_manual_check).
Member counts are not available without joining, so members_manual_check stays empty.

TECH STACK
Java 17, Maven, Jsoup, Gson, java.net.http.HttpClient. Package com.humlog.whatsapp. NO Playwright, NO Python.

RUN (my default java is 8, ALWAYS use 17)
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
  mvn compile
  mvn test
  mvn exec:java          (needs Chrome menu View > Developer > Allow JavaScript from Apple Events)

STRICT RULES
- Do NOT use Playwright/Selenium/Firefox or launch a new browser. Only ChromeController (AppleScript on my normal Chrome).
- Never ask for or store my LinkedIn credentials.
- If the Trae sandbox blocks osascript or Chrome control, give me the exact command to run in my own Terminal.
- Do not rewrite working code; propose a fix and show the exact error first.

TASKS NOW
1. Read the project and confirm you understand the flow.
2. Run mvn compile and mvn test with Java 17 and report results.
3. Tell me exactly what to do before running mvn exec:java, then wait for my go-ahead.
