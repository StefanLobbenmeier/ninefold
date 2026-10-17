import { test } from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const template = readFileSync(new URL('../webApp/src/webMain/resources/sw.js', import.meta.url), 'utf8');
const generator = fileURLToPath(new URL('./prepare-offline-release.py', import.meta.url));
const scope = 'https://example.test/ninefold/';

const cacheName = (release, workerScope = scope) => `ninefold-app-${encodeURIComponent(workerScope)}:${release.manifest.version}`;

function release(version = 'old') {
    const directory = mkdtempSync(`${tmpdir()}/ninefold-offline-`);
    const files = {
        'index.html': `HTML ${version}`, 'styles.css': 'styles', 'offline.js': 'registration',
        'webApp.js': `loader ${version}`, 'chunks/runtime.mjs': `runtime ${version}`,
        'chunks/app.wasm': `wasm ${version}`, 'fonts/fallback.ttf': 'font',
        'composeResources/ninefold.shared.generated.resources/font/lora.ttf': 'synthetic editorial font',
    };
    try {
        for (const [path, contents] of Object.entries(files)) {
            mkdirSync(`${directory}/${path.split('/').slice(0, -1).join('/')}`, { recursive: true });
            writeFileSync(`${directory}/${path}`, contents);
        }
        writeFileSync(`${directory}/sw.js`, template);
        writeFileSync(`${directory}/webApp.js.map`, 'source map');
        execFileSync('python3', [generator, directory]);
        const source = readFileSync(`${directory}/sw.js`, 'utf8');
        const manifest = JSON.parse(source.match(/const RELEASE = (.*);/)[1]);
        return { source, manifest, files, cacheName: `ninefold-app-${encodeURIComponent(scope)}:${manifest.version}` };
    } finally {
        rmSync(directory, { recursive: true, force: true });
    }
}

function storage() {
    const entries = new Map();
    return {
        entries,
        async open(name) {
            if (!entries.has(name)) entries.set(name, new Map());
            const cache = entries.get(name);
            const key = request => typeof request === 'string' ? request : request.url;
            return {
                async match(request) { return cache.get(key(request))?.clone(); },
                async put(request, response) { cache.set(key(request), response.clone()); },
            };
        },
        async delete(name) { return entries.delete(name); },
        async keys() { return [...entries.keys()]; },
    };
}

function worker(release, caches, network, workerScope = scope) {
    const handlers = {};
    const requests = [];
    let activations = 0;
    vm.runInNewContext(release.source, {
        URL, Response, Uint8Array, crypto: webcrypto, caches,
        fetch: async (...args) => { requests.push(args); return network(...args); },
        // Activation must only be requested by an explicit update message.
        self: {
            registration: { scope: workerScope }, addEventListener: (name, handler) => { handlers[name] = handler; },
            skipWaiting: async () => { activations++; },
        },
    });
    return {
        requests,
        get activations() { return activations; },
        async dispatch(name, request) {
            let result;
            handlers[name]({ request, data: name === 'message' ? request : undefined, waitUntil: promise => { result = promise; }, respondWith: promise => { result = promise; } });
            return await result;
        },
    };
}
const networkFor = (release, workerScope = scope) => async (url) => {
    const path = new URL(url).pathname.slice(new URL(workerScope).pathname.length);
    return new Response(release.files[path], { status: path in release.files ? 200 : 404 });
};
const old = release();
const next = release('new');

test('production manifest covers nested runtime assets and changes with the release', () => {
    assert.deepEqual(old.manifest.assets.map(asset => asset.path).sort(), Object.keys(old.files).sort());
    assert.notEqual(old.manifest.version, next.manifest.version);
});

