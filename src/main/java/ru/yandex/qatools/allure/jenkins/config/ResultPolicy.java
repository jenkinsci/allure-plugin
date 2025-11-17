/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *  Licensed under the Apache License, Version 2.0
 */
package ru.yandex.qatools.allure.jenkins.config;

import hudson.model.Result;
import ru.yandex.qatools.allure.jenkins.utils.BuildSummary;

public enum ResultPolicy {
    LEAVE_AS_IS,
    UNSTABLE_IF_FAILED_OR_BROKEN,
    FAILURE_IF_FAILED_OR_BROKEN;

    public Result decide(final BuildSummary summary) {
        final boolean hasProblems = summary.getFailedCount() > 0 || summary.getBrokenCount() > 0;

        switch (this) {
            case LEAVE_AS_IS:
                return null;

            case FAILURE_IF_FAILED_OR_BROKEN:
                if (hasProblems) {
                    return Result.FAILURE;
                } else {
                    return Result.SUCCESS;
                }

            case UNSTABLE_IF_FAILED_OR_BROKEN:
            default:
                if (hasProblems) {
                    return Result.UNSTABLE;
                } else {
                    return Result.SUCCESS;
                }
        }
    }
}
