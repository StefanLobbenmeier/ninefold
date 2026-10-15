// Use the browser's link handling synchronously while the user gesture is active.
window.ninefoldOpenExternalLink = uri => {
    const url = new URL(uri, window.location.href);
    const link = document.createElement('a');
    link.href = url.href;
    link.rel = 'external noopener noreferrer';
    // iOS usually presents cross-origin links in a browser sheet.
    // _self preserves universal-link handoff; it does not force an external browser.
    // _blank can leave an empty in-app sheet after a universal link opens an app:
    // https://bugs.webkit.org/show_bug.cgi?id=236253
    link.target = navigator.standalone === true && url.origin !== window.location.origin
        ? '_self' : '_blank';
    link.hidden = true;
    document.body.appendChild(link);
    try {
        window.ninefoldPrepareExternalViewport?.();
        link.click();
    } finally {
        link.remove();
    }
};