test('startup and every runtime asset use cache without waiting for the network', async () => {
    const caches = storage();
    const installer = worker(old, caches, networkFor(old));
    await installer.dispatch('install');
    assert.equal(caches.entries.get(old.cacheName).size, Object.keys(old.files).length);
    const offline = worker(old, caches, () => { throw new Error('No internet'); });
    for (const [path, contents] of Object.entries(old.files)) {
        const response = await offline.dispatch('fetch', { method: 'GET', mode: 'cors', url: scope + path });
        assert.equal(await response.text(), contents);
    }
    const page = await offline.dispatch('fetch', { method: 'GET', mode: 'navigate', url: scope + '?puzzle=123' });
    assert.equal(await page.text(), old.files['index.html']);
    assert.equal(offline.requests.length, 0);
});

for (const failure of ['interrupted', 'http error', 'wrong release', 'storage full']) {
    test(`${failure} during update preserves the old complete release`, async () => {
        const caches = storage();
        await worker(old, caches, networkFor(old)).dispatch('install');
        let downloaded = 0;
        if (failure === 'storage full') {
            const open = caches.open.bind(caches);
            caches.open = async name => {
                const cache = await open(name);
                if (name === next.cacheName) cache.put = async () => { throw new Error('Quota exceeded'); };
                return cache;
            };
        }
        const updater = worker(next, caches, async url => {
            if (++downloaded === 3) {
                if (failure === 'interrupted') throw new Error('Network disconnected');
                if (failure === 'http error') return new Response('Unavailable', { status: 503 });
                if (failure === 'wrong release') return new Response('File from a different deployment');
            }
            return networkFor(next)(url);
        });
        await assert.rejects(updater.dispatch('install'));
        assert.equal(caches.entries.has(next.cacheName), false);
        assert.equal(caches.entries.get(old.cacheName).size, Object.keys(old.files).length);
    });
}

test('complete update stays separate until activation and preserves puzzle/font data', async () => {
    const caches = storage();
    await worker(old, caches, networkFor(old)).dispatch('install');
    const data = await caches.open('ninefold-offline-data-v1');
    await data.put('https://fonts.gstatic.com/font.ttf', new Response('saved font'));
    const updater = worker(next, caches, networkFor(next));
    await updater.dispatch('install');
    assert.equal(caches.entries.has(old.cacheName), true);
    const oldPage = await worker(old, caches, networkFor(next)).dispatch('fetch', { method: 'GET', mode: 'navigate', url: scope });
    assert.equal(await oldPage.text(), old.files['index.html']);
    await updater.dispatch('activate');
    assert.equal(caches.entries.has(old.cacheName), false);
    assert.equal(caches.entries.has('ninefold-offline-data-v1'), true);
    const newPage = await updater.dispatch('fetch', { method: 'GET', mode: 'navigate', url: scope });
    assert.equal(await newPage.text(), next.files['index.html']);
});

for (const previewRelease of [old, next]) {
    test(`deployment scopes retain independent caches with ${previewRelease === old ? 'identical' : 'different'} releases`, async () => {
        const caches = storage();
        const previewScope = scope + 'pr-preview/pr-123/';
        const main = worker(old, caches, networkFor(old));
        const preview = worker(previewRelease, caches, networkFor(previewRelease, previewScope), previewScope);
        await main.dispatch('install');
        await main.dispatch('activate');
        await preview.dispatch('install');
        await preview.dispatch('activate');
        assert.equal(caches.entries.has(old.cacheName), true);
        assert.equal(caches.entries.has(cacheName(previewRelease, previewScope)), true);

        const update = worker(next, caches, networkFor(next));
        await update.dispatch('install');
        await update.dispatch('activate');
        assert.equal(caches.entries.has(old.cacheName), false);
        assert.equal(caches.entries.has(cacheName(previewRelease, previewScope)), true);
        for (const [release, workerScope] of [[next, scope], [previewRelease, previewScope]]) {
            const offline = worker(release, caches, () => { throw new Error('No internet'); }, workerScope);
            for (const [path, contents] of Object.entries(release.files)) {
                const response = await offline.dispatch('fetch', { method: 'GET', mode: 'cors', url: workerScope + path });
                assert.equal(await response.text(), contents);
            }
            const page = await offline.dispatch('fetch', { method: 'GET', mode: 'navigate', url: workerScope });
            assert.equal(await page.text(), release.files['index.html']);
            assert.equal(offline.requests.length, 0);
        }
    });
}

