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
package ru.yandex.qatools.allure.jenkins.dsl;

import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.config.ResultPolicy;

import javaposse.jobdsl.dsl.Context;

/**
 * @author Marat Mavlutov <{@literal mavlyutov@yandex-team.ru}>
 */
class AllureReportPublisherContext implements Context {

    private static final String FAILURE_POLICY = "FAILURE";

    @SuppressWarnings("PMD.ImmutableField")
    private final AllureReportPublisher publisher;

    AllureReportPublisherContext(final AllureReportPublisher publisher) {
        this.publisher = publisher;
    }

    public AllureReportPublisher getPublisher() {
        return publisher;
    }

    public void buildFor(final String buildPolicy) {
        final String policy = FAILURE_POLICY.equals(buildPolicy)
            ? ReportBuildPolicy.UNSUCCESSFUL.getValue()
            : buildPolicy;
        getPublisher().setReportBuildPolicy(ReportBuildPolicy.valueOf(policy));
    }

    public void jdk(final String jdk) {
        this.getPublisher().setJdk(jdk);
    }

    public void disabled(final boolean disabled) {
        this.getPublisher().setDisabled(disabled);
    }

    public void commandline(final String commandline) {
        getPublisher().setCommandline(commandline);
    }

    public void property(final String key, final String value) {
        getPublisher().getProperties().add(new PropertyConfig(key, value));
    }

    public void includeProperties(final boolean includeProperties) {
        getPublisher().setIncludeProperties(includeProperties);
    }

    public void configPath(final String configPath) {
        getPublisher().setConfigPath(configPath);
    }

    public void resultPolicy(final String policy) {
        getPublisher().setResultPolicy(ResultPolicy.valueOf(policy));
    }
    public void unstableThresholdPercent(final int value) {
        getPublisher().setUnstableThresholdPercent(value);
    }
    public void failureThresholdPercent(final int value) {
        getPublisher().setFailureThresholdPercent(value);
    }
    public void unstableThresholdCount(final int value) {
        getPublisher().setUnstableThresholdCount(value);
    }
    public void failureThresholdCount(final int value) {
        getPublisher().setFailureThresholdCount(value);
    }
    public void reportName(final String reportName) {
        getPublisher().setReportName(reportName);
    }
}
