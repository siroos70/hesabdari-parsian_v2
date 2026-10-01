#!/usr/bin/env node
// Tests for scripts/github-actions-pin-manager.mjs.
// Run: node --test scripts/test-github-actions-pin-manager.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import {
  parseUsesLine,
  renderUsesLine,
  parseTarget,
  refShape,
  versionFromComment,
  parseVersion,
  compareVersions,
  normalizeVersion,
  listStableVersions,
  resolveTagToCommitSha,
  scanFiles,
  planUpdates,
  applyUpdates,
  validateChanges,
  buildPullRequestBody,
  ensurePullRequest,
  runApply,
  createApi,
  versionAffected,
  securityClassification,
} from './github-actions-pin-manager.mjs';

const SHA_A = 'a'.repeat(40);
const SHA_B = 'b'.repeat(40);
const SHA_C = 'c'.repeat(40);

function json(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

// Minimal routing mock of the GitHub REST surface used by the planner.
function makeFetch(routes) {
  const calls = [];
  const impl = async (url, options = {}) => {
    calls.push({ method: options.method || 'GET', url });
    for (const r of routes) {
      const m = url.match(r.url);
      if (m && (r.method || 'GET') === (options.method || 'GET')) {
        return typeof r.reply === 'function' ? r.reply(m, options) : r.reply;
      }
    }
    return json({ message: 'not found' }, 404);
  };
  return { impl, calls };
}

const tagRefRoute = (repo, tag, sha) => ({
  method: 'GET',
  url: new RegExp(`/repos/${repo}/git/ref/tags/${tag}$`),
  reply: json({ object: { type: 'commit', sha } }),
});

// ---------------------------------------------------------------------------
// Detection: line parsing and classification
// ---------------------------------------------------------------------------

test('detects sha pinned action with version comment', () => {
  const parsed = parseUsesLine('      - uses: actions/checkout@' + SHA_A + ' # v5.0.0');
  assert.ok(parsed);
  const target = parseTarget(parsed.value);
  assert.equal(target.owner, 'actions');
  assert.equal(target.repo, 'checkout');
  assert.equal(refShape(target.ref), 'SHA');
  assert.equal(versionFromComment(parsed.comment), '5.0.0');
});

test('detects floating major tag as mutable', () => {
  const parsed = parseUsesLine('- uses: actions/cache@v4');
  const target = parseTarget(parsed.value);
  assert.equal(refShape(target.ref), 'FLOATING_TAG');
  assert.equal(target.ref, 'v4');
});

test('detects branch reference', () => {
  const t1 = refShape('main');
  const t2 = refShape('master');
  const t3 = refShape('latest');
  assert.deepEqual([t1, t2, t3], ['BRANCH', 'BRANCH', 'BRANCH']);
});

test('flags missing version comment on sha pin', () => {
  const parsed = parseUsesLine('uses: actions/checkout@' + SHA_A);
  assert.ok(parsed);
  assert.equal(parsed.comment, '');
  assert.equal(versionFromComment(''), null);
});

test('rejects malformed short sha', () => {
  assert.notEqual(refShape('a'.repeat(39)), 'SHA');
  assert.equal(refShape('a'.repeat(39)), 'OTHER');
});

test('classifies local action and skips it', () => {
  const parsed = parseUsesLine('      - name: x\n');
  assert.equal(parsed, null);
  const local = parseTarget('./.github/actions/setup-rust-android');
  assert.equal(local.kind, 'LOCAL');
});

test('classifies reusable workflow from another repository', () => {
  const parsed = parseUsesLine('  uses: owner/repo/.github/workflows/ci.yml@' + SHA_B + ' # v1.2.3');
  const target = parseTarget(parsed.value);
  assert.equal(target.kind, 'REUSABLE_WORKFLOW');
  assert.equal(target.owner, 'owner');
  assert.equal(target.repo, 'repo');
  assert.equal(refShape(target.ref), 'SHA');
  // The .yaml extension is equally valid for reusable workflows.
  const yamlTarget = parseTarget('owner/repo/.github/workflows/deploy.yaml@' + SHA_A);
  assert.equal(yamlTarget.kind, 'REUSABLE_WORKFLOW');
});

test('parses quoted values and preserves quote on render', () => {
  const parsed = parseUsesLine(`        uses: "actions/checkout@${SHA_A}" # v5.0.0`);
  assert.equal(parsed.quote, '"');
  assert.equal(parseTarget(parsed.value).ref, SHA_A);
  const rendered = renderUsesLine(parsed, `actions/checkout@${SHA_B}`, '# v6.0.0');
  assert.equal(rendered, `        uses: "actions/checkout@${SHA_B}" # v6.0.0`);
});

test('handles inline comment without space after hash', () => {
  const parsed = parseUsesLine('uses: dtolnay/rust-toolchain@' + SHA_C + ' #stable');
  assert.equal(versionFromComment(parsed.comment), null);
});

test('ignores non-uses lines including run steps containing word uses', () => {
  assert.equal(parseUsesLine('  run: echo uses: nothing'), null);
  assert.equal(parseUsesLine('# uses: actions/checkout@v4'), null);
});

test('scanFiles reports duplicate action references per occurrence', () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  writeFileSync(
    join(dir, 'w.yml'),
    [
      'jobs:',
      '  a:',
      '    steps:',
      '      - uses: actions/checkout@v4',
      '  b:',
      '    steps:',
      '      - uses: actions/checkout@v4',
    ].join('\n'),
  );
  const [file] = scanFiles([join(dir, 'w.yml')]);
  assert.equal(file.occurrences.length, 2);
  assert.ok(file.occurrences.every((o) => o.target.slug === 'actions/checkout'));
});