test('failed installation in another scope preserves an identical installed release', async () => {
    const caches = storage();
    await worker(old, caches, networkFor(old)).dispatch('install');
    const previewScope = scope + 'pr-preview/pr-123/';
    const preview = worker(old, caches, async () => new Response('Unavailable', { status: 503 }), previewScope);
    await assert.rejects(preview.dispatch('install'));
    assert.equal(caches.entries.has(cacheName(old, previewScope)), false);
    const page = await worker(old, caches, networkFor(old)).dispatch('fetch', { method: 'GET', mode: 'navigate', url: scope });
    assert.equal(await page.text(), old.files['index.html']);
});

test('a missing cached shell does not fall through to an incompatible online version', async () => {
    const offline = worker(old, storage(), () => { throw new Error('Should not fetch'); });
    const response = await offline.dispatch('fetch', { method: 'GET', mode: 'navigate', url: scope });
    assert.equal(response.status, 503);
    assert.match(await response.text(), /reconnect/);
    assert.equal(offline.requests.length, 0);
});


test('explicit puzzle reimports bypass saved responses and leave the offline cache intact', async () => {
    const caches = storage();
    const data = await caches.open('ninefold-offline-data-v1');
    const url = 'https://sudokupad.svencodes.com/ctclegacy/test';
    await data.put(url, new Response('old puzzle'));
    const active = worker(old, caches, () => { throw new Error('Should pass through to browser fetch'); });
    assert.equal(await active.dispatch('fetch', { method: 'GET', mode: 'cors', url: url + '?reimport=123' }), undefined);
    const saved = await active.dispatch('fetch', { method: 'GET', mode: 'cors', url });
    assert.equal(await saved.text(), 'old puzzle');
    assert.equal(caches.entries.get('ninefold-offline-data-v1').size, 1);
});

test('only explicit user acceptance asks a fully installed worker to activate', async () => {
    const updater = worker(next, storage(), networkFor(next));
    await updater.dispatch('install');
    assert.equal(updater.activations, 0);
    await updater.dispatch('message', { type: 'unrelated' });
    assert.equal(updater.activations, 0);
    await updater.dispatch('message', { type: 'activate-update' });
    assert.equal(updater.activations, 1);
});

const registrationSource = readFileSync(new URL('../webApp/src/webMain/resources/offline.js', import.meta.url), 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));

async function browserUpdates({ waiting = null, controlled = true, supported = true } = {}) {
    const window = new EventTarget();
    const document = new EventTarget();
    document.visibilityState = 'visible';
    let reloads = 0;
    window.location = { reload: () => reloads++ };
    const registration = new EventTarget();
    registration.waiting = waiting;
    registration.installing = null;
    let checks = 0;
    let offline = false;
    registration.update = async () => { checks++; if (offline) throw new Error('Offline'); };
    const serviceWorker = new EventTarget();
    serviceWorker.controller = controlled ? {} : null;
    let registrationArgs;
    serviceWorker.register = async (...args) => { registrationArgs = args; return registration; };
    serviceWorker.ready = Promise.resolve(registration);
    const intervals = [];
    const timeouts = new Map();
    vm.runInNewContext(registrationSource, {
        window, document, navigator: supported ? { serviceWorker } : {}, URL,
        performance: { getEntriesByType: () => [] },
        PerformanceObserver: class { observe() {} }, console: { warn() {} },
        setInterval: (callback, delay) => intervals.push({ callback, delay }),
        setTimeout: callback => { timeouts.set(callback, callback); return callback; },
        clearTimeout: id => timeouts.delete(id),
    });
    await settle();
    const states = [];
    const unsubscribe = window.ninefoldUpdate.subscribe(value => states.push(value));
    return {
        window, document, registration, serviceWorker, intervals, timeouts, states, unsubscribe,
        get checks() { return checks; }, get reloads() { return reloads; },
        get registrationArgs() { return registrationArgs; },
        offline: value => { offline = value; },
    };
}

