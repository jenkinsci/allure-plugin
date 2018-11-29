package ru.yandex.qatools.allure.jenkins.config;

import hudson.model.Result;
import hudson.model.Run;

/**
 * eroshenkoam.
 * 6/25/14
 */
@SuppressWarnings("TrailingComment")
public enum ReportBuildPolicy {

    ALWAYS("For all builds", new ReportBuildPolicyDecision() {
        @Override
        public boolean isNeedToBuildReport(Run run) {
            return true;
        }
    }),

    UNSTABLE("For all unstable builds", new ReportBuildPolicyDecision() {
        @Override
        public boolean isNeedToBuildReport(Run run) {
            return run != null && Result.UNSTABLE.equals(run.getResult());
        }
    }),

    UNSUCCESSFUL("For unsuccessful builds", new ReportBuildPolicyDecision() {
        @Override
        public boolean isNeedToBuildReport(Run run) {
            return run != null && isUnsuccessful(run.getResult());
        }

        private boolean isUnsuccessful(Result result) {
            return Result.UNSTABLE.equals(result) || Result.FAILURE.equals(result);
        }
    });

    private String title;

    private ReportBuildPolicyDecision decision; //NOSONAR

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
