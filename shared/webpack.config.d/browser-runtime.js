// Okio contains Node-only branches alongside its browser implementation.
// Do not resolve their built-ins when bundling the browser test runner.
config.resolve.fallback = { ...config.resolve.fallback, os: false, path: false };
