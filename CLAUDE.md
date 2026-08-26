# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Jenkins plugin (`org.allurereport.jenkins:allure-jenkins-plugin`, `hpi` packaging) that publishes Allure test reports as a post-build action and serves them in the Jenkins UI. Java 11, Maven, Jenkins baseline `2.462`.

## Build & test commands

Uses the Maven wrapper — do not invoke a system `mvn`.

```bash
./mvnw package                                    # full build with unit tests + spotless/checkstyle/pmd/spotbugs (bound to `validate` phase)
./mvnw test                                       # unit tests only
./mvnw verify                                     # runs integration tests (*IT classes via maven-failsafe-plugin)
./mvnw test -Dtest=ReportBuilderTest              # single unit test class
./mvnw test -Dtest=ReportBuilderTest#methodName   # single unit test method
./mvnw verify -Dit.test=AllureReportBuildActionIT # single integration test
./mvnw hpi:run                                    # launch Jenkins at localhost:8080/jenkins with the plugin loaded (dev loop)
./mvnw spotless:apply                             # auto-format Java/XML/properties
```

Integration tests download `allure-commandline` (version pinned in `pom.xml` as `allureCommandline.version`) into `target/resources/test/` during the `pre-integration-test` phase — the first `verify` after a clean needs network access.

Spotless is configured with `ratchetFrom=origin/main`, so formatting checks only apply to files changed relative to `main`. Checkstyle/PMD/SpotBugs run on every build at the `validate` phase and can fail `./mvnw package`.

## Architecture — the big picture

### Post-build flow (writing a report)

`AllureReportPublisher#perform` (`src/main/java/org/allurereport/jenkins/AllureReportPublisher.java`) is the entry point. It:

1. Resolves result directories from globs via `FindByGlob` (a `MasterToSlaveFileCallable`).
2. Enriches `allure-results/` on the agent with executor/environment/testrun metadata and copies history from the previous build (via `callables/Add*Info` and `AllureReportArchiveSource`).
3. Picks the CLI (`AllureCommandlineInstallation` for Allure 2, `Allure3Installation` for Allure 3 — `getAllureVersion()` gates which one) and invokes `ReportBuilder#build`, which shells out to `allure generate`.
4. **Archives the generated report into `allure-report.zip` via `AllureReportArchive` (agent-side `MasterToSlaveFileCallable` using `TrueZipArchiver`), uploads it through `run.pickArtifactManager().archive(...)`, and then deletes both the local zip and the unpacked directory.** This is the source of truth now — there is no persistent unpacked report directory. This was introduced in PR #433 (`dcbfc95 Remove unzipped report dir`) to fix the "disk consumed twice" regression (issue #426).
5. Attaches an `AllureReportBuildAction` to the `Run` and applies threshold-based result policies.

Matrix support: `AllureReportPublisher` is a `MatrixAggregatable`. Per-configuration results are copied to the parent workspace (`copyResultsToParentIfNeeded`), then aggregated in `MatrixAggregator#endBuild`.

### Serving the report (reading)

`AllureReportBuildAction#doDynamic` is the Stapler entry point for `/<job>/<build>/allure/...`. It resolves a report source via `AllureReportArchiveSourceFactory.forRun(run)` and returns one of two inner `HttpResponse` handlers:

- `ArchiveReportBrowser` — wraps an `AllureReportArchiveSource` and streams individual entries out of the zip on demand. Used for both locally-stored zips and remote artifact managers (S3, Azure, etc.).
- `DirectoryReportBrowser` — legacy path that serves from an unpacked directory under `run.getRootDir()`; only reached if the archive isn't present.

**`AllureReportArchiveSource` abstraction** (`src/main/java/org/allurereport/jenkins/utils/`) is the key architectural piece for understanding report serving:

- `LocalFileArchiveSource` — opens `<artifactsDir>/allure-report.zip` with `java.util.zip.ZipFile` (random access, fast entry lookup).
- `ArtifactManagerArchiveSource` — goes through `run.getArtifactManager().root()` / `VirtualFile`. If the zip lives on S3/Azure, `VirtualFile.open()` returns a stream from remote storage, and entries are located via `ZipEntryInputStream` which **linearly scans a `ZipInputStream`** (no central-directory random access over HTTP). This is the reason remote-stored reports are slow: every click re-opens the remote stream and scans from the start until the target entry is found. PR #446 (`f60fcb3 Fix Allure report browsing with S3`) is what introduced this behavior — before, S3-stored archives weren't browsable at all.
- `FallbackArchiveSource` — tries local first, falls back to artifact manager. This is what `AllureReportArchiveSourceFactory.forRun()` returns.

History propagation between builds also goes through `AllureReportArchiveSource` — `FilePathUtils#getPreviousRunWithHistory` and `copyHistoryToResultsPaths` read `history/*.json` entries out of the previous build's archive, so history works the same whether the archive is local or remote.

### Configuration & UI

- Jelly views for each `Action` live under `src/main/resources/org/allurereport/jenkins/<ActionClassName>/`.
- `AllureReportPublisherDescriptor` owns global configuration (commandline installations, properties). The legacy `AllureBuildAction` (deprecated, `DirectoryBrowserSupport`-based) is retained only for deserializing old builds via `AllureXStreamAliases`.
- `dsl/` package integrates with the `job-dsl` plugin; `config/` holds POJOs (`AllureReportConfig`, `ResultsConfig`, `ReportBuildPolicy`, `ResultPolicy`).
- Allure 3 vs Allure 2: `isAllure3()` branches throughout. Allure 3 is Node-based (no JDK configuration, different summary paths under `awesome/widgets/summary.json`), Allure 2 is Java-based.

## Investigation context (from `../instructions.md`)

This repo was cloned to investigate performance of Allure report browsing with the S3 Artifact Manager plugin after upgrading to v2.35.2. Key historical points:

- **Before ~v2.34**: the generated Allure report directory lived unpacked under `<run>/archive/allure-report/` and was served with `DirectoryBrowserSupport` (old `AllureBuildAction`, still present as deprecated). No zip existed.
- **v2.34.0**: added zipping of the report for artifact storage but **did not remove the unpacked copy** — hence issue #426 (disk used twice).
- **v2.35.0 (PR #433)**: removes the unpacked directory after archiving; introduces the `AllureReportArchiveSource` abstraction so the UI reads entries out of the zip on demand.
- **v2.35.1 (PR #446)**: fixes browsing when the zip is stored via an artifact manager (e.g. S3) — adds `ArtifactManagerArchiveSource` + `ZipEntryInputStream`. **This is the path that is slow over S3**: linear scan of a remote `ZipInputStream` per request.

When reasoning about performance: the bottleneck is the linear scan in `ZipEntryInputStream.open()` combined with the fact that `ArchiveReportBrowser#generateResponse` opens a fresh stream per request via `AllureReportArchiveSourceFactory.forRun(run)` (no caching of central directory, no range-read optimization).
