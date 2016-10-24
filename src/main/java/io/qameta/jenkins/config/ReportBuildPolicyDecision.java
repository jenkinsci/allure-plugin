package io.qameta.jenkins.config;

import hudson.model.Run;

/**
 * @author eroshenkoam (Artem Eroshenko)
 */
@FunctionalInterface
public interface ReportBuildPolicyDecision {

    boolean isNeedToBuildReport(Run run);

}
