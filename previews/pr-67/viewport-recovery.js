// iOS can retain the browser sheet's toolbar inset after an external link closes.
// https://bugs.webkit.org/show_bug.cgi?id=244264
(() => {
    if (navigator.standalone !== true) return;
    const viewport = document.querySelector('meta[name="viewport"]');
    if (!viewport) return;

    let beforeLink;
    let attempts = 0;
    let restoring = false;
    let timers = [];
    const orientation = () => window.screen.orientation?.angle ?? window.orientation;
    const editing = () => document.activeElement?.matches(
        'input, textarea, select, [contenteditable]:not([contenteditable="false"])',
    );
    const zoomed = () => Math.abs((window.visualViewport?.scale ?? 1) - 1) > 0.01;
    const cancelTimers = () => {
        timers.forEach(clearTimeout);
        timers = [];
    };

    window.ninefoldPrepareExternalViewport = () => {
        cancelTimers();
        beforeLink = !editing() && !zoomed() ? {
            width: window.innerWidth, height: window.innerHeight, orientation: orientation(),
        } : undefined;
        attempts = 0;
    };

    function recover() {
        if (!beforeLink || restoring || document.visibilityState !== 'visible') return;
        if (orientation() !== beforeLink.orientation || Math.abs(window.innerWidth - beforeLink.width) > 1) {
            // Rotation and window resizing invalidate the old dimensions.
            beforeLink = undefined;
            return;
        }
        if (editing() || zoomed() || window.innerHeight >= beforeLink.height - 1 || attempts >= 3) return;
        attempts++;
        restoring = true;
        const original = viewport.content;
        // Change viewport policy briefly to invalidate WebKit's cached geometry.
        // Restore the exact policy; do not pin the app to an old pixel height or reload it.
        const fit = /viewport-fit\s*=\s*[^,]+/i;
        const temporary = /viewport-fit\s*=\s*cover/i.test(original) ? 'contain' : 'cover';
        viewport.content = fit.test(original)
            ? original.replace(fit, `viewport-fit=${temporary}`)
            : `${original}, viewport-fit=${temporary}`;
        requestAnimationFrame(() => {
            viewport.content = original;
            requestAnimationFrame(() => {
                window.dispatchEvent(new Event('resize'));
                restoring = false;
            });
        });
    }

    function scheduleRecovery() {
        if (!beforeLink || restoring || attempts >= 3) return;
        cancelTimers();
        // The sheet dismissal animation can finish after focus/visibility returns.
        timers = [0, 250, 750].map(delay => setTimeout(recover, delay));
    }
    for (const event of ['focus', 'pageshow', 'resize']) window.addEventListener(event, scheduleRecovery);
    document.addEventListener('visibilitychange', scheduleRecovery);
    window.visualViewport?.addEventListener('resize', scheduleRecovery);
    // Some iOS sheet transitions emit no lifecycle event at all.
    document.addEventListener('pointerdown', scheduleRecovery, { passive: true });
})();
