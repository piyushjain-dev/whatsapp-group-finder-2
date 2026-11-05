(function () {
  return JSON.stringify({
    url: location.href,
    ready: document.readyState,
    body: (document.body ? document.body.innerText : '').slice(0, 3000)
  });
})()
