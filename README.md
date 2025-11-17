# Allure Jenkins Plugin

[![release](https://img.shields.io/github/v/release/jenkinsci/allure-plugin?style=flat)](https://github.com/jenkinsci/allure-plugin/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/jenkinsci/allure-plugin/build.yml?branch=master&style=flat)](https://github.com/jenkinsci/allure-plugin/actions/workflows/build.yml?query=branch%3Amaster)

> This repository contains the source code of the Allure plugin for Jenkins.

[<img src="https://allurereport.org/public/img/allure-report.svg" height="85px" alt="Allure Report logo" align="right" />](https://allurereport.org "Allure Report")

- Learn more about Allure Report at [https://allurereport.org](https://allurereport.org)
- 📚 [Documentation](https://allurereport.org/docs/) – discover official documentation for Allure Report
- ❓ [Questions and Support](https://github.com/orgs/allure-framework/discussions/categories/questions-support) – get help from the team and community
- 📢 [Official announcements](https://github.com/orgs/allure-framework/discussions/categories/announcements) –  stay updated with our latest news and updates
- 💬 [General Discussion](https://github.com/orgs/allure-framework/discussions/categories/general-discussion) – engage in casual conversations, share insights and ideas with the community
- 🖥️ [Live Demo](https://demo.allurereport.org/) — explore a live example of Allure Report in action

## Getting Started

This plugin allows you to create Allure reports as part of your Jenkins builds. You can then view the generated report directly in Jenkins or download it to your machine.

To learn more, please visit [the official documentation](https://allurereport.org/docs/integrations-jenkins/).

### Advanced Threshold Policies

Overview
 - The plugin can now assess build stability using:
 - Percentage-based thresholds
 - Absolute failure-count thresholds
 - Aggregated evaluation in matrix builds
 - Optional preservation of the original Jenkins build result

This functionality is fully backward-compatible. Existing pipelines continue to operate without modification unless new parameters are explicitly provided.

### Parameters
| Parameter                       | Description                                                             |
|---------------------------------|-------------------------------------------------------------------------|
| `unstableThresholdPercent`      | Marks build **UNSTABLE** if % of failed tests ≥ threshold               |
| `failureThresholdPercent`       | Marks build **FAILURE** if % of failed tests ≥ threshold                |
| `failureThresholdCount`         | Marks build **FAILURE** if number of failed tests ≥ threshold           |
| `resultPolicy` (`DEFAULT`, `LEAVE_AS_IS`) | Controls whether Allure modifies the final build result      |
| `results`                        | Supports glob patterns for multi-axis builds (e.g., `**/allure-results`) |

### Improved Usage Examples

Below you can find practical examples demonstrating how to use threshold policies in a Jenkins Pipeline.
All examples are fully functional and can be copied directly into any Pipeline job to the configuration pipeline.

#### 1. Mark build UNSTABLE when failures ≥ 20%

This rule is useful if you want to tolerate a small number of failures but highlight instability when the failure rate grows.
```groovy
node {
stage('Prepare tests') {
sh '''
set -eu
python3 -m venv venv && . venv/bin/activate
pip install -q pytest allure-pytest
mkdir -p tests
# 6 passed, 2 failed (25%)
for i in $(seq 1 6); do printf "def test_pass_${i}(): assert 1==1\n" >> tests/test.py; done
for i in $(seq 1 2); do printf "def test_fail_${i}(): assert 1==2\n" >> tests/test.py; done
pytest -q --alluredir=allure-results || true
'''
}

    stage('Publish Allure (UNSTABLE ≥ 20%)') {
        allure(
          results: [[path: 'allure-results']],
          unstableThresholdPercent: 20
        )
    }
}
```
#### 2. Mark build FAILURE when ≥ 3 tests fail

Absolute count is often clearer than percentage — good for teams with small test suites.

```groovy
node {
    stage('Prepare tests') {
        sh '''
          set -eu
          python3 -m venv venv && . venv/bin/activate
          pip install -q pytest allure-pytest
          mkdir -p tests
          # 4 failed tests
          for i in $(seq 1 4); do printf "def test_fail_${i}(): assert 1==2\n" >> tests/test.py; done
          pytest -q --alluredir=allure-results || true
        '''
    }

    stage('Publish Allure (FAILURE ≥ 3 fails)') {
        allure(
          results: [[path: 'allure-results']],
          failureThresholdCount: 3
        )
    }
}
```

#### 3. Matrix builds: aggregated evaluation (UNSTABLE ≥ 30%)
The plugin automatically aggregates all test results from parallel axes.
```groovy
node {
    stage('Matrix') {
        parallel(
            FLAVOR_A: {
                sh '''
                  mkdir -p A/tests
                  python3 -m venv venvA && . venvA/bin/activate
                  pip install -q pytest allure-pytest
                  # 33% failures
                  for i in $(seq 1 4); do printf "def test_pass_${i}(): assert 1==1\n" >> A/tests/test.py; done
                  for i in $(seq 1 2); do printf "def test_fail_${i}(): assert 1==2\n" >> A/tests/test.py; done
                  pytest -q A/tests --alluredir=A/allure-results || true
                '''
            },
            FLAVOR_B: {
                sh '''
                  mkdir -p B/tests
                  python3 -m venv venvB && . venvB/bin/activate
                  pip install -q pytest allure-pytest
                  # 0% failures
                  for i in $(seq 1 6); do printf "def test_pass_${i}(): assert 1==1\n" >> B/tests/test.py; done
                  pytest -q B/tests --alluredir=B/allure-results || true
                '''
            }
        )
    }

    stage('Publish Allure (UNSTABLE ≥ 30%)') {
        allure(
          results: [[path: '**/allure-results']],
          unstableThresholdPercent: 30
        )
    }
}
```

#### 4. Preserve Jenkins build result (LEAVE_AS_IS)

Use this when you want Allure reports without affecting pipeline status.
```groovy
node {
    stage('Prepare tests') {
        sh '''
          set -eu
          python3 -m venv venv && . venv/bin/activate
          pip install -q pytest allure-pytest
          mkdir -p tests
          printf "def test_fail(): assert 1==2\n" >> tests/test.py
          pytest -q --alluredir=allure-results || true
        '''
    }

    stage('Publish Allure (LEAVE_AS_IS)') {
        allure(
          results: [[path: 'allure-results']],
          resultPolicy: 'LEAVE_AS_IS'
        )
    }
}
```

#### 5. Default behavior (no thresholds) → UNSTABLE
This example shows explicitly where the default behavior still applies.
```groovy
node {
    stage('Prepare tests') {
        sh '''
          set -eu
          python3 -m venv venv && . venv/bin/activate
          pip install -q pytest allure-pytest
          mkdir -p tests
          printf "def test_fail(): assert 1==2\n" >> tests/test.py
          pytest -q --alluredir=allure-results || true
        '''
    }

    stage('Publish Allure (default → UNSTABLE)') {
        allure(
          results: [[path: 'allure-results']],
          reportBuildPolicy: 'ALWAYS'
        )

        if (currentBuild.currentResult != 'UNSTABLE') {
            error "Expected UNSTABLE in default behavior, but was ${currentBuild.currentResult}"
        }
    }
}
```

####

Compatibility Notes
 - If no threshold parameters are provided, the plugin uses its original behavior.
 - Thresholds apply only when Allure results are present and successfully generated.
 - This feature does not alter the reporting format or Allure commandline behavior.

## Useful links

* [Issues](https://github.com/jenkinsci/allure-plugin/issues?labels=&milestone=&page=1&state=open)
* [Releases](https://github.com/jenkinsci/allure-plugin/releases)

## Contact us

* Mailing list: [allure@qameta.io](mailto:allure@qameta.io)
* StackOverflow tag: [Allure](http://stackoverflow.com/questions/tagged/allure)
