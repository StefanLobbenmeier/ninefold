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
const scope = 'https://example.test/sudokupad/';

function release(version = 'old') {
    const directory = mkdtempSync(`${tmpdir()}/ninefold-offline-`);
    const files = {
        'index.html': `HTML ${version}`, 'styles.css': 'styles', 'offline.js': 'registration',
        'webApp.js': `loader ${version}`, 'chunks/runtime.mjs': `runtime ${version}`,
        'chunks/app.wasm': `wasm ${version}`, 'fonts/fallback.ttf': 'font',
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
        return { source, manifest, files, cacheName: `ninefold-app-${manifest.version}` };
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

function worker(release, caches, network) {
    const handlers = {};
    const requests = [];
    vm.runInNewContext(release.source, {
        URL, Response, Uint8Array, crypto: webcrypto, caches,
        fetch: async (...args) => { requests.push(args); return network(...args); },
        // Calling skipWaiting/claim would throw, protecting existing tabs from mixed versions.
        self: { registration: { scope }, addEventListener: (name, handler) => { handlers[name] = handler; } },
    });
    return {
        requests,
        async dispatch(name, request) {
            let result;
            handlers[name]({ request, waitUntil: promise => { result = promise; }, respondWith: promise => { result = promise; } });
            return await result;
        },
    };
}
const networkFor = release => async (url) => {
    const path = new URL(url).pathname.slice('/sudokupad/'.length);
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

test('a missing cached shell does not fall through to an incompatible online version', async () => {
    const offline = worker(old, storage(), () => { throw new Error('Should not fetch'); });
    const response = await offline.dispatch('fetch', { method: 'GET', mode: 'navigate', url: scope });
    assert.equal(response.status, 503);
    assert.match(await response.text(), /reconnect/);
    assert.equal(offline.requests.length, 0);
});
