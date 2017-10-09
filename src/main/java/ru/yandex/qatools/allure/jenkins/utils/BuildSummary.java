package ru.yandex.qatools.allure.jenkins.utils;

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
