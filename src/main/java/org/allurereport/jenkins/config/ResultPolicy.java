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
import org.allurereport.jenkins.utils.BuildSummary;

public enum ResultPolicy {
    LEAVE_AS_IS,
    UNSTABLE_IF_FAILED_OR_BROKEN,
    FAILURE_IF_FAILED_OR_BROKEN;

    public Result decide(final BuildSummary summary) {
        final boolean hasProblems = summary.getFailedCount() > 0 || summary.getBrokenCount() > 0;

        return switch (this) {
            case LEAVE_AS_IS -> null;
            case FAILURE_IF_FAILED_OR_BROKEN -> hasProblems ? Result.FAILURE : Result.SUCCESS;
            case UNSTABLE_IF_FAILED_OR_BROKEN -> hasProblems ? Result.UNSTABLE : Result.SUCCESS;
        };
    }
}