// ---------------------------------------------------------------------------
// Version handling
// ---------------------------------------------------------------------------

test('version helpers pad compare and reject', () => {
  assert.deepEqual(normalizeVersion('4'), '4.0.0');
  assert.deepEqual(normalizeVersion('4.2'), '4.2.0');
  assert.deepEqual(parseVersion('v7.10.1'), [7, 10, 1]);
  assert.ok(compareVersions('7.1.0', '7.0.1') > 0);
  assert.ok(compareVersions('7.0.1', '7.1.0') < 0);
  assert.ok(Number.isNaN(compareVersions('abc', '1.0.0')));
});

test('replacement synchronizes sha and version comment together', () => {
  const original = `        uses: actions/github-script@${SHA_A} # v7.0.1`;
  const parsed = parseUsesLine(original);
  const rendered = renderUsesLine(
    parsed,
    parsed.value.replace(/@[^@]*$/, `@${SHA_B}`),
    '# v7.1.0',
  );
  assert.equal(rendered, `        uses: actions/github-script@${SHA_B} # v7.1.0`);
  assert.ok(!rendered.includes(SHA_A));
  assert.ok(rendered.endsWith('# v7.1.0'));
});

test('applyUpdates rewrites only planned lines byte-exact elsewhere', () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  const lines = [
    'name: Demo',
    'on: push',
    'jobs:',
    '  j:',
    '    runs-on: ubuntu-latest   ',
    '    steps:',
    '      - uses: actions/checkout@v4',
    '        with:',
    '          fetch-depth: 0 # keep history',
    '      - run: echo "uses: fake"',
    '',
  ];
  const path = join(dir, 'demo.yml');
  writeFileSync(path, lines.join('\n'));
  const files = scanFiles([path]);
  const updates = [
    {
      file: path,
      line: 6,
      action: 'actions/checkout',
      currentRef: 'actions/checkout@v4',
      currentValue: '4',
      targetTag: 'v4.2.2',
      targetVersion: '4.2.2',
      targetSha: SHA_B,
      reason: 'stable update',
    },
  ];
  const changed = applyUpdates(files, updates);
  const problems = validateChanges(files, changed, updates);
  assert.deepEqual(problems, []);
  const newLines = changed[path].split('\n');
  assert.equal(newLines[6], `      - uses: actions/checkout@${SHA_B} # v4.2.2`);
  newLines.forEach((l, i) => {
    if (i !== 6) assert.equal(l, lines[i], `line ${i} must stay untouched`);
  });
});

test('validateChanges rejects unsynchronized comment', () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  const path = join(dir, 'x.yml');
  const text = `- uses: actions/checkout@${SHA_A} # v5.0.0\n`;
  writeFileSync(path, text);
  const files = scanFiles([path]);
  const bad = { ...applyUpdates(files, [{ file: path, line: 0, targetTag: 'v6.0.0', targetVersion: '6.0.0', targetSha: SHA_B }]) };
  // Simulate a stale comment by editing outside of applyUpdates.
  bad[path] = `- uses: actions/checkout@${SHA_B} # v5.0.0\n`;
  const problems = validateChanges(files, bad, [{ file: path, line: 0, targetTag: 'v6.0.0', targetVersion: '6.0.0', targetSha: SHA_B }]);
  assert.ok(problems.some((p) => p.includes('not synchronized')));
});

// ---------------------------------------------------------------------------
// Safety
// ---------------------------------------------------------------------------

async function setupRepo(tmpRoot, content) {
  mkdirSync(join(tmpRoot, '.github', 'workflows'), { recursive: true });
  const wf = join(tmpRoot, '.github', 'workflows', 'w.yml');
  writeFileSync(wf, content);
  return wf;
}

test('weekly plan converts floating tag to same major immutable pin', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, '- uses: actions/checkout@v4\n');
  const { impl } = makeFetch([
    {
      method: 'GET',
      url: /\/repos\/actions\/checkout\/releases\?/,
      reply: json([
        { tag_name: 'v6.0.0', draft: false, prerelease: false },
        { tag_name: 'v4.2.2', draft: false, prerelease: false },
      ]),
    },
    tagRefRoute('actions/checkout', 'v4.2.2', SHA_B),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v4.2.2');
  assert.equal(plan.updates[0].targetSha, SHA_B);
  // The same-major conversion wins over the major-jump report: the exclusive
  // update/reportOnly branch must not emit a major-bump row here.
  assert.equal(plan.reportOnly.length, 0);
});

test('weekly plan reports major bump instead of applying it', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: actions/checkout@${SHA_A} # v5.0.0\n`);
  const { impl } = makeFetch([
    {
      method: 'GET',
      url: /\/repos\/actions\/checkout\/releases\?/,
      reply: json([{ tag_name: 'v7.0.0', draft: false, prerelease: false }]),
    },
    // The recorded version of the pin must resolve cleanly for the drift
    // gate to pass before the major-bump report path is exercised.
    tagRefRoute('actions/checkout', 'v5.0.0', SHA_A),
    tagRefRoute('actions/checkout', 'v7.0.0', SHA_C),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.reportOnly.length, 1);
  assert.ok(plan.reportOnly[0].note.includes('major'));
});

