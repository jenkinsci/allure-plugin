/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.config;

import hudson.model.Result;
import hudson.model.Run;

/**
 * eroshenkoam.
 * 6/25/14
 */
@SuppressWarnings("TrailingComment")
public enum ReportBuildPolicy {

    ALWAYS("For all builds", run -> true),

    UNSTABLE("For all unstable builds", run -> run != null && Result.UNSTABLE.equals(run.getResult())),

    UNSUCCESSFUL("For unsuccessful builds", new ReportBuildPolicyDecision() {
        @Override
        public boolean isNeedToBuildReport(final Run run) {
            return run != null && isUnsuccessful(run.getResult());
        }

        private boolean isUnsuccessful(final Result result) {
            return Result.UNSTABLE.equals(result) || Result.FAILURE.equals(result);
        }
    });

    private final String title;

    private final transient ReportBuildPolicyDecision decision; //NOSONAR

    ReportBuildPolicy(final String title,
                      final ReportBuildPolicyDecision decision) {
        this.decision = decision;
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String getValue() {
        return name();
    }

    public boolean isNeedToBuildReport(final Run run) {
        return decision.isNeedToBuildReport(run);
    }
}
