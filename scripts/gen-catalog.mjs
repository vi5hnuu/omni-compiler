// Generates core/catalog/src/main/assets/catalog.json from the ls-judge web repo.
//
// ls-judge serves only live runtime facts (GET /runtimes). Display names, starter code, practice
// problems and per-runtime limit defaults live as TypeScript in ls-judge/web/lib; this script
// imports them directly (Node >= 22.18 strips TS types natively) so the app ships the same data
// as the web playground.
//
// Usage:  node scripts/gen-catalog.mjs [path/to/ls-judge]     (default: ../../WebstormProjects/ls-judge)
import { readdir, readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const appRoot = resolve(here, '..');
const lsJudge = resolve(process.argv[2] ?? join(appRoot, '..', '..', 'WebstormProjects', 'ls-judge'));
const webLib = join(lsJudge, 'web', 'lib');
const outFile = join(appRoot, 'core', 'catalog', 'src', 'main', 'assets', 'catalog.json');

const { RUNTIME_CATALOG, RUNTIME_LIMITS, DEFAULT_TIME_MS, DEFAULT_MEM_MB } = await import(join(webLib, 'runtimeCatalog.ts'));
const { SAMPLES } = await import(join(webLib, 'samples.ts'));
const { EXAMPLES } = await import(join(webLib, 'examples.ts'));
const { PROBLEM_CATALOG } = await import(join(webLib, 'problemCatalog.ts'));

// ── Copied from ls-judge/web/app/play/page.tsx (not exported there). Keep in sync. ───────────────
const LINE_COMMENT = {
  ada: '--', haskell: '--', lua: '--', sqlite: '--',
  clojure: ';', lisp: ';', racket: ';', guile: ';', nasm: ';',
  erlang: '%', octave: '%', prolog: '%',
  fortran: '!', forth: '\\', cobol: '*>', wasm: ';;',
  awk: '#', bash: '#', bc: '#', bqn: '#', coffeescript: '#', crystal: '#', dc: '#',
  elixir: '#', fish: '#', jq: '#', julia: '#', nim: '#', perl: '#', powershell: '#',
  pypy: '#', python: '#', r: '#', raku: '#', ruby: '#', tcl: '#', zsh: '#',
  c: '//', cpp: '//', csharp: '//', d: '//', dart: '//', fsharp: '//', go: '//',
  groovy: '//', java: '//', javascript: '//', kotlin: '//', pascal: '//', php: '//',
  rust: '//', scala: '//', swift: '//', typescript: '//', vlang: '//', zig: '//',
  deno: '//', bun: '//',
};
const BLOCK_COMMENT = { ocaml: ['(*', '*)'], smalltalk: ['"', '"'] };
const IMPORT_HINT = {
  python: 'import helper  — from helper.py (same dir)',
  pypy: 'import helper  — from helper.py',
  javascript: "require('./helper')  or  import './helper.js'",
  typescript: "import { x } from './helper'",
  ruby: "require_relative 'helper'",
  php: "require 'helper.php';",
  perl: "require 'helper.pl';",
  lua: "require('helper')",
  java: 'default package — use class Helper directly (all *.java compiled)',
  kotlin: 'same package — call top-level declarations (all *.kt compiled)',
  scala: 'same package — reference directly (all *.scala compiled)',
  groovy: 'same dir — reference class Helper directly',
  cpp: '#include "helper.h"  (all *.cpp linked)',
  c: '#include "helper.h"  (all *.c linked)',
  go: 'same package main — call funcs directly (all *.go built)',
  rust: 'mod helper;  then helper::fn()',
  haskell: 'import Helper  — module Helper in Helper.hs',
  swift: 'same module — reference directly (all *.swift compiled)',
  d: 'import helper;  (all *.d linked)',
  csharp: 'same namespace — reference class directly',
  dart: "import 'helper.dart';",
  nim: 'import helper',
  crystal: 'require "./helper"',
  ada: 'with Helper;  use Helper;',
  pascal: 'uses helper;',
  ocaml: 'reference Helper.fn (compiled together)',
};
const DEFAULT_IMPORT_HINT = 'all files live together in /workspace (flat, import by bare name)';
const MULTIFILE_UNSUPPORTED = new Set([
  'ocaml', 'fortran', 'wasm', 'nasm', 'prolog', 'zig', 'forth', 'bqn',
  'brainfuck', 'dc', 'bc', 'jq', 'smalltalk', 'sqlite', 'cobol', 'vlang',
]);

// ── App-only presentation data ───────────────────────────────────────────────────────────────────
const CATEGORY = {
  COMPILED: ['ada', 'c', 'cpp', 'crystal', 'csharp', 'd', 'dart', 'fortran', 'go', 'nasm', 'nim', 'pascal', 'rust', 'swift', 'vlang', 'wasm', 'zig', 'cobol'],
  JVM: ['java', 'kotlin', 'scala', 'groovy', 'clojure'],
  FUNCTIONAL: ['haskell', 'ocaml', 'fsharp', 'elixir', 'erlang', 'lisp', 'racket', 'guile', 'prolog'],
  ESOTERIC: ['brainfuck', 'bqn', 'forth'],
};
const SHORT_CODE = {
  cpp: 'C+', csharp: 'C#', fsharp: 'F#', python: 'Py', pypy: 'PP', java: 'Jv', javascript: 'Js', typescript: 'Ts',
  kotlin: 'Kt', haskell: 'Hs', sqlite: 'Sq', rust: 'Rs', ruby: 'Rb', go: 'Go', c: 'C', d: 'D', r: 'R',
  brainfuck: 'Bf', coffeescript: 'Cs', powershell: 'Ps', nasm: 'As', ocaml: 'Ml', erlang: 'Er', elixir: 'Ex',
  clojure: 'Cj', crystal: 'Cr', julia: 'Jl', octave: 'Oc', prolog: 'Pl', perl: 'Pe', php: 'Ph', scala: 'Sc',
  swift: 'Sw', smalltalk: 'St', fortran: 'Fo', forth: 'Fr', lisp: 'Li', lua: 'Lu', guile: 'Gu', groovy: 'Gr',
  racket: 'Rk', raku: 'Ra', vlang: 'V', wasm: 'Wa', zsh: 'Zs', zig: 'Zg', deno: 'Dn', bun: 'Bn',
};

function categoryOf(base) {
  for (const [category, bases] of Object.entries(CATEGORY)) if (bases.includes(base)) return category;
  return 'SCRIPTING';
}
function shortCodeOf(base, name) {
  if (SHORT_CODE[base]) return SHORT_CODE[base];
  const letters = name.replace(/[^A-Za-z]/g, '');
  return (letters.charAt(0).toUpperCase() + letters.charAt(1).toLowerCase()) || base.slice(0, 2);
}
const capitalize = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const normalizeTests = (tests) => (tests ?? []).map((t) => ({ stdin: t.stdin ?? '', expected: t.expected ?? '' }));

// Every family that has a runtime definition, plus anything the web catalog advertises.
const runtimesRoot = join(lsJudge, 'deploy', 'firecracker', 'runtimes');
const runtimeEntries = (await readdir(runtimesRoot, { withFileTypes: true }))
  .filter((e) => e.isDirectory() && !e.name.startsWith('_'));
const runtimeDirs = runtimeEntries.map((e) => e.name.split('-')[0]);

// Offline snapshot of runtimes (id, entry filename, status) so projects can be created before the live
// GET /runtimes has ever succeeded. Hot-lane families mirror ls-judge internal/models HotLanguages.
const HOT_LANGUAGES = new Set(['python', 'cpp', 'java', 'javascript', 'go', 'sqlite']);
const runtimes = [];
for (const entry of runtimeEntries) {
  let tests;
  try {
    tests = JSON.parse(await readFile(join(runtimesRoot, entry.name, 'tests.json'), 'utf8'));
  } catch {
    continue; // not a buildable runtime definition
  }
  let status = 'ready';
  try {
    const meta = await readFile(join(runtimesRoot, entry.name, 'metadata.yml'), 'utf8');
    status = /^status:\s*(\w+)/m.exec(meta)?.[1] ?? 'ready';
  } catch {
    // no metadata.yml: ready by convention
  }
  if (status === 'removed') continue;
  const id = tests.runtime_id ?? entry.name;
  const dash = id.indexOf('-');
  const language = dash < 0 ? id : id.slice(0, dash);
  runtimes.push({
    id,
    language,
    version: dash < 0 ? '' : id.slice(dash + 1),
    // "experimental" passes its tests.json and is schedulable; only the admin console flags it.
    status: status === 'experimental' ? 'ready' : status,
    filename: tests.filename ?? '',
    lane: HOT_LANGUAGES.has(language) ? 'hot' : 'cold',
  });
}
runtimes.sort((a, b) => a.id.localeCompare(b.id));
const catalogByBase = Object.fromEntries(RUNTIME_CATALOG.map((e) => [e.base, e]));
const bases = [...new Set([...runtimeDirs, ...Object.keys(catalogByBase)])].sort();

const languages = bases.map((base) => {
  const entry = catalogByBase[base];
  const name = entry?.name ?? capitalize(base);
  const sample = SAMPLES[base];
  return {
    base,
    name,
    shortCode: shortCodeOf(base, name),
    category: categoryOf(base),
    tagline: entry?.tagline ?? null,
    // Newest first, as the web orders them; decides the default version (C++23 before C++98).
    versions: entry?.versions ?? [],
    importHint: IMPORT_HINT[base] ?? DEFAULT_IMPORT_HINT,
    multiFileSupported: !MULTIFILE_UNSUPPORTED.has(base),
    lineComment: LINE_COMMENT[base] ?? null,
    blockComment: BLOCK_COMMENT[base] ?? null,
    starter: sample ? { code: sample.code, tests: normalizeTests(sample.tests) } : null,
  };
});

const examples = EXAMPLES.map((e) => ({
  id: e.id,
  title: e.title,
  difficulty: e.difficulty.toUpperCase(),
  statement: e.blurb,
  tests: normalizeTests(e.tests),
}));

const problems = PROBLEM_CATALOG.map((p) => ({
  slug: p.slug,
  title: p.title,
  difficulty: p.difficulty.toUpperCase(),
  tags: p.tags,
  tagline: p.tagline,
  statement: p.statement,
  examples: p.examples.map((x) => ({ input: x.input, output: x.output, explanation: x.explanation ?? null })),
  constraints: p.constraints,
  solutions: Object.fromEntries(Object.entries(p.solutions ?? {}).map(([base, s]) => [base, s.code])),
  tests: normalizeTests(p.tests),
}));

const catalog = {
  version: 1,
  defaultLimits: { timeMs: DEFAULT_TIME_MS, memMb: DEFAULT_MEM_MB },
  runtimeLimits: RUNTIME_LIMITS,
  languages,
  runtimes,
  examples,
  problems,
};

await mkdir(dirname(outFile), { recursive: true });
await writeFile(outFile, JSON.stringify(catalog, null, 1) + '\n');
console.log(`catalog.json: ${languages.length} languages, ${runtimes.length} runtimes, ${examples.length} examples, ${problems.length} problems`);