test('weekly mode pins an up-to-date stable tag immutably', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, '- uses: some/action@v1.0.0\n');
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v1.0.0', SHA_A),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  // No newer release exists, but the mutable tag itself must become a pin.
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v1.0.0');
  assert.equal(plan.updates[0].targetSha, SHA_A);
  assert.ok(plan.updates[0].reason.includes('immutable'));
});

test('weekly plan pins floating tag equal to newest same major release', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, '- uses: some/action@v4\n');
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v4.0.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v4.0.0', SHA_A),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v4.0.0');
  assert.equal(plan.updates[0].targetSha, SHA_A);
  assert.equal(plan.reportOnly.length, 0);
});

test('prerelease and drafts are rejected as targets', async () => {
  const stable = listStableVersions;
  const api = {
    getReleases: async () => [
      { tag_name: 'v9.9.9-rc1', draft: false, prerelease: true },
      { tag_name: 'v9.9.8', draft: true, prerelease: false },
      { tag_name: 'v9.9.7', draft: false, prerelease: false },
    ],
    getTags: async () => [],
  };
  const result = await stable(api);
  assert.deepEqual(result.versions.map((vItem) => vItem.tag), ['v9.9.7']);
});

test('unknown release source is reported not guessed', async () => {
  const api = {
    getReleases: async () => {
      const err = new Error('nf');
      err.status = 404;
      throw err;
    },
    getTags: async () => [],
  };
  const result = await listStableVersions(api);
  assert.equal(result.source, 'unknown');
  assert.equal(result.versions.length, 0);
});

test('invalid resolved sha aborts candidate', async () => {
  const api = {
    getReleases: async () => [{ tag_name: 'v2.0.0', draft: false, prerelease: false }],
    getTags: async () => [],
    getTagRef: async () => ({ object: { type: 'commit', sha: 'z'.repeat(39) } }),
  };
  await assert.rejects(() => resolveTagToCommitSha(api, 'v2.0.0'), /not a 40-hex SHA|cannot resolve/);
});

test('annotated tag dereferences to commit sha', async () => {
  const api = {
    getReleases: async () => [],
    getTags: async () => [{ name: 'v1.2.3' }],
    getTagRef: async () => ({ object: { type: 'tag', sha: SHA_A } }),
    getTagObject: async () => ({ object: { type: 'commit', sha: SHA_B } }),
  };
  const result = await listStableVersions(api);
  assert.equal(result.source, 'tags');
  const sha = await resolveTagToCommitSha(api, 'v1.2.3');
  assert.equal(sha, SHA_B);
});

test('tag movement between resolutions aborts candidate', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.0.0\n`);
  let n = 0;
  const { impl } = makeFetch([
    {
      method: 'GET',
      url: /\/repos\/evil\/action\/releases\?/,
      reply: json([{ tag_name: 'v1.1.0', draft: false, prerelease: false }]),
    },
    // Recorded version resolves to the pinned commit; only the TARGET tag moves.
    tagRefRoute('evil/action', 'v1.0.0', SHA_A),
    {
      method: 'GET',
      url: /\/repos\/evil\/action\/git\/ref\/tags\/v1\.1\.0$/,
      reply: () => json({ object: { type: 'commit', sha: ++n === 1 ? SHA_B : SHA_C } }),
    },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  // The candidate must be aborted and surfaced, never silently applied.
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].aborted, true);
  assert.ok(plan.updates[0].reason.includes('moved during resolution'));
  assert.equal(plan.updates[0].targetSha, undefined);
});

test('duplicate occurrences reuse verified tag resolutions within one run', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(
    dir,
    [
      `- uses: some/action@${SHA_A} # v1.0.0`,
      `- uses: some/action@${SHA_A} # v1.0.0`,
    ].join('\n') + '\n',
  );
  const { impl, calls } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v1.0.0', SHA_A),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  // Two occurrences of the same pin double-resolve the recorded tag once
  // (2 requests for the movement check); the verified cache then serves the
  // second occurrence with zero additional requests.
  const tagRefs = calls.filter((c) => c.url.includes('/git/ref/tags/v1.0.0'));
  assert.equal(tagRefs.length, 2);
});

test('branch reference goes to needsHuman not auto updated', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, '- uses: some/action@main\n');
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }]) },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
});

test('sha pin missing version comment goes to needsHuman', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A}\n`);
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v2.0.0', draft: false, prerelease: false }]) },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('version comment'));
});

test('partial or channel sha comments go to needsHuman', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(
    dir,
    [
      `- uses: Swatinem/rust-cache@${SHA_A} # v2`,
      `- uses: dtolnay/rust-toolchain@${SHA_B} # stable`,
    ].join('\n') + '\n',
  );
  const { impl } = makeFetch([
    { method: 'GET', url: /\/repos\/Swatinem\/rust-cache\/releases\?/, reply: json([{ tag_name: 'v2.8.0', draft: false, prerelease: false }]) },
    tagRefRoute('Swatinem/rust-cache', 'v2.8.0', SHA_C),
    { method: 'GET', url: /\/repos\/dtolnay\/rust-toolchain\/releases\?/, reply: json([]) },
    { method: 'GET', url: /\/repos\/dtolnay\/rust-toolchain\/tags\?/, reply: json([]) },
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 2);
  for (const rItem of plan.needsHuman) {
    assert.ok(rItem.note.includes('usable version comment'));
  }
});

