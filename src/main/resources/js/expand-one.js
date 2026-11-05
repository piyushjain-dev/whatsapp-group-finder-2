(function () {
  var nodes = document.querySelectorAll('button, span[role="button"]');
  for (var i = 0; i < nodes.length; i++) {
    var t = (nodes[i].innerText || '').trim().toLowerCase().split(' ').join('');
    if (t.length <= 12 && t.slice(-4) === 'more' && (t.indexOf('…') === 0 || t.indexOf('...') === 0)) {
      nodes[i].click();
      return '1';
    }
  }
  return '0';
})()
