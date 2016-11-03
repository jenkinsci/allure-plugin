package io.qameta.jenkins.dsl;

import io.qameta.jenkins.config.AllureReportConfig;
import io.qameta.jenkins.config.PropertyConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;
import io.qameta.jenkins.config.ResultsConfig;
import javaposse.jobdsl.dsl.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mavlyutov (Marat Mavlutov) <mavlyutov@yandex-team.ru>
 */
class AllureReportPublisherContext implements Context {

    private String jdk;

    private String commandline;

    private List<ResultsConfig> paths;

    private List<PropertyConfig> properties = new ArrayList<>();

    private ReportBuildPolicy policy = ReportBuildPolicy.ALWAYS;

    private boolean includeProperties;

    public AllureReportPublisherContext(List<String> paths) {
        this.paths = paths.stream().map(ResultsConfig::new).collect(Collectors.toList());
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