test('partial version comment cannot drive an automatic update', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A} # v5\n`);
  const { impl, calls } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v5.9.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v5.9.0', SHA_B),
  ]);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('usable version comment'));
  // No drift resolution may even start from an unprovable baseline.
  const tagCalls = calls.filter((c) => c.url.includes('/git/ref/tags/'));
  assert.equal(tagCalls.length, 0);
});

// ---------------------------------------------------------------------------
// Security advisory classification (range-based affectedness)
// ---------------------------------------------------------------------------

const ADV_MATCH = {
  ghsa_id: 'GHSA-aaaa-bbbb-cccc',
  cve_id: 'CVE-2026-0001',
  summary: 'action allows template injection',
  html_url: 'https://github.com/evil/action/security/advisories/GHSA-aaaa-bbbb-cccc',
  vulnerabilities: [
    {
      vulnerable_version_range: '>= 1.0.0, < 1.4.2',
      first_patched_version: '1.4.2',
    },
  ],
};

function advisoriesRoutes() {
  return [
    { method: 'GET', url: /\/repos\/evil\/action\/security-advisories\?/, reply: json([ADV_MATCH]) },
    { method: 'GET', url: /\/advisories\?affects=/, reply: json([]) },
    { method: 'GET', url: /\/repos\/evil\/action\/releases\?/, reply: json([{ tag_name: 'v1.4.2', draft: false, prerelease: false }, { tag_name: 'v1.5.0', draft: false, prerelease: false }]) },
    // The drift gate runs for every mode; the recorded pin must verify.
    tagRefRoute('evil/action', 'v1.2.0', SHA_A),
    tagRefRoute('evil/action', 'v1.4.2', SHA_B),
  ];
}

test('versionAffected matches ranges strictly and rejects unknown clauses', () => {
  assert.equal(versionAffected('1.0.0', ADV_MATCH.vulnerabilities[0]), true);
  assert.equal(versionAffected('1.4.1', ADV_MATCH.vulnerabilities[0]), true);
  assert.equal(versionAffected('1.4.2', ADV_MATCH.vulnerabilities[0]), false);
  assert.equal(versionAffected('2.0.0', ADV_MATCH.vulnerabilities[0]), false);
  assert.equal(versionAffected('1.2.3', { vulnerable_version_range: '~> junk', first_patched_version: '2' }), false);
  // Missing or unparsable patched version: never affected, whatever the range.
  assert.equal(versionAffected('1.2.3', { vulnerable_version_range: '< 9.9.9' }), false);
  // Single-sided ranges are real advisory formats and are fully supported
  // when they name a parseable patch.
  assert.equal(versionAffected('1.2.3', { vulnerable_version_range: '< 1.4.2', first_patched_version: '1.4.2' }), true);
  assert.equal(versionAffected('1.4.2', { vulnerable_version_range: '< 1.4.2', first_patched_version: '1.4.2' }), false);
  assert.equal(versionAffected('1.2.3', { vulnerable_version_range: '= 1.2.3', first_patched_version: '1.4.2' }), true);
  // The { identifier } object shape is normalized like the string form.
  assert.equal(
    versionAffected('1.2.3', { vulnerable_version_range: '< 1.4.2', first_patched_version: { identifier: '1.4.2' } }),
    true,
  );
  assert.equal(versionAffected('1.2.3', { vulnerable_version_range: '< 1.4.2', first_patched_version: {} }), false);
});

test('securityClassification requires a parseable patched version', () => {
  const ok = securityClassification([ADV_MATCH], '1.2.0');
  assert.equal(ok.level, 'SECURITY');
  assert.deepEqual(ok.patched, ['1.4.2']);
  const noPatch = securityClassification(
    [{ vulnerabilities: [{ vulnerable_version_range: '< 9.9.9', first_patched_version: null }] }],
    '1.2.0',
  );
  assert.equal(noPatch.level, 'NEEDS_HUMAN');
  assert.equal(securityClassification([], '1.0.0').level, 'NO_UPDATE');
});

test('securityClassification splits confirmed and unproven advisories', () => {
  const unrelated = {
    ghsa_id: 'GHSA-zzzz-0000-1111',
    vulnerabilities: [{ vulnerable_version_range: '>= 9.0.0, < 9.1.0', first_patched_version: '9.1.0' }],
  };
  const mixed = securityClassification([ADV_MATCH, unrelated], '1.2.0');
  assert.equal(mixed.level, 'SECURITY');
  assert.equal(mixed.confirmed.length, 1);
  assert.deepEqual(mixed.confirmed[0].patched, ['1.4.2']);
  assert.equal(mixed.unproven.length, 1);
  assert.equal(mixed.unproven[0].ghsa_id, 'GHSA-zzzz-0000-1111');
  // Every advisory unproven still means human review, never silence.
  const noneConfirmed = securityClassification([unrelated], '1.2.0');
  assert.equal(noneConfirmed.level, 'NEEDS_HUMAN');
  assert.equal(noneConfirmed.confirmed.length, 0);
  assert.equal(noneConfirmed.unproven.length, 1);
});

test('security mode proposes update only when the range covers the pinned version', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  const api = createApi({ fetchImpl: makeFetch(advisoriesRoutes()).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 1);
  const upd = plan.updates[0];
  assert.equal(upd.reason, 'SECURITY UPDATE');
  assert.equal(upd.targetTag, 'v1.4.2');
  assert.equal(upd.advisories.length, 1);
  assert.ok(upd.advisories[0].range.includes('< 1.4.2'));
});

