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
package org.allurereport.jenkins.utils;

import hudson.model.Result;

import java.util.Map;

/**
 * @author Egor Borisov ehborisov@gmail.com
 */
public class BuildSummary {

    private Map<String, Integer> statistics;

    public BuildSummary withStatistics(final Map<String, Integer> statistics) {
        this.statistics = statistics;
        return this;
    }

    private Integer getStatistic(final String key) {
        return this.statistics != null ? statistics.get(key) : 0;
    }

    public long getFailedCount() {
        return getStatistic("failed");
    }

    public long getPassedCount() {
        return getStatistic("passed");
    }

    public long getSkipCount() {
        return getStatistic("skipped");
    }

    public long getBrokenCount() {
        return getStatistic("broken");
    }

    public long getUnknownCount() {
        return getStatistic("unknown");
    }

    public Result getResult() {
        if (getFailedCount() > 0 || getBrokenCount() > 0) {
            return Result.UNSTABLE;
        }
        return Result.SUCCESS;
    }
}
