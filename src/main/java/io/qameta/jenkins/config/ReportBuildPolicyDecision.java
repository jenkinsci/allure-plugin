package io.qameta.jenkins.config;

import hudson.model.Run;

import java.io.Serializable;

/**
 * @author eroshenkoam (Artem Eroshenko)
 */
@FunctionalInterface
public interface ReportBuildPolicyDecision extends Serializable {

    boolean isNeedToBuildReport(Run run);

}