test('global advisory source contributes when the repo endpoint fails', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  // Repo advisories are unreachable; only the global database knows.
  const routes = advisoriesRoutes().map((r) => {
    if (r.url.source.includes('security-advisories')) {
      return { method: r.method, url: r.url, reply: json({ message: 'Not Found' }, 404) };
    }
    return r.url.source.includes('/advisories') && !r.url.source.includes('repos')
      ? { method: r.method, url: r.url, reply: json([ADV_MATCH]) }
      : r;
  });
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v1.4.2');
});

test('advisory outside the pinned version range is NEEDS_HUMAN not SECURITY', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v2.5.0\n`);
  const routes = advisoriesRoutes().map((r) =>
    r.url.source.includes('releases')
      ? { method: 'GET', url: /\/repos\/evil\/action\/releases\?/, reply: json([{ tag_name: 'v2.6.0', draft: false, prerelease: false }]) }
      : r,
  );
  // The recorded pin of THIS occurrence must verify for the drift gate.
  routes.push(tagRefRoute('evil/action', 'v2.5.0', SHA_A));
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('affectedness'));
});

test('partial version baseline cannot prove affectedness and stays NEEDS_HUMAN', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1\n`);
  const api = createApi({ fetchImpl: makeFetch(advisoriesRoutes()).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  // The planner gate refuses a partial SHA baseline before any mode logic.
  assert.ok(plan.needsHuman[0].note.includes('usable version comment'));
});

test('security mode refuses drifted sha pin', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  // Prepend an overriding ref route: the recorded tag v1.2.0 resolves to
  // SHA_B, NOT the pinned SHA_A.
  const routes = [tagRefRoute('evil/action', 'v1.2.0', SHA_B), ...advisoriesRoutes()];
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('drift'));
});

const ADV_STRONGER = {
  ghsa_id: 'GHSA-dddd-eeee-ffff',
  cve_id: 'CVE-2026-0002',
  summary: 'second flaw fixed only in a newer release',
  html_url: 'https://github.com/evil/action/security/advisories/GHSA-dddd-eeee-ffff',
  vulnerabilities: [
    {
      vulnerable_version_range: '>= 1.0.0, < 1.5.1',
      first_patched_version: '1.5.1',
    },
  ],
};

function multiAdvisoryRoutes(releases) {
  return [
    { method: 'GET', url: /\/repos\/evil\/action\/security-advisories\?/, reply: json([ADV_MATCH, ADV_STRONGER]) },
    { method: 'GET', url: /\/advisories\?affects=/, reply: json([]) },
    { method: 'GET', url: /\/repos\/evil\/action\/releases\?/, reply: json(releases) },
    tagRefRoute('evil/action', 'v1.2.0', SHA_A),
  ];
}

test('security target satisfies the newest required patched version across advisories', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  const routes = multiAdvisoryRoutes([
    { tag_name: 'v1.5.1', draft: false, prerelease: false },
    { tag_name: 'v1.4.2', draft: false, prerelease: false },
  ]);
  routes.push(tagRefRoute('evil/action', 'v1.5.1', SHA_B));
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 1);
  // Must not stop at the weaker v1.4.2 fix.
  assert.equal(plan.updates[0].targetTag, 'v1.5.1');
});

test('security target picks newest release above the strongest patched baseline when the exact patch is absent', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  // v1.5.1 was never released; the older patch v1.4.2 exists but does not
  // satisfy the stronger advisory.
  const routes = multiAdvisoryRoutes([
    { tag_name: 'v2.0.0', draft: false, prerelease: false },
    { tag_name: 'v1.4.2', draft: false, prerelease: false },
  ]);
  routes.push(tagRefRoute('evil/action', 'v2.0.0', SHA_C));
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v2.0.0');
});

test('repo published advisories with patched_versions classify like global ones', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  // The repository advisory endpoint reports the fix as patched_versions.
  const routes = [
    {
      method: 'GET',
      url: /\/repos\/evil\/action\/security-advisories\?/,
      reply: json([
        {
          ghsa_id: 'GHSA-repo-2222-3333',
          vulnerabilities: [{ vulnerable_version_range: '>= 1.0.0, < 1.4.2', patched_versions: '1.4.2' }],
        },
      ]),
    },
    { method: 'GET', url: /\/advisories\?affects=/, reply: json([]) },
    { method: 'GET', url: /\/repos\/evil\/action\/releases\?/, reply: json([{ tag_name: 'v1.4.2', draft: false, prerelease: false }]) },
    tagRefRoute('evil/action', 'v1.2.0', SHA_A),
    tagRefRoute('evil/action', 'v1.4.2', SHA_B),
  ];
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v1.4.2');
});

test('one failing advisory source does not discard the other', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: evil/action@${SHA_A} # v1.2.0\n`);
  // Repository source fails; the global source still names the advisory.
  const routes = advisoriesRoutes().map((r) =>
    r.url.source.includes('security-advisories')
      ? { method: 'GET', url: r.url, reply: json({ message: 'boom' }, 500) }
      : r.url.source.includes('affects=')
        ? { method: 'GET', url: r.url, reply: json([ADV_MATCH]) }
        : r,
  );
  const api = createApi({ fetchImpl: makeFetch(routes).impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.errors.length, 0);
  assert.equal(plan.updates.length, 1);
  assert.equal(plan.updates[0].targetTag, 'v1.4.2');
});

