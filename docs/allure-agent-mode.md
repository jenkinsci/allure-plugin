# Allure Agent Mode

Use Allure agent mode to design, review, validate, debug, and enrich tests in this project.

Before authoring or materially changing a test, invoke the `$allure-agent-mode` skill and read its `references/test-design.md`. If the skill is unavailable, use the test-design floor below and keep conclusions conservative.

## Why Agent Mode

A test run is an instrument, not a pass/fail to scrape. `allure agent` preserves a reviewable account of the tests, failures, runtime artifacts, and automated findings. Give every result-informing run a `--goal`, read the printed summary, and then inspect the generated agent output beginning with its `AGENTS.md` and `index.md`.

Do not reduce a run to console fragments with `tail`, `grep`, `head`, or `/dev/null`. The console is a status signal; the agent output is the evidence.

## Local Capability Snapshot

- Allure wrapper: `npx --yes allure@3`
- Capability snapshot last checked: 2026-08-26
- Refresh with: `npx --yes allure@3 --version`, `npx --yes allure@3 agent --help`, `npx --yes allure@3 agent capabilities --json`, and `npx --yes allure@3 agent -h=1`
- Agent execution: supported with `--goal`, `--output`, `--results-dir`, expectations, environments, selection, and reruns
- Human report modes: `--report auto|off|awesome|config`
- Latest/state recovery: `agent latest`, `agent state-dir`, and `agent query`
- Selection/rerun: `agent select`, `--rerun-latest`, and `--rerun-from <output>` with `review`, `failed`, `unsuccessful`, or `all` presets
- Existing results: `agent inspect <allure-results-dir-or-glob>`
- CI dumps: `agent inspect --dump <archive-or-glob>`; repeat `--dump` to combine archives

Node.js and npm are required for the project wrapper. A compatible global `allure` command may be used interactively, but committed examples use the same `npx` wrapper as CI.
The `allure@3` selector intentionally floats to the newest Allure 3 release published to npm; workflows do not pin a minor or patch version.

## Test Surfaces And Results

| Surface | Runner | Test root | Results | Prerequisites |
| --- | --- | --- | --- | --- |
| Regular unit tests | Maven Surefire + JUnit 4 | `src/test/java/**/*Test.java` | `target/allure-results` | Java and Maven wrapper |
| Regular integration tests | Maven Failsafe + JUnit 4 | `src/test/java/**/*IT.java` | `target/allure-results` | Jenkins test harness; the build downloads an Allure commandline fixture |
| Jenkins compatibility smoke | Maven Surefire + JUnit 5/Testcontainers | `compat/jenkins-smoke/src/test/java` | `compat-artifacts/<jenkins-version>/allure-results` | Docker, Java 17, and a built plugin HPI |
| S3 Artifact Manager compatibility | Maven Surefire + JUnit 5/Testcontainers | `S3ArtifactManagerCompatibilityTest` | `compat-artifacts/2.541.3-s3/allure-results` | Docker, Java 17, a built plugin HPI, and the `s3-compat` profile; MinIO and Toxiproxy images are pulled automatically |

The regular test adapter is configured in `pom.xml`; its stable result path is configured in `src/test/resources/allure.properties`. The compatibility runner has its own adapter and result path in `compat/jenkins-smoke/pom.xml`.

The Jenkins plugin harness also executes generated validation tests under `org.jvnet.hudson.test`. They are part of the regular Maven signal even though they are not declared with `@Test` in this repository. Some integration tests create nested `allure-results` fixtures containing deliberately failed sample data, so regular agent and CI runs must pass `--results-dir target/allure-results` instead of using unrestricted discovery.

The root suite supports Maven's `-Dtest=ClassName` or `-Dtest=ClassName#method` selectors for Surefire and `-Dit.test=ClassName` for Failsafe. The compatibility runner supports `-Dtest=JenkinsCompatibilitySmokeTest` and standard JUnit 5 method selectors through Surefire. Its S3 scenario is opt-in via `-Ps3-compat -Dtest=S3ArtifactManagerCompatibilityTest`; follow `compat/README.md` for the pinned topology and latency contract.

## Run Profiles

Use the CLI-provided temporary agent output by default.

### Focused unit test

```bash
npx --yes allure@3 agent \
  --results-dir target/allure-results \
  --goal "Confirm the selected unit-test behavior and preserve reviewable evidence" \
  -- ./mvnw -B -ntp -Dtest=Allure3ConfigTest test
```

Replace the selector with the class or method relevant to the change.

### Full regular suite

```bash
npx --yes allure@3 agent \
  --results-dir target/allure-results \
  --goal "Confirm the complete regular unit and integration suite" \
  -- ./mvnw -B -ntp verify
```

`package` does not reach Maven's Failsafe integration-test and verify phases. Use `verify` when the conclusion is meant to cover all regular tests.

### Compatibility smoke

Build the HPI first, then follow `compat/README.md` for the version-specific Maven properties. Wrap only the compatibility test command:

```bash
npx --yes allure@3 agent \
  --results-dir "$PWD/compat-artifacts/2.541.3/allure-results" \
  --goal "Confirm Jenkins compatibility for the selected Jenkins version" \
  -- ./mvnw -B -e -ntp -f compat/jenkins-smoke/pom.xml \
    -Dcompat.rootDir="$PWD" \
    -Dcompat.version=2.541.3 \
    -Dcompat.artifactRoot="$PWD/compat-artifacts/2.541.3" \
    test
```

