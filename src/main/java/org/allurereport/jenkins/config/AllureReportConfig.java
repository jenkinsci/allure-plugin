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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * eroshenkoam.
 * 30/07/14
 */
@SuppressWarnings("PMD.UseObjectForClearerAPI")
public class AllureReportConfig implements Serializable {

    private String jdk;

    private String commandline;

    /**
     * @deprecated Please, someone write why is this deprecated?
     */
    @Deprecated
    private String resultsPattern;

    private List<PropertyConfig> properties = new ArrayList<>();

    private List<ResultsConfig> results;

    private ReportBuildPolicy reportBuildPolicy = ReportBuildPolicy.ALWAYS;

    private Boolean includeProperties = Boolean.TRUE;

    @SuppressWarnings({"unused", "PMD.SingularField"})
    private String configPath = "";

    @DataBoundConstructor
    public AllureReportConfig(final List<ResultsConfig> results) {
        this.results = results == null ? Collections.<ResultsConfig>emptyList() : results;
    }

    @DataBoundSetter
    public void setJdk(final String jdk) {
        this.jdk = jdk;
    }

    public String getJdk() {
        return jdk;
    }

    @DataBoundSetter
    public void setCommandline(final String commandline) {
        this.commandline = commandline;
    }

    public String getCommandline() {
        return commandline;
    }

    @NonNull
    @SuppressWarnings("deprecation")
    public List<ResultsConfig> getResults() {
        if (StringUtils.isNotBlank(this.resultsPattern)) {
            this.results = convertPaths(this.resultsPattern);
            this.resultsPattern = null;
        }
        return results;
    }

    public List<PropertyConfig> getProperties() {
        return this.properties;
    }

    @DataBoundSetter
    public void setProperties(final List<PropertyConfig> properties) {
        this.properties = properties;
    }

    public ReportBuildPolicy getReportBuildPolicy() {
        return this.reportBuildPolicy;
    }

    @DataBoundSetter
    public void setReportBuildPolicy(final ReportBuildPolicy reportBuildPolicy) {
        this.reportBuildPolicy = reportBuildPolicy;
    }

    @DataBoundSetter
    public void setIncludeProperties(final Boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    @DataBoundSetter
    public void setConfigPath(final String configPath) {
        this.configPath = configPath;
    }

    public Boolean getIncludeProperties() {
        return includeProperties;
    }

    public static AllureReportConfig newInstance(final List<String> results) {
        return newInstance(null, null, null, results.toArray(new String[]{}));
    }

    public static AllureReportConfig newInstance(final String jdk,
                                                 final String commandline,
                                                 final String configPath,
                                                 final String... paths) {
        return newInstance(jdk, commandline, configPath, Arrays.asList(paths));
    }

    private static AllureReportConfig newInstance(final String jdk,
                                                  final String commandline,
                                                  final String configPath,
                                                  final List<String> paths) {
        final List<ResultsConfig> results = convertPaths(paths);
        final AllureReportConfig config = new AllureReportConfig(results);
        config.setJdk(jdk);
        config.setCommandline(commandline);
        config.setIncludeProperties(true);
        config.setConfigPath(configPath);
        return config;
    }

    private static List<ResultsConfig> convertPaths(final String paths) {
        return convertPaths(Arrays.asList(paths.split("\\n")));
    }

    private static List<ResultsConfig> convertPaths(final List<String> paths) {
        return paths.stream()
                .map(ResultsConfig::new)
                .collect(Collectors.toList());
    }
}