test('duplicate occurrences share one advisory lookup', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(
    dir,
    [
      `- uses: evil/action@${SHA_A} # v1.2.0`,
      `- uses: evil/action@${SHA_A} # v1.2.0`,
    ].join('\n') + '\n',
  );
  const { impl, calls } = makeFetch(advisoriesRoutes());
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'security');
  assert.equal(plan.updates.length, 2);
  const repoAdvisories = calls.filter((c) => c.url.includes('/security-advisories'));
  const globalAdvisories = calls.filter((c) => c.url.includes('/advisories?affects='));
  assert.equal(repoAdvisories.length, 1);
  assert.equal(globalAdvisories.length, 1);
});

// ---------------------------------------------------------------------------
// Pin drift detection
// ---------------------------------------------------------------------------

test('sha that no longer matches its recorded version tag is flagged as drift', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: actions/github-script@${SHA_A} # v7.0.1\n`);
  const api = createApi({
    fetchImpl: makeFetch([
      { method: 'GET', url: /\/repos\/actions\/github-script\/releases\?/, reply: json([{ tag_name: 'v7.0.1', draft: false, prerelease: false }, { tag_name: 'v7.1.0', draft: false, prerelease: false }]) },
      // The recorded tag resolves to a DIFFERENT commit than the pin.
      tagRefRoute('actions/github-script', 'v7.0.1', SHA_B),
      tagRefRoute('actions/github-script', 'v7.1.0', SHA_C),
    ]).impl,
    token: 't',
    repo: 'o/r',
  });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('drift'));
});

test('recorded version tag that no longer resolves is flagged not rebased', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: actions/github-script@${SHA_A} # v7.0.1\n`);
  const api = createApi({
    fetchImpl: makeFetch([
      { method: 'GET', url: /\/repos\/actions\/github-script\/releases\?/, reply: json([{ tag_name: 'v7.1.0', draft: false, prerelease: false }]) },
      { method: 'GET', url: /\/repos\/actions\/github-script\/git\/ref\/tags\/v7\.0\.1$/, reply: json({ message: 'nf' }, 404) },
      tagRefRoute('actions/github-script', 'v7.1.0', SHA_B),
    ]).impl,
    token: 't',
    repo: 'o/r',
  });
  const plan = await planUpdates(scanFiles([join(dir, '.github', 'workflows', 'w.yml')]), api, 'weekly');
  assert.equal(plan.updates.length, 0);
  assert.equal(plan.needsHuman.length, 1);
  assert.ok(plan.needsHuman[0].note.includes('does not resolve'));
});


test('no candidates means zero mutating API calls', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A} # v1.0.0\n`);
  const { impl, calls } = makeFetch([
    // Unknown release source: nothing can be proposed.
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json({ message: 'nf' }, 404) },
    { method: 'GET', url: /\/repos\/some\/action\/tags\?/, reply: json([]) },
    { method: 'GET', url: /\/advisories\?affects=/, reply: json([]) },
    { method: 'GET', url: /\/repos\/some\/action\/security-advisories\?/, reply: json([]) },
  ]);
  const result = await runApply({
    repoRoot: dir,
    repo: 'o/r',
    token: 't',
    mode: 'weekly',
    fetchImpl: impl,
    log: () => {},
  });
  assert.equal(result.applied, false);
  const mutations = calls.filter((cItem) => cItem.method !== 'GET');
  assert.deepEqual(mutations, []);
});

test('applyUpdates skips aborted candidates and applies verified ones', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  const path = join(dir, 'w.yml');
  writeFileSync(path, `- uses: good/action@${SHA_A} # v1.0.0\n- uses: flaky/action@${SHA_B} # v2.0.0\n`);
  const files = scanFiles([path]);
  const updates = [
    { file: path, line: 0, targetTag: 'v1.1.0', targetVersion: '1.1.0', targetSha: SHA_C },
    { file: path, line: 1, aborted: true },
  ];
  const changed = applyUpdates(files, updates);
  const newLines = changed[path].split('\n');
  assert.equal(newLines[0], `- uses: good/action@${SHA_C} # v1.1.0`);
  // The aborted line must stay byte-identical; it has no verified target.
  assert.equal(newLines[1], `- uses: flaky/action@${SHA_B} # v2.0.0`);
  const problems = validateChanges(files, changed, updates);
  assert.deepEqual(problems, []);
});

test('runApply refuses the batch when every candidate was aborted', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A} # v1.0.0\n`);
  let n = 0;
  const { impl, calls } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }, { tag_name: 'v1.1.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v1.0.0', SHA_A),
    { method: 'GET', url: /\/repos\/some\/action\/git\/ref\/tags\/v1\.1\.0$/, reply: () => json({ object: { type: 'commit', sha: ++n % 2 === 1 ? SHA_B : SHA_C } }) },
  ]);
  const result = await runApply({
    repoRoot: dir,
    repo: 'o/r',
    token: 't',
    mode: 'weekly',
    fetchImpl: impl,
    log: () => {},
  });
  assert.equal(result.applied, false);
  assert.equal(result.plan.updates[0].aborted, true);
  const mutations = calls.filter((cItem) => cItem.method !== 'GET');
  assert.deepEqual(mutations, []);
});