test('checks on launch, every five minutes, and visible app resumes; offline checks recover', async () => {
    const page = await browserUpdates();
    assert.equal(page.registrationArgs[0], 'sw.js');
    assert.equal(page.registrationArgs[1].updateViaCache, 'none');
    assert.equal(page.checks, 1);
    assert.equal(page.intervals.length, 1);
    assert.equal(page.intervals[0].delay, 300000);
    await page.intervals[0].callback();
    assert.equal(page.checks, 2);
    page.offline(true);
    await page.intervals[0].callback();
    page.offline(false);
    await page.intervals[0].callback();
    assert.equal(page.checks, 4);
    for (const event of ['visibilitychange', 'pageshow', 'focus']) {
        const target = event === 'visibilitychange' ? page.document : page.window;
        page.document.visibilityState = 'hidden';
        target.dispatchEvent(new Event(event));
        await settle();
        const before = page.checks;
        page.document.visibilityState = 'visible';
        target.dispatchEvent(new Event(event));
        await settle();
        assert.equal(page.checks, before + 1);
    }
    assert.deepEqual([...new Set(page.states)], ['None']);
    assert.equal(page.reloads, 0);
});

test('prompts only after a complete update, accepts explicitly, and reloads once on takeover', async () => {
    const page = await browserUpdates();
    const messages = [];
    const installing = new EventTarget();
    installing.postMessage = message => messages.push(message.type);
    page.registration.installing = installing;
    page.registration.dispatchEvent(new Event('updatefound'));
    installing.state = 'installing';
    installing.dispatchEvent(new Event('statechange'));
    assert.equal(page.states.at(-1), 'None');
    // A failed/interrupted install never becomes a waiting worker.
    installing.state = 'redundant';
    installing.dispatchEvent(new Event('statechange'));
    assert.equal(page.states.at(-1), 'None');
    page.registration.waiting = installing;
    installing.state = 'installed';
    installing.dispatchEvent(new Event('statechange'));
    assert.equal(page.states.at(-1), 'Ready');
    assert.deepEqual(messages, []);
    page.window.ninefoldUpdate.apply();
    page.window.ninefoldUpdate.apply();
    assert.deepEqual(messages, ['activate-update']);
    assert.equal(page.states.at(-1), 'Applying');
    assert.equal(page.reloads, 0);
    page.serviceWorker.dispatchEvent(new Event('controllerchange'));
    page.serviceWorker.dispatchEvent(new Event('controllerchange'));
    assert.equal(page.reloads, 1);
    assert.equal(page.timeouts.size, 0);
});

test('an update already waiting at launch is available to late subscribers and can retry activation', async () => {
    const messages = [];
    const page = await browserUpdates({ waiting: { postMessage: message => messages.push(message.type) } });
    assert.deepEqual(page.states, ['Ready']);
    page.window.ninefoldUpdate.apply();
    for (const callback of page.timeouts.values()) callback();
    assert.equal(page.states.at(-1), 'Failed');
    page.window.ninefoldUpdate.apply();
    assert.equal(page.states.at(-1), 'Applying');
    assert.deepEqual(messages, ['activate-update', 'activate-update']);
    page.unsubscribe();
    for (const callback of page.timeouts.values()) callback();
    assert.equal(page.states.at(-1), 'Applying');
});

test('initial installation and unsupported browsers do not prompt or reload', async () => {
    const page = await browserUpdates({ controlled: false });
    page.serviceWorker.controller = {};
    page.serviceWorker.dispatchEvent(new Event('controllerchange'));
    assert.equal(page.reloads, 0);
    assert.equal(page.states.at(-1), 'None');
    const unsupported = await browserUpdates({ supported: false });
    assert.deepEqual(unsupported.states, ['None']);
    assert.equal(unsupported.intervals.length, 0);
});
