package io.qameta.jenkins.dsl;

import javaposse.jobdsl.dsl.Context;
import io.qameta.jenkins.config.AllureReportConfig;
import io.qameta.jenkins.config.PropertyConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mavlyutov (Marat Mavlutov) <mavlyutov@yandex-team.ru>
 */
class AllureReportPublisherContext implements Context {

    private String jdk;

    private String commandline;

    private List<String> paths;

    private List<PropertyConfig> properties = new ArrayList<>();

    private ReportBuildPolicy policy = ReportBuildPolicy.ALWAYS;

    private boolean includeProperties;

    public AllureReportPublisherContext(List<String> paths) {
        this.paths = paths;
    }

    public AllureReportConfig getConfig() {
        return new AllureReportConfig(jdk, commandline, properties, policy, includeProperties, paths);
    }

    public void buildFor(String policy) {
        this.policy = ReportBuildPolicy.valueOf(policy);
    }

    public void jdk(String jdk) {
        this.jdk = jdk;
    }

    public void commandline(String commandline) {
        this.commandline = commandline;
    }

    public void property(String key, String value) {
        properties.add(new PropertyConfig(key, value));
    }

    public void includeProperties(boolean includeProperties) {
        this.includeProperties = includeProperties;
    }
}
