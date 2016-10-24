package io.qameta.jenkins.config;

import hudson.model.Result;
import hudson.model.Run;

import java.io.Serializable;

/**
 * @author eroshenkoam (Artem Eroshenko)
 */
public enum ReportBuildPolicy implements Serializable {

    ALWAYS("For all builds", run -> true),

    UNSTABLE("For all unstable builds", run -> run.getResult().equals(Result.UNSTABLE)),

    UNSUCCESSFUL("For unsuccessful builds", run -> run.getResult().isWorseOrEqualTo(Result.UNSTABLE));

    private String title;

    private ReportBuildPolicyDecision decision;

    ReportBuildPolicy(String title, ReportBuildPolicyDecision decision) {
        this.decision = decision;
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String getValue() {
        return name();
    }

    public boolean isNeedToBuildReport(Run run) {
        return decision.isNeedToBuildReport(run);
    }
}
