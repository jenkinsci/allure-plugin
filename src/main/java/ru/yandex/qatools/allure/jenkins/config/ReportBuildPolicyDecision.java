package ru.yandex.qatools.allure.jenkins.config;

import hudson.model.Run;

/**
 * eroshenkoam.
 * 6/28/14
 */
public interface ReportBuildPolicyDecision {

    boolean isNeedToBuildReport(Run run);
}
