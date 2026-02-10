// Node/jsdom: polyfill TextEncoder for react-router and other deps
const { TextEncoder, TextDecoder } = require('util');
global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;

// jsdom does not provide fetch; polyfill so modules that call fetch at load time do not throw
global.fetch = global.fetch || (() =>
  Promise.resolve({ ok: true, json: () => Promise.resolve([]), text: () => Promise.resolve('') }));

// Extend Jest matchers (e.g. toBeInTheDocument)
require('@testing-library/jest-dom');
