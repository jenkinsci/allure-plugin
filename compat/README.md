# Compatibility Harness

This directory contains the Jenkins compatibility smoke runner used by the manual GitHub Actions workflow.

## What It Covers

The runner starts a real Jenkins controller in Docker with Testcontainers, installs the plugin under test, configures Allure CLI, and verifies these flows:

- Freestyle report generation
- Pipeline step execution
- Matrix build aggregation

Artifacts such as Jenkins logs, console output, report HTML, and a markdown summary are written to `compat-artifacts/`.

## Local Dry Run

You need Docker available locally. Build the plugin first so the runner can pick up the generated `.hpi` from `target/`.

```bash
./mvnw -DskipTests clean package
./mvnw -q -f compat/jenkins-smoke/pom.xml \
  -Dcompat.rootDir="$(pwd)" \
  -Dcompat.version=2.462.1 \
  exec:java
```

By default, a bare Jenkins version such as `2.462.1` is normalized to the Docker image tag `2.462.1-lts-jdk17`. If you want to test an exact Docker tag, pass that tag directly via `-Dcompat.version=...`.
