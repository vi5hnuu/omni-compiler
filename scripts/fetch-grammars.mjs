// Downloads TextMate grammars (from the `tm-grammars` npm package, the set used by Shiki) into
// core/editor assets and writes the Sora `languages.json` index plus language configurations.
//
// Only permissively licensed grammars (MIT / BSD / Apache-2.0) are bundled; languages whose grammar is
// GPL or unlicensed open as plain text. Re-run after bumping VERSION:  node scripts/fetch-grammars.mjs
import { mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const VERSION = '1.32.22';
const CDN = `https://cdn.jsdelivr.net/npm/tm-grammars@${VERSION}`;
const ALLOWED_LICENSES = new Set(['MIT', 'BSD-3-Clause', 'BSD-2-Clause', 'Apache-2.0']);

const here = dirname(fileURLToPath(import.meta.url));
const assets = resolve(here, '..', 'core', 'editor', 'src', 'main', 'assets', 'textmate');

// grammar name -> comment style + whether a single quote delimits strings (it doesn't in Lisps, Rust lifetimes, OCaml, …)
const GRAMMARS = {
  awk: ['hash', false], shellscript: ['hash', true], fish: ['hash', true], c: ['slash', false], clojure: ['semicolon', false],
  cobol: ['none', false], coffee: ['hash', true], cpp: ['slash', false], crystal: ['hash', false], csharp: ['slash', false],
  d: ['slash', false], dart: ['slash', true], erlang: ['percent', false], 'fortran-free-form': ['bang', false],
  fsharp: ['slash', false], go: ['slash', false], groovy: ['slash', true], scheme: ['semicolon', false],
  haskell: ['dash', false], java: ['slash', false], javascript: ['slash', true], julia: ['hash', false],
  kotlin: ['slash', false], 'common-lisp': ['semicolon', false], lua: ['dash', true], asm: ['semicolon', false],
  ocaml: ['ml', false], pascal: ['slash', true], perl: ['hash', true], php: ['slash', true],
  powershell: ['hash', true], prolog: ['percent', true], python: ['hash', true], r: ['hash', true], raku: ['hash', true],
  ruby: ['hash', true], rust: ['slash', false], scala: ['slash', false], smalltalk: ['none', false], sql: ['dash', true],
  swift: ['slash', false], typescript: ['slash', true], v: ['slash', true], wasm: ['wasm', false], zig: ['slash', false],
  regexp: ['none', false],
};

const COMMENTS = {
  slash: { lineComment: '//', blockComment: ['/*', '*/'] },
  hash: { lineComment: '#' },
  dash: { lineComment: '--' },
  semicolon: { lineComment: ';' },
  percent: { lineComment: '%' },
  bang: { lineComment: '!' },
  ml: { blockComment: ['(*', '*)'] },
  wasm: { lineComment: ';;', blockComment: ['(;', ';)'] },
  none: {},
};

function languageConfiguration(style, singleQuoteStrings) {
  const pairs = [['{', '}'], ['[', ']'], ['(', ')']];
  const autoClosing = [...pairs.map(([open, close]) => ({ open, close })), { open: '"', close: '"', notIn: ['string'] }];
  if (singleQuoteStrings) autoClosing.push({ open: "'", close: "'", notIn: ['string', 'comment'] });
  return {
    comments: COMMENTS[style],
    brackets: pairs,
    autoClosingPairs: autoClosing,
    surroundingPairs: [...pairs, ['"', '"'], ...(singleQuoteStrings ? [["'", "'"]] : [])],
  };
}

const { grammars } = await import(`${CDN}/index.js`).catch(async () => {
  const text = await (await fetch(`${CDN}/index.js`)).text();
  return import(`data:text/javascript;base64,${Buffer.from(text).toString('base64')}`);
});
const meta = Object.fromEntries(grammars.map((g) => [g.name, g]));

await rm(assets, { recursive: true, force: true });
await mkdir(join(assets, 'grammars'), { recursive: true });
await mkdir(join(assets, 'config'), { recursive: true });

const languages = [];
const notices = [];
for (const [name, [style, singleQuote]] of Object.entries(GRAMMARS)) {
  const info = meta[name];
  if (!info) { console.warn(`skip ${name}: not in tm-grammars`); continue; }
  if (!ALLOWED_LICENSES.has(info.license)) { console.warn(`skip ${name}: license ${info.license}`); continue; }
  const res = await fetch(`${CDN}/grammars/${name}.json`);
  if (!res.ok) { console.warn(`skip ${name}: HTTP ${res.status}`); continue; }
  await writeFile(join(assets, 'grammars', `${name}.json`), await res.text());
  const configName = `${style}${singleQuote ? '-sq' : ''}.json`;
  await writeFile(join(assets, 'config', configName), JSON.stringify(languageConfiguration(style, singleQuote)));
  languages.push({
    grammar: `textmate/grammars/${name}.json`,
    name,
    scopeName: info.scopeName,
    languageConfiguration: `textmate/config/${configName}`,
  });
  notices.push(`${name}\t${info.license}\t${info.source}`);
}

// Grammars are loaded lazily per language, so each entry lists the other bundled grammars it includes
// (heredocs, embedded SQL/JS, …) for the app to load first. Sora's own reader ignores this key.
const bundledScopes = new Set(languages.map((l) => l.scopeName));
for (const language of languages) {
  const raw = await readFile(join(assets, 'grammars', `${language.name}.json`), 'utf8');
  const refs = [...raw.matchAll(/"include"\s*:\s*"((?:source|text)\.[^"#]+)/g)].map((m) => m[1]);
  const dependencies = [...new Set(refs)].filter((scope) => scope !== language.scopeName && bundledScopes.has(scope)).sort();
  if (dependencies.length) language.dependencies = dependencies;
}

await writeFile(join(assets, 'languages.json'), JSON.stringify({ languages }, null, 1) + '\n');
await writeFile(join(assets, 'NOTICE.txt'), `TextMate grammars from tm-grammars@${VERSION}\n\n${notices.join('\n')}\n`);
console.log(`bundled ${languages.length} grammars`);
