import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const resource = name => readFileSync(new URL(`../webApp/src/webMain/resources/${name}`, import.meta.url), 'utf8');
function page(standalone = true) {
    const window = new EventTarget();
    const document = new EventTarget();
    const writes = [];
    let content = 'width=device-width, initial-scale=1.0';
    const viewport = { get content() { return content; }, set content(value) { content = value; writes.push(value); } };
    Object.assign(window, { innerWidth: 390, innerHeight: 800, screen: { orientation: { angle: 0 } },
        visualViewport: Object.assign(new EventTarget(), { scale: 1 }), location: new URL('https://example.test/') });
    Object.assign(document, { visibilityState: 'visible', querySelector: () => viewport, activeElement: null });
    let id = 0;
    const timers = new Map();
    const frames = [];
    const context = vm.createContext({ window, document, navigator: { standalone }, Event, URL,
        setTimeout: callback => { timers.set(++id, callback); return id; },
        clearTimeout: id => timers.delete(id), requestAnimationFrame: callback => frames.push(callback) });
    vm.runInContext(resource('viewport-recovery.js'), context);
    return { window, document, writes, viewport, context,
        prepare: () => window.ninefoldPrepareExternalViewport?.(),
        event(name) { (['pointerdown', 'visibilitychange'].includes(name) ? document : window).dispatchEvent(new Event(name)); },
        flush() {
            for (const [id, callback] of [...timers]) {
                if (!timers.delete(id)) continue;
                callback();
                while (frames.length) frames.shift()();
            }
        },
    };
}

for (const event of ['focus', 'pageshow', 'visibilitychange', 'resize', 'pointerdown']) {
    test(`short viewport recovers on ${event}, with bounded retries and exact policy restoration`, () => {
        const p = page();
        const original = p.viewport.content;
        p.prepare();
        p.window.innerHeight = 750;
        for (let i = 0; i < 5; i++) { p.event(event); p.flush(); }
        assert.equal(p.writes.length, 6);
        assert.match(p.writes[0], /viewport-fit=cover/);
        assert.equal(p.viewport.content, original);
    });
}

test('healthy geometry and events before an outbound link do nothing', () => {
    const p = page();
    p.event('focus'); p.flush();
    p.prepare(); p.event('focus'); p.flush();
    assert.equal(p.writes.length, 0);
});

test('successful recovery stops retries; a later link can recover again', () => {
    const p = page();
    p.window.addEventListener('resize', () => { p.window.innerHeight = 800; });
    for (let i = 0; i < 2; i++) {
        p.prepare(); p.window.innerHeight = 750; p.event('focus'); p.flush();
    }
    assert.equal(p.writes.length, 4);
});

for (const condition of ['hidden', 'editing', 'zoom']) {
    test(`${condition} defers recovery until the condition clears`, () => {
        const p = page(); p.prepare(); p.window.innerHeight = 750;
        if (condition === 'hidden') p.document.visibilityState = 'hidden';
        if (condition === 'editing') p.document.activeElement = { matches: () => true };
        if (condition === 'zoom') p.window.visualViewport.scale = 2;
        p.event('focus'); p.flush(); assert.equal(p.writes.length, 0);
        p.document.visibilityState = 'visible'; p.document.activeElement = null; p.window.visualViewport.scale = 1;
        p.event('focus'); p.flush(); assert.equal(p.writes.length, 6);
    });
}

for (const change of ['rotation', 'width']) {
    test(`${change} invalidates stale geometry`, () => {
        const p = page(); p.prepare(); p.window.innerHeight = 750;
        if (change === 'rotation') p.window.screen.orientation.angle = 90;
        else p.window.innerWidth = 600;
        p.event('resize'); p.flush();
        p.window.screen.orientation.angle = 0; p.window.innerWidth = 390;
        p.event('focus'); p.flush(); assert.equal(p.writes.length, 0);
    });
}

test('regular browser tabs are untouched', () => {
    const p = page(false);
    assert.equal(p.window.ninefoldPrepareExternalViewport, undefined);
    p.window.innerHeight = 750; p.event('focus'); p.flush();
    assert.equal(p.writes.length, 0);
});

test('external link captures geometry synchronously and keeps the original HTTPS URL', () => {
    const p = page();
    let clicked = false;
    const link = { click() { clicked = true; p.window.innerHeight = 750; }, remove() {} };
    p.document.createElement = () => link;
    p.document.body = { appendChild() {} };
    vm.runInContext(resource('external-links.js'), p.context);
    p.window.ninefoldOpenExternalLink('https://sudokupad.app/synthetic');
    assert.equal(clicked, true);
    assert.equal(link.href, 'https://sudokupad.app/synthetic');
    assert.equal(link.target, '_self');
    p.event('focus'); p.flush(); assert.equal(p.writes.length, 6);
});
