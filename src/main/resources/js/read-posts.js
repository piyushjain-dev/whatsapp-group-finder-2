(function () {
  var selectors = [
    'div.feed-shared-update-v2',
    '[data-view-name="feed-full-update"]',
    'li.reusable-search__result-container',
    'div[data-urn^="urn:li:activity"]',
    'div[role="listitem"]'
  ];
  var nodes = [];
  for (var s = 0; s < selectors.length; s++) {
    nodes = Array.prototype.slice.call(document.querySelectorAll(selectors[s]));
    if (nodes.length) break;
  }
  var out = [];
  nodes.forEach(function (n) {
    var text = (n.innerText || '').trim();
    if (text.length < 30) return;
    var hrefs = Array.prototype.slice.call(n.querySelectorAll('a[href]')).map(function (a) { return a.href; });
    var postUrl = '';
    for (var i = 0; i < hrefs.length; i++) {
      if (hrefs[i].indexOf('/feed/update/') >= 0 || hrefs[i].indexOf('/posts/') >= 0
          || hrefs[i].indexOf('activity-') >= 0 || hrefs[i].indexOf('urn:li:activity') >= 0) {
        postUrl = hrefs[i].split('?')[0];
        break;
      }
    }
    var urnNode = n.getAttribute('data-urn') ? n : n.querySelector('[data-urn]');
    var urn = urnNode ? (urnNode.getAttribute('data-urn') || '') : '';
    if (!postUrl && urn.indexOf('urn:li:') === 0) postUrl = 'https://www.linkedin.com/feed/update/' + urn;
    out.push({ postUrl: postUrl, fullText: text, hrefs: hrefs });
  });
  return JSON.stringify(out);
})()
