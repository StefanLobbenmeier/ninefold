import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

// Exercise the actual browser callbacks in both Kotlin interop implementations.
for (const target of ['js', 'wasmJs']) {
    const source = readFileSync(new URL(
        `../shared/src/${target}Main/kotlin/de/ninefold/PageVisibility.${target}.kt`, import.meta.url,
    ), 'utf8').split('"""')[1];

    function observe(initialVisibility = 'visible') {
        const document = new EventTarget();
        const window = new EventTarget();
        document.visibilityState = initialVisibility;
        const changes = [];
        const onChange = visible => changes.push(visible);
        const context = { document, window, onChange };
        const unsubscribe = vm.runInNewContext(
            target === 'js' ? source : `(${source})(onChange)`, context,
        );
        return {
            changes, unsubscribe,
            visibility(state) { document.visibilityState = state; },
            dispatch(name) {
                (name === 'visibilitychange' ? document : window).dispatchEvent(new Event(name));
            },
        };
    }

    test(`${target}: reports initial visibility and normal hide/resume`, () => {
        const page = observe('hidden');
        assert.deepEqual(page.changes, [false]);
        page.visibility('visible');
        page.dispatch('visibilitychange');
        page.visibility('hidden');
        page.dispatch('visibilitychange');
        page.visibility('visible');
        page.dispatch('visibilitychange');
        assert.deepEqual(page.changes, [false, true, false, true]);
    });

    for (const event of ['pageshow', 'focus']) {
        test(`${target}: ${event} resumes after a missing visibilitychange`, () => {
            const page = observe();
            page.visibility('hidden');
            page.dispatch('visibilitychange');
            page.visibility('visible');
            page.dispatch(event);
            assert.deepEqual(page.changes, [true, false, true]);
        });
    }

    test(`${target}: pagehide pauses even before visibility changes`, () => {
        const page = observe();
        page.dispatch('pagehide');
        page.dispatch('pageshow');
        assert.deepEqual(page.changes, [true, false, true]);
    });

    test(`${target}: hidden pages cannot resume and blur does not pause`, () => {
        const page = observe();
        page.dispatch('blur');
        assert.deepEqual(page.changes, [true]);
        page.visibility('hidden');
        page.dispatch('visibilitychange');
        page.dispatch('pageshow');
        page.dispatch('focus');
        assert.deepEqual(page.changes, [true, false]);
    });

    test(`${target}: disposal removes every lifecycle listener`, () => {
        const page = observe();
        page.unsubscribe();
        for (const event of ['visibilitychange', 'pagehide', 'pageshow', 'focus']) page.dispatch(event);
        assert.deepEqual(page.changes, [true]);
    });
}
