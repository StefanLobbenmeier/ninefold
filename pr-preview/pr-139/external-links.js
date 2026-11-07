// Use the browser's link handling synchronously while the user gesture is active.
window.ninefoldOpenExternalLink = uri => {
    const url = new URL(uri, window.location.href);
    const standaloneExternal = navigator.standalone === true && url.origin !== window.location.origin;
    // Undocumented Safari escape hatch, reported working on iOS 17+ (not iOS 16).
    // This deliberately opens Safari even when another browser is the default.
    // https://christiantietze.de/posts/2023/05/safari-for-mac-url-scheme/
    // Desktop-mode iPads report Version/... rather than an iPhone OS version.
    const iosVersion = /(?:CPU(?: iPhone)? OS |iPhone OS )(\d+)/.exec(navigator.userAgent)
        ?? /Version\/(\d+)/.exec(navigator.userAgent);
    const openSafari = standaloneExternal && url.protocol === 'https:' && Number(iosVersion?.[1]) >= 17;
    const link = document.createElement('a');
    // Prefix only the scheme; embedded URLs in queries/fragments must stay intact.
    link.href = openSafari ? `x-safari-${url.href}` : url.href;
    link.rel = 'external noopener noreferrer';
    // Older iOS and non-HTTPS URLs retain normal system/universal-link handling.
    // _blank can leave an empty in-app sheet after a universal link opens an app:
    // https://bugs.webkit.org/show_bug.cgi?id=236253
    link.target = standaloneExternal ? '_self' : '_blank';
    link.hidden = true;
    document.body.appendChild(link);
    try {
        window.ninefoldPrepareExternalViewport?.();
        link.click();
    } finally {
        link.remove();
    }
};