Compatibility coverage is a separate Docker-backed signal; a regular `verify` run does not include it.

## Core Review Loop

1. State the intended behavior, scope, and confidence limit in `--goal`.
2. Decide whether a small fresh expectation would protect the conclusion.
3. Use `--report off` for intermediate private runs and `--report auto` or `awesome` for the final reviewable run.
4. Run the narrowest command that supports the conclusion.
5. Print and open the output directory's `index.md`.
6. Read its `AGENTS.md`, `manifest/run.json`, `manifest/test-events.jsonl`, `manifest/tests.jsonl`, `manifest/findings.jsonl`, and relevant per-test Markdown before inspecting source.
7. If expectations were used, inspect `manifest/expected.json` and confirm the contracted scope resolved correctly.
8. If a runner-visible failure is absent from `manifest/tests.jsonl`, inspect `artifacts/global/` for stdout/stderr and describe the review as partial.
9. Enrich weak evidence only at meaningful behavior or helper boundaries, then rerun with fresh output.

## Expectations

Inline controls are supported for exact test count, full names and prefixes, labels, environment, minimum steps or attachments, and attachment name/content type. Confirm exact syntax with `npx --yes allure@3 agent --help` before use.

Use the smallest expectation that protects a real claim. Do not add step or attachment expectations merely to force decorative evidence. Every expectation run must use fresh expectations for its exact intended scope; never reuse expectation state across unrelated runs.

When no stable count or identity is known, omit expectations, review the observed scope from the manifests, and state that scope checking was observational.

## Failure Triage And Reruns

Read the failing per-test evidence and findings before editing. Classify the failure as a product defect, stale or wrong expectation, fixture/environment problem, or flake. Do not weaken assertions or hide the test to get a green run.

For reruns, use the captured test plan instead of reconstructing runner selectors:

```bash
npx --yes allure@3 agent --rerun-latest --rerun-preset unsuccessful \
  --goal "Recheck only unsuccessful tests after the fix" \
  -- ./mvnw -B -ntp verify
```

## Inspecting CI Evidence

The build workflow runs on Ubuntu and Windows and retains one Allure dump per environment as
`allure-results-build-<environment>`. Its always-run report job merges both dumps into the
`allure-report` artifact. Report plugins, grouping, and conditional Allure Service access are
configured in `allurerc.mjs`. When the `ALLURE_SERVICE_TOKEN` GitHub Actions secret is available,
the same report generation command also publishes the report to Allure Service. Fork pull requests,
where repository secrets are unavailable, still retain the downloadable dumps and report artifacts.
Compatibility jobs retain `compat-<version>-allure3-dump`; the dedicated S3 job retains
`compat-2.541.3-s3-allure3-dump`.

Inspect downloaded artifacts before trying to reproduce a CI-only failure:

```bash
npx --yes allure@3 agent inspect \
  --goal "Review the downloaded CI execution and identify actionable failures" \
  --dump 'allure-results-*.zip'
```

Pass compatibility archives with another `--dump`. Use `--report awesome` for a forced single-file report when needed.

## Output And Concurrency

Agent output, `target/allure-results`, compatibility results, CI dumps, and generated reports are separate artifacts.

- Default agent output is a CLI-managed temporary directory; recover it with `npx --yes allure@3 agent latest`.
- An explicit `--output <dir>` is caller-managed and must be removed or archived when no longer needed.
- Concurrent runs must each use a unique explicit `--output` directory. Never share output paths or expectation state; the default temporary output is unsafe for parallel runs because a newer run can replace the previous default.
- Agent mode can discover newly emitted directories named `allure-results`, but regular project runs must override discovery with `--results-dir target/allure-results` so temporary test fixtures are not reported as suite results. Clearing the canonical result directory is not required, though `clean` may still be useful for an isolated Maven build.
- For a final run, read `manifest/human-report.json`. If its status is `generated`, resolve its path against the agent output directory and share that absolute report link.

## Test-Design Floor

- Tests are behavior contracts. Do not delete, weaken, invert, skip, mute, or quarantine coverage merely to make a run pass.
- A regression test should fail for the intended defect before the fix and pass afterward, or the handoff must explain why pre-fix reproduction was impossible.
- Prefer explicit, independently runnable tests and precise observable assertions over loops, factories, conditional registration, or broad existence checks.
- Do not hide missing prerequisites behind early returns or runtime `if` branches. Use JUnit's visible `@Ignore` or `Assume` mechanics with a clear reason when suppression is legitimate.
- Current known suppression: `ReportGenerateIT.shouldFailBuildIfNoResultsFound` is explicitly ignored because of the Windows commandline exit-code behavior. There is no broader documented quarantine policy.

## Evidence Conventions

The adapters always provide test identity, status, timing, suite/package data, and failure details. Current root tests do not define a general metadata or attachment taxonomy; do not invent one.

When extra evidence is necessary:

- keep descriptions, labels, links, parameters, and intent-defining step names inline with the test
- use steps for meaningful setup, actions, external calls, and assertion phases
- attach current-execution artifacts such as generated files, Jenkins logs, command output, or fixture state only when they explain behavior or failure
- redact secrets while preserving the artifact's useful structure
- prefer Allure integrations and stable helper boundaries over wrapping every test line
- avoid placeholder steps, static success messages, stale files, and a single ceremonial step around the whole test

Accept a run only when observed scope matches the claim, coverage remains meaningful, evidence explains the result, execution limits are explicit, and no high-confidence placeholder or no-op evidence finding remains.
