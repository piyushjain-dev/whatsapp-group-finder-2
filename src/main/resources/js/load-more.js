(function () {
  var nodes = document.querySelectorAll('button');
  for (var i = 0; i < nodes.length; i++) {
    var t = (nodes[i].innerText || '').trim().toLowerCase();
    if (t === 'load more' || t === 'show more results' || t === 'see more results') {
      nodes[i].scrollIntoView({ block: 'center' });
      nodes[i].click();
      return '1';
    }
  }
  return '0';
})()
