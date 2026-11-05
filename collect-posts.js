// Paste this into the DevTools Console of your NORMAL, logged-in Chrome,
// on the LinkedIn search page for "#watsapp join Java" (Posts tab).
// It expands "...more", scrolls slowly (human speed, max 30 scrolls) and downloads posts.json.
(async () => {
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const rnd = (a, b) => a + Math.random() * (b - a);
  const sels = ['div.feed-shared-update-v2', '[data-view-name="feed-full-update"]',
    'li.reusable-search__result-container', 'div[data-urn^="urn:li:activity"]', 'div[role="listitem"]'];
  const moreRe = /^\s*(…|\.\.\.)\s*(see\s+)?more\s*$/i;
  const posts = new Map();

  const read = () => {
    let nodes = [];
    for (const s of sels) { nodes = [...document.querySelectorAll(s)]; if (nodes.length) break; }
    for (const n of nodes) {
      const text = (n.innerText || '').trim();
      if (text.length < 30) continue;
      const hrefs = [...n.querySelectorAll('a[href]')].map(a => a.href);
      const pu = hrefs.find(h => h.includes('/feed/update/') || h.includes('/posts/'));
      const urn = n.getAttribute('data-urn') || '';
      const postUrl = pu ? pu.split('?')[0]
        : (urn.startsWith('urn:li:') ? 'https://www.linkedin.com/feed/update/' + urn : '');
      const key = postUrl || text.slice(0, 80);
      const old = posts.get(key);
      if (!old || text.length >= old.fullText.length) posts.set(key, { postUrl, fullText: text, hrefs });
    }
  };

  let idle = 0;
  for (let i = 1; i <= 30; i++) {
    for (let g = 0; g < 50; g++) {
      const b = [...document.querySelectorAll('button,span[role="button"]')]
        .find(e => moreRe.test(e.innerText || ''));
      if (!b) break;
      b.click();
      await sleep(rnd(300, 800));
    }
    const before = posts.size;
    read();
    idle = posts.size === before ? idle + 1 : 0;
    console.log(`scroll ${i}/30 -> ${posts.size} posts`);
    if (idle >= 4) break;
    window.scrollBy(0, 700 + Math.random() * 400);
    await sleep(rnd(2000, 5000));
  }

  const blob = new Blob([JSON.stringify([...posts.values()], null, 1)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'posts.json';
  a.click();
  console.log('Done. posts.json downloaded with', posts.size, 'posts');
})();
