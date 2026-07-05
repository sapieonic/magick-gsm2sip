# Contributing

## Commit messages

This repo follows [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

Common types:

| Type       | Effect on release          |
|------------|-----------------------------|
| `feat`     | minor version bump          |
| `fix`      | patch version bump          |
| `perf`     | patch version bump          |
| `docs`, `refactor`, `test`, `build`, `ci`, `style`, `chore` | no version bump (still recorded, most hidden from changelog) |

Add `!` after the type/scope (e.g. `feat!:`) or a `BREAKING CHANGE:` footer to trigger a major version bump.

Examples:

```
feat(sip): add support for SIP TLS transport
fix(gsm): correct dropped-call detection on dual-SIM devices
feat!: remove legacy XML config format

BREAKING CHANGE: config must now be JSON; see docs/CONFIG.md
```

### Enforcement

Commit messages are linted locally via [Husky](https://typicode.github.io/husky/) +
[commitlint](https://commitlint.js.org/) on the `commit-msg` hook. One-time setup after cloning:

```
npm install
```

This installs the hook (via the `prepare` script) so non-conforming commit messages are rejected
before they're created. `npm` is only used for this lint tooling — the app itself is a Gradle/Android
project and has no other Node dependency.

## Releases

Versioning is driven entirely by commit history via
[release-please](https://github.com/googleapis/release-please):

1. Merge Conventional Commits to `main` as normal.
2. The `release-please` GitHub Action (`.github/workflows/release-please.yml`) opens/updates a
   "Release PR" that bumps `version.txt` and appends `CHANGELOG.md` based on the commits since the
   last release.
3. Merging that Release PR tags the repo (`vX.Y.Z`) and publishes a GitHub Release with the
   generated changelog.

`app/build.gradle.kts` reads `version.txt` as the single source of truth for `versionName`, and
derives `versionCode` from it (`MAJOR*1_000_000 + MINOR*1_000 + PATCH`) — never edit either by hand
outside of a release PR.
