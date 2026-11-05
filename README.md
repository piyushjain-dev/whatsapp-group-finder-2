# WhatsApp Group Finder (Java 17)

Fully automatic: finds LinkedIn posts for `#watsapp join Java`, extracts WhatsApp links, and keeps only
valid, relevant WhatsApp **group/community** invites (no channels). Never joins any group.

## How it works
1. **Step 1 - search & collect** (`PostSearcher` + `ChromeController`): Java drives your NORMAL, already logged-in
   Google Chrome (macOS, via AppleScript). Opens the LinkedIn post search, clicks every "...more", scrolls slowly
   (random 2-5s pauses, max 30 scrolls), reads full post text + links. Stops if LinkedIn shows a captcha/security check.
2. **Step 2 - WhatsApp URLs** (`LinkExtractor`): finds lnkd.in / linkedin safety / whatsapp links and follows redirects
   to the final URL. Posts without a WhatsApp URL are skipped.
3. **Step 3 - filter & validate** (`LinkValidator`): keeps `chat.whatsapp.com/<code>` only, drops channels and other links,
   reads the group name from the invite page (revoked/invalid -> excluded), checks tech relevance, dedupes.
   Output: `output/groups.csv`.

No LinkedIn password is ever used, asked for or stored.

## One-time setup
1. Log in to LinkedIn in your normal Chrome (you probably already are).
2. In Chrome menu: **View > Developer > Allow JavaScript from Apple Events** (tick it).
3. First run: macOS asks if Terminal/Trae may control Google Chrome. Click OK
   (or System Settings > Privacy & Security > Automation).

## Run
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home   # default java 8 will NOT work
mvn compile
mvn exec:java
```
Keep Chrome open and don't use that tab while it runs (it navigates the front tab of the front window).
Tip: open a separate new Chrome window just for this run.

### Fallback (any OS): collect posts yourself
Paste `collect-posts.js` into the DevTools console on the LinkedIn search page, copy the downloaded
`posts.json` to `output/`, then: `mvn exec:java -Dexec.args="--posts output/posts.json"`

## Output
`output/groups.csv`: invite_url, group_name, post_url, author, post_age, status, relevant, members_manual_check.
Member counts are not visible without joining, so fill `members_manual_check` yourself.

## Notes
- If Step 1 finds 0 posts, LinkedIn changed its markup: adjust selectors in `src/main/resources/js/read-posts.js`.
- Automating LinkedIn is against its terms of use; keep volumes small (the defaults are conservative).