test('default branch resolution failure refuses the PR with a clear error', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A} # v1.0.0\n`);
  const { impl, calls } = makeFetch([
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }, { tag_name: 'v1.1.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v1.0.0', SHA_A),
    tagRefRoute('some/action', 'v1.1.0', SHA_B),
    { method: 'GET', url: /\/repos\/o\/r$/, reply: json({ default_branch: 'main' }) },
    { method: 'GET', url: /\/repos\/o\/r\/git\/ref\/heads\/main$/, reply: json({ message: 'boom' }, 500) },
  ]);
  await assert.rejects(
    runApply({ repoRoot: dir, repo: 'o/r', token: 't', mode: 'weekly', fetchImpl: impl, log: () => {} }),
    /cannot resolve default branch SHA/,
  );
  // The refusal happens before any write attempt.
  const mutations = calls.filter((cItem) => cItem.method !== 'GET');
  assert.deepEqual(mutations, []);
});

// ---------------------------------------------------------------------------
// PR lifecycle
// ---------------------------------------------------------------------------

function lifecycleRoutes({ existingPr = [], branchExists = false } = {}) {
  const state = {};
  return {
    state,
    routes: [
      { method: 'GET', url: /\/repos\/o\/r$/, reply: json({ default_branch: 'main' }) },
      { method: 'GET', url: /\/repos\/o\/r\/git\/ref\/heads\/main$/, reply: json({ object: { sha: SHA_A } }) },
      { method: 'GET', url: /\/repos\/o\/r\/git\/commits\/[0-9a-f]{40}$/, reply: json({ tree: { sha: 'tree0'.padEnd(40, '0') } }) },
      { method: 'POST', url: /\/repos\/o\/r\/git\/blobs$/, reply: json({ sha: SHA_B }) },
      { method: 'POST', url: /\/repos\/o\/r\/git\/trees$/, reply: json({ sha: SHA_C }) },
      { method: 'POST', url: /\/repos\/o\/r\/git\/commits$/, reply: json({ sha: SHA_A }) },
      {
        method: 'GET',
        url: /\/repos\/o\/r\/git\/ref\/heads\/ci\/pin-updates$/,
        reply: () => json(branchExists ? { object: { sha: SHA_B } } : { message: 'nf' }, branchExists ? 200 : 404),
      },
      {
        method: 'PATCH',
        url: /\/repos\/o\/r\/git\/refs\/heads\/ci\/pin-updates$/,
        reply: () => {
          state.updatedRef = true;
          return json({});
        },
      },
      {
        method: 'POST',
        url: /\/repos\/o\/r\/git\/refs$/,
        reply: () => {
          state.createdRef = true;
          return json({});
        },
      },
      { method: 'GET', url: /\/repos\/o\/r\/pulls\?head=/, reply: json(existingPr) },
      {
        method: 'PATCH',
        url: /\/repos\/o\/r\/pulls\/\d+$/,
        reply: (m, opts) => {
          state.pullPatch = JSON.parse(opts.body);
          return json({});
        },
      },
      {
        method: 'POST',
        url: /\/repos\/o\/r\/pulls$/,
        reply: () => {
          state.createdPull = true;
          return json({ number: 42, html_url: 'https://example.test/pull/42' });
        },
      },
    ],
  };
}

function samplePlan() {
  return {
    updates: [
      {
        file: 'wf.yml',
        line: 0,
        action: 'actions/checkout',
        ownership: 'GITHUB_OWNED',
        kind: 'ACTION',
        currentValue: 'v5.0.0',
        currentRef: `actions/checkout@${SHA_A}`,
        targetTag: 'v5.1.0',
        targetVersion: '5.1.0',
        targetSha: SHA_B,
        reason: 'stable update v5.0.0 -> v5.1.0',
      },
    ],
    reportOnly: [],
    needsHuman: [],
    errors: [],
  };
}

async function runLifecycle(mode, opts) {
  const harness = lifecycleRoutes(opts);
  const { impl, calls } = makeFetch(harness.routes);
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const pr = await ensurePullRequest({
    api,
    mode,
    changed: { 'wf.yml': 'new content' },
    plan: samplePlan(),
  });
  return { pr, calls, state: harness.state };
}

test('no existing pr creates exactly one pull request', async () => {
  const { pr, calls } = await runLifecycle('weekly', {});
  assert.equal(pr.created, true);
  const created = calls.filter((c) => c.method === 'POST' && /\/pulls$/.test(c.url));
  assert.equal(created.length, 1);
  assert.ok(calls.some((c) => c.method === 'POST' && /git\/refs$/.test(c.url)));
});

test('existing open pr is updated not duplicated', async () => {
  const { pr, calls, state } = await runLifecycle('weekly', {
    existingPr: [{ number: 7, html_url: 'u7' }],
    branchExists: true,
  });
  assert.equal(pr.created, false);
  assert.equal(pr.number, 7);
  assert.ok(calls.some((c) => c.method === 'PATCH' && /refs\/heads/.test(c.url)));
  const created = calls.filter((c) => c.method === 'POST' && /\/pulls$/.test(c.url));
  assert.equal(created.length, 0);
  // The stale description must be refreshed with the current batch.
  assert.ok(state.pullPatch);
  assert.equal(state.pullPatch.title, 'chore(ci): update pinned GitHub Actions');
  assert.ok(state.pullPatch.body.includes('stable update v5.0.0 -> v5.1.0'));
});

