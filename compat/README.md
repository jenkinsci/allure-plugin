# Compatibility Harness

This directory contains the Jenkins compatibility smoke tests used by the GitHub Actions workflow.

## What It Covers

The suite starts a real Jenkins controller in Docker with Testcontainers, installs the plugin under test, configures Allure CLI, and verifies these flows:

- Freestyle report generation
- Pipeline step execution
- Matrix build aggregation

The harness stages Allure results, Surefire reports, Jenkins logs, console output, and report HTML under `compat-artifacts/` while it runs. GitHub Actions wraps the suite with Allure 3 and uploads a single `compat-<version>.zip` dump instead of that raw directory. The dump preserves the Allure results and their evidence attachments.

Shared diagnostics are stored as global attachments: the Jenkins controller log, Jenkins initialization and setup scripts, the plugin list, and Maven stdout and stderr. Job-specific Groovy checks, JSON responses, console logs, and report pages remain attached to the test that produced them. Surefire reports stay in the local staging directory and are not duplicated in the dump.

Pull requests run the suite against Jenkins `2.462.1` when plugin, build, workflow, or compatibility-harness files change. A newer commit on the same pull request cancels the superseded run. Use the manual workflow input to test an additional Jenkins version or exact Docker tag.

## Local Dry Run

You need Docker available locally. Build the plugin first so the runner can pick up the generated `.hpi` from `target/`.

```bash
./mvnw -DskipTests clean package
./mvnw -q -f compat/jenkins-smoke/pom.xml \
  -Dcompat.rootDir="$(pwd)" \
  -Dcompat.version=2.462.1 \
  test
```

By default, a bare Jenkins version such as `2.462.1` is normalized to the Docker image tag `2.462.1-lts-jdk17`. If you want to test an exact Docker tag, pass that tag directly via `-Dcompat.version=...`.

The main local test output lives in `compat-artifacts/<version>/allure-results`. To reproduce the CI artifact locally, run the suite through the pinned Allure 3 CLI (Node.js and npm are required):

```bash
export COMPAT_ARTIFACT_ROOT="$(pwd)/compat-artifacts/2.462.1"
mkdir -p allure-dumps
npx --yes allure@3 run \
  --dump=allure-dumps/compat-2.462.1 \
  --environment=jenkins-2-462-1 \
  -- ./mvnw -B -e -ntp -f compat/jenkins-smoke/pom.xml \
    -Dcompat.rootDir="$(pwd)" \
    -Dcompat.version=2.462.1 \
    -Dcompat.artifactRoot="$COMPAT_ARTIFACT_ROOT" \
    test
```

This creates `allure-dumps/compat-2.462.1.zip`. Generate a report from a downloaded dump with:

```bash
npx --yes allure@3 generate \
  --dump=allure-dumps/compat-2.462.1.zip
```
