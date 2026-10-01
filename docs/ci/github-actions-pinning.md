# GitHub Actions Pinning Policy

## Why immutable SHA pinning

A tag is mutable. Its owner can move it to another commit at any time. A
`uses:` line that names a tag can therefore run different code on every run.
An attacker who takes over the upstream repository, or a mistaken force-push,
can turn a routine CI run into code execution with the repository token.

This repository requires every third-party action to be pinned to a
40-character commit SHA:

```yaml
uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09 # v5.1.0
```

The SHA never changes. The upstream repository cannot change what runs. Some
existing references do not meet this policy yet. The audit reports them, and
the updater proposes fixes through pull requests.

SHA pinning protects against mutable tags and branches. It does not
automatically guarantee that a pinned commit stays safe forever. A pinned
commit can still contain a vulnerability. That is why this repository also
runs an update system; see below.

## Version comments

The `# vX.Y.Z` comment records which release the SHA belongs to. Humans need
it for review. Tools need it to compute update candidates. The updater always
writes the SHA and the comment together. It never updates one without the
other. A full triple (`vX.Y.Z`) is required for compliance. A partial
comment such as `# v5` counts as non-compliant. The updater never rebases a
SHA pin onto a partial comment. It routes that reference to human review,
because the comment alone cannot prove which release the commit is from.

## Reference classes and policy

| Class | Example | Policy |
|---|---|---|
| Third-party action | `alibaba/open-code-review@<sha>` | Immutable SHA + version comment. Updated by the manager. |
| GitHub-owned action | `actions/checkout@<sha>` | Same rule as third-party. Reported separately. |
| Reusable workflow from another repo | `o/r/.github/workflows/ci.yml@<sha>` | Same rule as third-party. None exist today. |
| Local action | `./.github/actions/setup-rust-android` | Never touched by the manager. |
| Docker reference | `docker://...` | Never touched by the manager. |

## The update system

The workflow `.github/workflows/github-actions-pin-manager.yml` runs two
scheduled jobs from `scripts/github-actions-pin-manager.mjs`.

### Daily security scan

Runs at 05:17 UTC every day. It queries GitHub Security Advisories for each
pinned action repository. An advisory counts as SECURITY only when the
tool can prove affectedness:

1. The advisory lists a `vulnerable_version_range`.
2. The exact pinned version satisfies every range clause.
3. The advisory names a parseable `first_patched_version`.

The target is the first patched release, or the newest stable release at or
above it. If an advisory exists but any of the three proofs fails, the
candidate is classified NEEDS_HUMAN. The tool never claims a security
update it cannot prove. Security candidates go to their own PR branch
`ci/pin-security`. Major version jumps are allowed there, because a fix may
require one.

### Weekly stable update

Runs Mondays at 06:23 UTC. For each pinned action it finds the newest stable
release (no draft, no prerelease). It proposes:

- same-major stable updates: applied;
- conversion of floating tags such as `@v4` to an immutable SHA of the
  newest release in that same major: applied;
- major version jumps: listed in the PR body only. A human decides;
- branch refs, missing or unusable version comments, ambiguous cases:
  reported as NEEDS_HUMAN. Never guessed.

### Verification before any change

For every target release the tool verifies, in order:

1. The release exists and is not a draft or prerelease.
2. The tag resolves to a commit SHA.
3. Annotated tag objects are dereferenced to the commit.
4. The resolved value is exactly 40 hexadecimal characters.
5. The resolution runs twice. A mismatch means the tag moved during the
   process. The candidate aborts and gets reported.
6. Only then does the tool edit the workflow line.

After editing, a validation gate re-parses every changed file. It confirms:
only planned lines changed; every new ref is a 40-hex SHA; the version
comment matches the target tag. Any failure blocks the PR.

## What is auto-proposed vs human review

Auto-proposed through a PR (never merged):

- security-driven updates;
- same-major stable updates;
- floating-tag-to-SHA conversions within the same major.

Human review required:

- major version jumps (weekly mode);
- moving or ambiguous tags;
- pins whose recorded version no longer matches the pinned commit;
- branch refs such as `@main`;
- SHA pins without any usable version comment.

## Why auto-merge is disabled

The updater edits the files that define CI itself. A bad update could
silently weaken every future review. Every PR therefore needs an explicit
human approval.

GitHub suppresses workflow runs that the built-in GITHUB_TOKEN triggers.
The updater therefore uses the `WORKFLOW_PAT` secret, the same token as
the release workflow. Events from that token start normal CI on the
maintenance pull requests. If `WORKFLOW_PAT` is not configured, the
updater falls back to GITHUB_TOKEN, and CI on its PRs is suppressed.

## Suspicious or moving tag

If the tool reports `tag moved during resolution`, do nothing automated.
Check the upstream repository manually. Compare the advisory history and the
release notes. Pin only after you trust the target commit. If you suspect
compromise, open an issue and reference `docs/SECURITY.md`.

## Local usage

```bash
# Offline compliance audit of all workflows:
node scripts/github-actions-pin-manager.mjs

# Machine-readable audit:
node scripts/github-actions-pin-manager.mjs --json

# Tests:
node --test scripts/test-github-actions-pin-manager.mjs
```

The `--apply` modes need `GITHUB_TOKEN` and `GITHUB_REPOSITORY`. They are
meant for the scheduled workflow, not for local runs.

## Test coverage

`scripts/test-github-actions-pin-manager.mjs` covers detection, version
handling, safety checks (tag movement, invalid SHAs, prereleases), and PR
lifecycle behavior. `lint.yml` runs this suite when the manager, its tests,
or any workflow file changes.
