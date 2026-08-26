# Compatibility Harness

This directory contains the Jenkins compatibility smoke tests used by the manual GitHub Actions workflow.

## What It Covers

The suite starts a real Jenkins controller in Docker with Testcontainers, installs the plugin under test, configures Allure CLI, and verifies these flows:

- Freestyle report generation
- Pipeline step execution
- Matrix build aggregation

Artifacts such as Allure results, Surefire reports, Jenkins logs, console output, and report HTML are written to `compat-artifacts/`.

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

The main test output lives in `compat-artifacts/<version>/allure-results`, which can be rendered with standard Allure tooling or uploaded as a workflow artifact.
