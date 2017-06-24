package ru.yandex.qatools.allure.jenkins.dsl;

import javaposse.jobdsl.dsl.Context;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;

/**
 * @author Marat Mavlutov <{@literal mavlyutov@yandex-team.ru}>
 */
class AllureReportPublisherContext implements Context {

    private static final String FAILURE_POLICY = "FAILURE";

    private AllureReportPublisher publisher;

    AllureReportPublisherContext(AllureReportPublisher publisher) {
        this.publisher = publisher;
    }

    public AllureReportPublisher getPublisher() {
        return publisher;
    }

    public void buildFor(String buildPolicy) {
        final String policy = buildPolicy.equals(FAILURE_POLICY) ? ReportBuildPolicy.UNSUCCESSFUL.getValue()
                : buildPolicy;
        getPublisher().setReportBuildPolicy(ReportBuildPolicy.valueOf(policy));
    }

    public void jdk(String jdk) {
        this.getPublisher().setJdk(jdk);
    }

    public void commandline(String commandline) {
        getPublisher().setCommandline(commandline);
    }

    public void property(String key, String value) {
        getPublisher().getProperties().add(new PropertyConfig(key, value));
    }

    public void includeProperties(boolean includeProperties) {
        getPublisher().setIncludeProperties(includeProperties);
    }
}