test('security mode uses its own branch title and body header', async () => {
  let pullPayload = null;
  const routed = makeFetch(lifecycleRoutes({}).routes).impl;
  const impl = async (url, options = {}) => {
    if (/\/repos\/o\/r\/pulls$/.test(url) && options.method === 'POST') {
      pullPayload = JSON.parse(options.body);
    }
    return routed(url, options);
  };
  const api = createApi({ fetchImpl: impl, token: 't', repo: 'o/r' });
  const pr = await ensurePullRequest({
    api,
    mode: 'security',
    changed: { 'wf.yml': 'new content' },
    plan: samplePlan(),
  });
  assert.equal(pr.created, true);
  // The payload sent to GitHub must itself carry the security identity.
  assert.ok(pullPayload.title.startsWith('security(ci):'));
  assert.ok(pullPayload.body.startsWith('## SECURITY UPDATE'));
});

test('pr body lists verification steps and disables auto merge', () => {
  const body = buildPullRequestBody('weekly', samplePlan());
  assert.ok(body.includes('stable (no draft, no prerelease)'));
  assert.ok(body.includes('twice'));
  assert.ok(body.includes('Auto-merge is disabled'));
  assert.ok(body.includes(`\`${SHA_B}\``));
});

test('pr body reports aborted candidates separately from verified updates', () => {
  const plan = samplePlan();
  plan.updates.push({
    file: 'wf.yml',
    line: 3,
    action: 'evil/action',
    aborted: true,
    reason: 'stable update v1.0.0 -> v1.1.0 ABORTED: tag v1.1.0 moved during resolution',
  });
  const body = buildPullRequestBody('weekly', plan);
  assert.ok(body.includes('### Candidates aborted during verification'));
  assert.ok(body.includes('moved during resolution'));
  // The aborted row must stay out of the verified-updates table.
  const verifiedSection = body.split('### Candidates aborted')[0];
  assert.ok(!verifiedSection.includes('evil/action'));
});

function applyHarnessRoutes({ defaultBranch = 'main' } = {}) {
  return [
    { method: 'GET', url: /\/repos\/some\/action\/releases\?/, reply: json([{ tag_name: 'v1.0.0', draft: false, prerelease: false }, { tag_name: 'v1.1.0', draft: false, prerelease: false }]) },
    tagRefRoute('some/action', 'v1.0.0', SHA_A),
    tagRefRoute('some/action', 'v1.1.0', SHA_B),
    { method: 'GET', url: /\/repos\/o\/r$/, reply: json({ default_branch: defaultBranch }) },
    { method: 'GET', url: new RegExp(`/repos/o/r/git/ref/heads/${defaultBranch}$`), reply: json({ object: { sha: SHA_A } }) },
    { method: 'GET', url: /\/repos\/o\/r\/git\/commits\/[0-9a-f]{40}$/, reply: json({ tree: { sha: 'tree0'.padEnd(40, '0') } }) },
    { method: 'POST', url: /\/repos\/o\/r\/git\/blobs$/, reply: json({ sha: SHA_B }) },
    { method: 'POST', url: /\/repos\/o\/r\/git\/trees$/, reply: json({ sha: SHA_C }) },
    { method: 'POST', url: /\/repos\/o\/r\/git\/commits$/, reply: json({ sha: SHA_A }) },
    { method: 'GET', url: /\/repos\/o\/r\/git\/ref\/heads\/ci\/pin-updates$/, reply: json({ message: 'nf' }, 404) },
    { method: 'POST', url: /\/repos\/o\/r\/git\/refs$/, reply: json({}) },
    { method: 'GET', url: /\/repos\/o\/r\/pulls\?head=/, reply: json([]) },
    { method: 'POST', url: /\/repos\/o\/r\/pulls$/, reply: json({ number: 9, html_url: 'u9' }) },
  ];
}

test('workflow tree entry uses repository-relative path', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A} # v1.0.0\n`);
  const routed = makeFetch(applyHarnessRoutes()).impl;
  let treePath = null;
  const impl = async (url, options = {}) => {
    if (/\/git\/trees$/.test(url) && options.body) {
      treePath = JSON.parse(options.body).tree[0].path;
    }
    return routed(url, options);
  };
  const result = await runApply({
    repoRoot: dir,
    repo: 'o/r',
    token: 't',
    mode: 'weekly',
    fetchImpl: impl,
    log: () => {},
  });
  assert.equal(result.applied, true);
  // The scanned file lives at an absolute temp path; the Git tree entry must
  // carry the repository-relative POSIX path instead.
  assert.equal(treePath, '.github/workflows/w.yml');
});

test('pr base uses repository default branch', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pinmgr-'));
  await setupRepo(dir, `- uses: some/action@${SHA_A} # v1.0.0\n`);
  const routed = makeFetch(applyHarnessRoutes({ defaultBranch: 'trunk' })).impl;
  let pullBody = null;
  const impl = async (url, options = {}) => {
    if (/\/repos\/o\/r\/pulls$/.test(url) && options.body) {
      pullBody = JSON.parse(options.body);
    }
    return routed(url, options);
  };
  const result = await runApply({
    repoRoot: dir,
    repo: 'o/r',
    token: 't',
    mode: 'weekly',
    fetchImpl: impl,
    log: () => {},
  });
  assert.equal(result.applied, true);
  assert.equal(pullBody.base, 'trunk');
  assert.equal(pullBody.head, 'ci/pin-updates');
});
