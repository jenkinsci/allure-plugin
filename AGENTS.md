# Project Guide

Use [Allure Agent Mode](docs/allure-agent-mode.md) for all test-related work in this repository.

A test run is an instrument, not a pass/fail to scrape. Running tests through `allure agent` turns them into a reviewable account of what actually happened, plus automated findings about the tests themselves. State what the run should confirm with `--goal`; the report carries the goal and evidence so an agent or human can validate it.

- Run regular result-informing tests as `npx --yes allure@3 agent --results-dir target/allure-results --goal "<what this run should confirm>" -- <test command>`, then read the generated `AGENTS.md`, `index.md`, findings, and per-test evidence. Use the compatibility result path documented in the guide for compatibility tests.
- On failure, inspect the exact failures and evidence before editing. Rerun only failed or unsuccessful tests through agent mode when supported. For CI failures, inspect the downloaded `allure-results-*` dump instead of reproducing locally.
- Use the relevant test under agent mode as a debugging instrument instead of replacing it with an ad-hoc script.
- Before writing or materially changing a test, invoke `$allure-agent-mode` and read its test-design rules. If the skill is unavailable, follow [docs/allure-agent-mode.md](docs/allure-agent-mode.md).

The console summary is only the entry point. Do not reduce a result-informing run to `tail`, `grep`, `head`, or `/dev/null`; the agent output is the signal.
