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
