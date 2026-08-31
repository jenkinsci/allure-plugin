# Compatibility Harness

This directory contains the Jenkins compatibility smoke tests used by the GitHub Actions workflow.

## What It Covers

The suite starts a real Jenkins controller in Docker with Testcontainers, installs the plugin under test, configures Allure CLI, and verifies these flows:

- Freestyle report generation with and without the optional `matrix-project` plugin installed
- Pipeline step execution
- Matrix build aggregation when `matrix-project` is installed
- Remote report browsing through `artifact-manager-s3` backed by MinIO, with Toxiproxy enforcing a slow download link

The harness stages Allure results, Surefire reports, Jenkins logs, console output, and report HTML under `compat-artifacts/` while it runs. GitHub Actions wraps the suite with Allure 3 and uploads a single `compat-<version>.zip` dump instead of that raw directory. The dump preserves the Allure results and their evidence attachments.

Shared diagnostics are stored as global attachments: the Jenkins controller log, Jenkins initialization and setup scripts, the plugin list, and Maven stdout and stderr. Job-specific Groovy checks, JSON responses, console logs, and report pages remain attached to the test that produced them. Surefire reports stay in the local staging directory and are not duplicated in the dump.

Pull requests run the suite against Jenkins `2.541.3` when plugin, build, workflow, or compatibility-harness files change. A newer commit on the same pull request cancels the superseded run. Use the manual workflow input to test an additional Jenkins version or exact Docker tag.

The S3 latency scenario runs as a separate job against Jenkins `2.541.3` and
`artifact-manager-s3:962.v15f7000205fa_`. It creates a 64 MiB incompressible
report attachment in MinIO, verifies that the report ZIP is remote and that
`index.html` follows more than 50 MiB of data, then constrains downstream S3
traffic to 1024 KiB/s with 150 ms latency. The first uncached index response must
return in under 10 seconds with the build-scoped immutable cache header. A full
archive download under that bandwidth limit would take about a minute.

## Local Dry Run

You need Docker available locally. Build the plugin first so the runner can pick up the generated `.hpi` from `target/`.

```bash
./mvnw -DskipTests clean package
./mvnw -q -f compat/jenkins-smoke/pom.xml \
  -Dcompat.rootDir="$(pwd)" \
  -Dcompat.version=2.541.3 \
  test
```

By default, a bare Jenkins version such as `2.541.3` is normalized to the Docker image tag `2.541.3-lts-jdk17`. If you want to test an exact Docker tag, pass that tag directly via `-Dcompat.version=...`.

The main local test output lives in `compat-artifacts/<version>/allure-results`. To reproduce the CI artifact locally, run the suite through the latest Allure 3 CLI from npm (Node.js and npm are required):

```bash
export COMPAT_ARTIFACT_ROOT="$(pwd)/compat-artifacts/2.541.3"
mkdir -p allure-dumps
npx --yes allure@3 run \
  --dump=allure-dumps/compat-2.541.3 \
  --environment=jenkins-2-541-3 \
  -- ./mvnw -B -e -ntp -f compat/jenkins-smoke/pom.xml \
    -Dcompat.rootDir="$(pwd)" \
    -Dcompat.version=2.541.3 \
    -Dcompat.artifactRoot="$COMPAT_ARTIFACT_ROOT" \
    test
```

This creates `allure-dumps/compat-2.541.3.zip`. Generate a report from a downloaded dump with:

```bash
npx --yes allure@3 generate \
  --config ./allurerc.mjs \
  --dump=allure-dumps/compat-2.541.3.zip
```

## S3 Artifact Manager Latency Test

The `s3-compat` Maven profile opts into the Docker-backed S3 scenario and defaults
to Jenkins `2.541.3`. MinIO and Toxiproxy are pulled automatically; no local S3
credentials or service are required. Run it through Allure agent mode so the
remote archive facts, proxy settings, measured latency, and container logs stay
reviewable:

```bash
npx --yes allure@3 agent \
  --results-dir "$PWD/compat-artifacts/2.541.3-s3/allure-results" \
  --goal "Confirm Jenkins 2.541.3 serves a greater-than-50-MiB Allure report from S3 within the constrained-link latency budget" \
  --expect-test "org.allurereport.jenkins.compat.S3ArtifactManagerCompatibilityTest.shouldServeLargeS3ReportWithinLatencyBudget" \
  -- ./mvnw -B -e -ntp -f compat/jenkins-smoke/pom.xml \
    -Ps3-compat \
    -Dtest=S3ArtifactManagerCompatibilityTest \
    -Dcompat.rootDir="$PWD" \
    -Dcompat.version=2.541.3 \
    -Dcompat.artifactRoot="$PWD/compat-artifacts/2.541.3-s3" \
    test
```

The regular compatibility command leaves this test visibly skipped because the
profile is disabled. CI stores the opt-in run separately as
`compat-2.541.3-s3-allure3-dump`.
