# Integration tests

End-to-end tests that drive a real CARDS instance through a browser, with [Playwright].

Unit tests check a class or a component in isolation; these check that a built, launched, fully wired
CARDS actually works. They are correspondingly slow — an instance boot plus a browser — so they do not run
in an ordinary build.

```
mvn clean install                        # does not build this module at all
mvn clean install -PintegrationTests     # builds everything, then runs these
```

## How it fits together

Each suite gets **its own instance, launched from its own aggregated feature**, on its own reserved port:

| Suite  | Feature    | Instance                                                                 |
|--------|------------|--------------------------------------------------------------------------|
| `core` | `core_tar` | The distribution `./start_cards.sh` launches, with no content beyond what the modules ship |

The instance is started by the [Sling feature launcher Maven plugin][launcher] — the same launcher
`start_cards.py` uses, pointed at the same published artifacts, so what the tests drive is the real
runtime rather than a lookalike assembled for testing.

Ports are reserved by `build-helper-maven-plugin` rather than hard-coded, so concurrent builds — and
anything already sitting on 8080 — do not collide. Maven passes each instance's URL to Playwright through
the environment, and `playwright.config.ts` turns those into one project per instance.

### Adding a suite

Three edits: a `<launch>` in `pom.xml`, its URL and skip flag in the environment of the `run e2e tests`
execution, and an entry in `src/test/e2e/support/instances.ts`. The specs then go in a directory named
after it.

The catch is the feature. The launcher plugin can only be pointed at **one** feature, so a suite that
needs more than the core distribution — the questionnaires and modules that `start_cards.py --test`
composes at launch time, say — needs those combined into an aggregate of its own in
`distribution/slingfeature` first. That turns out to be the better answer anyway: what a test run
exercised is then one versioned coordinate, and `analyse-features` validates the combination at build time
instead of it failing at startup.

## Running one suite

Each suite has a skip property that turns off both its launch and its Playwright project:

```
mvn clean install -PintegrationTests -Dit.core.skip=true
```

That is how a CI matrix should run them once there is more than one: an instance per job keeps peak memory
to a single instance rather than all of them at once. With `core` the only suite, skipping it leaves
nothing to run, and the build says so rather than reporting a vacuous success — to not run these at all,
leave the `integrationTests` profile off.

## Watching it, debugging it, picking browsers

Everything the Playwright CLI takes can be passed through the build with `-Dplaywright.args`, so none of
these need you to leave Maven:

```
mvn install -PintegrationTests -Dplaywright.args=--headed                    # a real browser window
mvn install -PintegrationTests -Dplaywright.args=--debug                     # the Playwright Inspector
mvn install -PintegrationTests -Dplaywright.args=--ui                        # time-travelling UI mode
mvn install -PintegrationTests -Dplaywright.args="--grep @smoke"             # only tagged tests
mvn install -PintegrationTests -Dplaywright.args="--project=core-chromium"   # one suite/engine
mvn install -PintegrationTests -Dplaywright.browsers="chromium firefox"      # download and run both
```

The first three want a display, and two of them want someone to drive — they are for iterating locally,
not for CI, where a build sitting on the Inspector is sitting on you. `mvn` swallows a bare
`-Dkey=a b`, so anything containing a space needs the quotes above.

`playwright.browsers` is the single source of truth: the same list decides which engines are downloaded
and which are run, so an engine can never be installed and then quietly ignored. Each one gets a project
per suite, named `<suite>-<browser>` — the suffix is there even with one browser, so that a `--project=`
written down today keeps meaning the same thing after a second engine is added. Unknown names are
rejected rather than silently treated as Chromium.

## Running against an instance you already have

The suites only need a URL, so an instance started by hand works just as well, and iterating this way is
far quicker than a Maven round trip:

```
cd src/test/e2e
yarn install
yarn playwright install chromium
CARDS_CORE_URL=http://localhost:8080 yarn test --project=core-chromium
CARDS_CORE_URL=http://localhost:8080 yarn test:ui           # watch mode, time-travel debugging
CARDS_CORE_URL=http://localhost:8080 yarn test:headed       # a real browser window
CARDS_CORE_URL=http://localhost:8080 yarn test:debug        # the Playwright Inspector
CARDS_CORE_URL=http://localhost:8080 PLAYWRIGHT_BROWSERS="chromium firefox" yarn test
```

## Browsers need system libraries

Downloading Chromium is not enough; it needs about ten shared libraries that a minimal container image
will not have. Playwright says so clearly when they are missing:

```
sudo yarn playwright install-deps        # or the apt-get line Playwright prints
```

This needs root, so it belongs in the CI image build rather than the test run. Only Chromium is installed
by default (`-Dplaywright.browsers="chromium firefox"` to widen it) — the other engines multiply both the
download and the system packages for very little extra signal on an internal application.

## Writing tests

- **Specs live under `specs/<suite>/`**, and only run against that suite's instance. Everything shared —
  page objects, flows, assertions, fixtures — lives outside those directories and is imported.
- **Locate things the way a person finds them**: by label, by role, by the text on the button. That keeps
  the tests honest about accessibility and stops a restyle from breaking them. Where a control has no
  accessible name to find it by, the fix is to give it one in the application, as the sign-out and avatar
  buttons were given theirs.
- **Prefer an API-level assertion where one is equally meaningful.** It runs anywhere, needs no browser,
  and fails faster. `homepage.smoke.spec.ts` keeps both forms deliberately: the API one covers the three
  paths that resolve to the homepage, the browser one proves a browser really renders it.
- **A suite's readiness is part of the suite.** The launcher only waits for the OSGi framework, and the
  frontend fetches most content once on mount without ever retrying — so a page that loads before its
  content is installed renders permanently without it, and no amount of assertion retrying recovers. Name
  what a suite depends on in `Instance.readyPaths` (probed anonymously, exactly as the browser reads it)
  rather than hoping.
- **The suite runs single-threaded**, because these tests sign in and out of one shared instance and
  nothing isolates one test's session from another's. A test that needs to run alongside others has to
  earn it first.
- A failing run leaves a trace, a screenshot and a video in `target/e2e-report`. Open it with
  `yarn report` — for anything that only fails on CI, that is the difference between a diagnosis and a
  guess.

## Typechecking and linting

These sources are held to the same standard as the application frontend, and both checks gate the build —
they run at `generate-resources`, before the instance boots and before Chromium is downloaded, so a type
error or a lint violation is reported in seconds rather than after a full launch:

```
yarn typecheck        # tsc --noEmit
yarn lint             # eslint .
yarn lint --fix       # for the formatting and import-order ones
```

`eslint.config.mjs` is `aggregated-frontend`'s configuration with the React-only parts removed; the rule
set that remains is deliberately identical, so a rule tightened there is worth tightening here.
`support/check-results.mjs` runs under bare Node rather than through Playwright's transpiler, so it is
JavaScript and outside the TypeScript project.

## Why the test run does not fail the build directly

The run is deferred and the verdict enforced at `verify` by `support/check-results.mjs`, the same way
`maven-failsafe-plugin` defers its own. This is not ceremony: a failure during `integration-test` skips
`post-integration-test`, and the launcher plugin's shutdown hook kills the launcher *script* without
killing the JVM underneath it. Every failing run would otherwise orphan a full Sling instance, reparented
to init and still holding its port — which then silently pushes the next run onto a different port.

[Playwright]: https://playwright.dev/
[launcher]: https://github.com/apache/sling-feature-launcher-maven-plugin
