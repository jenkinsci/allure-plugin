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
package ru.yandex.qatools.allure.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import ru.yandex.qatools.allure.jenkins.callables.AddExecutorInfo;
import ru.yandex.qatools.allure.jenkins.callables.AddTestRunInfo;
import ru.yandex.qatools.allure.jenkins.callables.FindByGlob;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;
import ru.yandex.qatools.allure.jenkins.exception.AllurePluginException;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;
import ru.yandex.qatools.allure.jenkins.utils.BuildUtils;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;
import ru.yandex.qatools.allure.jenkins.utils.TrueZipArchiver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ru.yandex.qatools.allure.jenkins.utils.ZipUtils.listEntries;

/**
 * User: eroshenkoam.
 * Date: 10/8/13, 6:20 PM
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings({"ClassDataAbstractionCoupling", "ClassFanOutComplexity", "PMD.GodClass"})
public class AllureReportPublisher extends Recorder implements SimpleBuildStep, Serializable, MatrixAggregatable {

    private static final String ALLURE_PREFIX = "allure";
    private static final String ALLURE_SUFFIX = "results";
    private static final String REPORT_ARCHIVE_NAME = "allure-report.zip";

    private AllureReportConfig config;

    private String configPath;

    private String jdk;

    private String commandline;

    private List<PropertyConfig> properties = new ArrayList<>();

    private List<ResultsConfig> results;

    private ReportBuildPolicy reportBuildPolicy;

    private Boolean includeProperties;

    private Boolean disabled;

    private String report;

    @DataBoundConstructor
    public AllureReportPublisher(final @Nonnull List<ResultsConfig> results) {
        this.results = results;
    }

    public List<ResultsConfig> getResults() {
        if (this.results == null && this.config != null) {
            this.results = this.config.getResults();
        }
        return results;
    }

    public boolean isDisabled() {
        return this.disabled == null ? Boolean.FALSE : this.disabled;
    }

    @DataBoundSetter
    public void setDisabled(final boolean disabled) {
        this.disabled = disabled;
    }

    @DataBoundSetter
    public void setConfig(final AllureReportConfig config) {
        this.config = config;
    }

    @DataBoundSetter
    public void setConfigPath(final String configPath) {
        this.configPath = configPath;
    }

    @DataBoundSetter
    public void setJdk(final String jdk) {
        this.jdk = jdk;
    }

    public String getJdk() {
        if (this.jdk == null && this.config != null) {
            this.jdk = this.config.getJdk();
        }
        return this.jdk;
    }

    @DataBoundSetter
    public void setCommandline(final String commandline) {
        this.commandline = commandline;
    }

    public String getCommandline() {
        if (this.commandline == null && this.config != null) {
            this.commandline = this.config.getCommandline();
        }
        return this.commandline;
    }

    private AllureCommandlineInstallation getCommandline(
            final @Nonnull Launcher launcher,
            final @Nonnull TaskListener listener,
            final @Nonnull EnvVars env)
            throws IOException, InterruptedException {

        // discover commandline
        final AllureCommandlineInstallation installation =
                getDescriptor().getCommandlineInstallation(getCommandline());

        if (installation == null) {
            throw new AllurePluginException("Can not find any allure commandline installation.");
        }

        // configure commandline
        final AllureCommandlineInstallation tool = BuildUtils.setUpTool(installation, launcher, listener, env);
        if (tool == null) {
            throw new AllurePluginException("Can not find any allure commandline installation for given environment.");
        }
        return tool;
    }

    @DataBoundSetter
    public void setProperties(final List<PropertyConfig> properties) {
        this.properties = properties;
    }

    public List<PropertyConfig> getProperties() {
        if (this.config != null) {
            this.properties = config.getProperties();
        }
        return this.properties == null ? Collections.<PropertyConfig>emptyList() : properties;
    }

    @DataBoundSetter
    public void setReportBuildPolicy(final ReportBuildPolicy reportBuildPolicy) {
        this.reportBuildPolicy = reportBuildPolicy;
    }

    public ReportBuildPolicy getReportBuildPolicy() {
        if (this.reportBuildPolicy == null && this.config != null) {
            this.reportBuildPolicy = this.config.getReportBuildPolicy();
        }
        return reportBuildPolicy == null ? ReportBuildPolicy.ALWAYS : reportBuildPolicy;
    }

    @DataBoundSetter
    public void setIncludeProperties(final Boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    public Boolean getIncludeProperties() {
        if (this.includeProperties == null && this.config != null) {
            this.includeProperties = this.config.getIncludeProperties();
        }
        return this.includeProperties == null ? Boolean.TRUE : includeProperties;
    }

    @DataBoundSetter
    public void setReport(final String report) {
        this.report = report;
    }

    public String getReport() {
        return this.report == null ? "allure-report" : this.report;
    }

    public String getConfigPath() {
        return StringUtils.isNotBlank(configPath) ? configPath : null;
    }

    @Nonnull
    public AllureReportConfig getConfig() {
        return config;
    }

    @Override
    @Nonnull
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    @Nonnull
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    @Override
    public void perform(final @Nonnull Run<?, ?> run,
                        final @Nonnull FilePath workspace,
                        final @Nonnull Launcher launcher,
                        final @Nonnull TaskListener listener) throws InterruptedException, IOException {
        if (isDisabled()) {
            listener.getLogger().println("Allure report is disabled.");
            return;
        }

        final List<ResultsConfig> resultsConfigs = getResults();
        if (resultsConfigs == null) {
            throw new AllurePluginException("The property 'Results' have to be specified!"
                    + " Check your job's configuration.");
        }
        final List<FilePath> results = new ArrayList<>();
        final EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(run, listener);
        for (final ResultsConfig resultsConfig : resultsConfigs) {
            final String expandedPath = buildEnvVars.expand(resultsConfig.getPath());
            results.addAll(workspace.act(new FindByGlob(expandedPath)));
        }
        prepareResults(results, run, workspace, listener);
        generateReport(results, run, workspace, launcher, listener);
        copyResultsToParentIfNeeded(results, run, listener);
    }

    /**
     * Its chunk of code copies raw data to matrix build allure dir in order to generate aggregated report.
     * <p>
     * It is not possible to move this code to MatrixAggregator->endRun, because endRun executed according
     * its triggering queue (despite of the run can be completed so long ago), and by the beginning of
     * executing the slave can be off already (for ex. with jclouds plugin).
     * <p>
     * It is not possible to make a method like MatrixAggregator->simulatedEndRun and call its from here,
     * because AllureReportPublisher is singleton for job, and it can't store state objects to communicate
     * between perform and createAggregator, because for concurrent builds (Jenkins provides such feature)
     * state objects will be corrupted.
     */
    private void copyResultsToParentIfNeeded(final @Nonnull List<FilePath> results,
                                             final @Nonnull Run<?, ?> run,
                                             final @Nonnull TaskListener listener
    ) throws IOException, InterruptedException {
        if (run instanceof MatrixRun) {
            final MatrixBuild parentBuild = ((MatrixRun) run).getParentBuild();
            final FilePath workspace = parentBuild.getWorkspace();
            if (workspace == null) {
                listener.getLogger().format("Can not find workspace for parent build %s", parentBuild.getDisplayName());
                return;
            }
            final FilePath aggregationDir = workspace.createTempDir(ALLURE_PREFIX, ALLURE_SUFFIX);
            listener.getLogger().format("Copy matrix build results to directory [%s]", aggregationDir);
            for (FilePath resultsPath : results) {
                FilePathUtils.copyRecursiveTo(resultsPath, aggregationDir, parentBuild, listener.getLogger());
            }
        }
    }

    @Override
    public MatrixAggregator createAggregator(final MatrixBuild build,
                                             final Launcher launcher,
                                             final BuildListener listener) {
        final FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            return null;
        }
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                final List<FilePath> resultsPaths = new ArrayList<>();
                for (FilePath directory : workspace.listDirectories()) {
                    if (directory.getName().startsWith(ALLURE_PREFIX) && directory.getName().contains(ALLURE_SUFFIX)) {
                        resultsPaths.add(directory);
                    }
                }
                generateReport(resultsPaths, build, workspace, launcher, listener);
                for (FilePath resultsPath : resultsPaths) {
                    FilePathUtils.deleteRecursive(resultsPath, listener.getLogger());
                }
                return true;
            }
        };
    }

    @SuppressWarnings("TrailingComment")
    private void generateReport(final @Nonnull List<FilePath> resultsPaths,
                                final @Nonnull Run<?, ?> run,
                                final @Nonnull FilePath workspace,
                                final @Nonnull Launcher launcher,
                                final @Nonnull TaskListener listener
    ) throws IOException, InterruptedException { //NOSONAR

        final ReportBuildPolicy reportBuildPolicy = getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(run)) {
            listener.getLogger().printf("Allure report generation reject by policy [%s]%n",
                    reportBuildPolicy.getTitle());
            return;
        }

        final EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(run, listener);
        setAllureProperties(buildEnvVars);
        configureJdk(launcher, listener, buildEnvVars);
        final AllureCommandlineInstallation commandline = getCommandline(launcher, listener, buildEnvVars);

        final FilePath reportPath = workspace.child(getReport());
        final ReportBuilder builder = new ReportBuilder(launcher, listener, workspace, buildEnvVars, commandline);
        if (getConfigPath() != null && workspace.child(getConfigPath()).exists()) {
            final FilePath configFilePath = workspace.child(getConfigPath()).absolutize();
            listener.getLogger().println("Allure config file: " + configFilePath.absolutize());
            builder.setConfigFilePath(configFilePath);
        }
        final int exitCode = builder.build(resultsPaths, reportPath);
        if (exitCode != 0) {
            throw new AllurePluginException("Can not generate Allure Report, exit code: " + exitCode);
        }
        listener.getLogger().println("Allure report was successfully generated.");
        saveAllureArtifact(run, workspace, listener);
        final AllureReportBuildAction buildAction = new AllureReportBuildAction(
                FilePathUtils.extractSummary(run, reportPath.getName()));
        buildAction.setReportPath(reportPath);
        run.addAction(buildAction);
        run.setResult(buildAction.getBuildSummary().getResult());
    }

    private void saveAllureArtifact(final Run<?, ?> run,
                                    final FilePath workspace,
                                    final TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Creating artifact for the build.");
        final File artifactsDir = run.getArtifactsDir();

        Files.createDirectories(artifactsDir.toPath());

        final File archive = new File(artifactsDir, REPORT_ARCHIVE_NAME);
        final File tempArchive = new File(archive.getAbsolutePath() + ".writing.zip");
        final FilePath reportPath = workspace.child(getReport());

        try (OutputStream os = Files.newOutputStream(tempArchive.toPath())) {
            Objects.requireNonNull(reportPath.getParent())
                    .archive(TrueZipArchiver.FACTORY, os, reportPath.getName() + "/**");
        }

        Files.move(tempArchive.toPath(), archive.toPath());
        listener.getLogger().println("Artifact was added to the build.");
    }

    private void setAllureProperties(final EnvVars envVars) {
        final StringBuilder options = new StringBuilder();
        final Map<String, String> properties = new HashMap<>();
        //global properties
        for (PropertyConfig config : getDescriptor().getProperties()) {
            properties.put(config.getKey(), config.getValue());
        }
        //job properties
        for (PropertyConfig config : getProperties()) {
            properties.put(config.getKey(), config.getValue());
        }
        for (Map.Entry<String, String> property : properties.entrySet()) {
            final String value = envVars.expand(property.getValue());
            options.append(String.format("\"-D%s=%s\" ", property.getKey(), value));
        }
        envVars.put("ALLURE_OPTS", options.toString());
    }

    @Nonnull
    @Override
    public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
        return Collections.singleton(new AllureReportProjectAction(
                project
        ));
    }

    private void prepareResults(final @Nonnull List<FilePath> resultsPaths,
                                final @Nonnull Run<?, ?> run,
                                final @Nonnull FilePath workspace,
                                final @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        addHistory(resultsPaths, run, workspace, listener);
        addTestRunInfo(resultsPaths, run);
        addExecutorInfo(resultsPaths, run);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void addTestRunInfo(final @Nonnull List<FilePath> resultsPaths,
                                final @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        final long start = run.getStartTimeInMillis();
        final long stop = run.getTimeInMillis();
        for (FilePath path : resultsPaths) {
            path.act(new AddTestRunInfo(run.getFullDisplayName(), start, stop));
        }
    }

    private void addExecutorInfo(final @Nonnull List<FilePath> resultsPaths,
                                 final @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {

        final String rootUrl = Jenkins.get().getRootUrl();
        final String buildUrl = rootUrl + run.getUrl();
        final String reportUrl = buildUrl + ALLURE_PREFIX;
        final String buildId = run.getId();
        final AddExecutorInfo callable = new AddExecutorInfo(rootUrl, run.getFullDisplayName(), buildUrl, reportUrl,
                buildId);
        for (FilePath path : resultsPaths) {
            path.act(callable);
        }
    }

    private void addHistory(final @Nonnull List<FilePath> resultsPaths,
                            final @Nonnull Run<?, ?> run,
                            final @Nonnull FilePath workspace,
                            final @Nonnull TaskListener listener) {
        try {
            final String reportPath = workspace.child(getReport()).getName();
            final FilePath previousReport = FilePathUtils.getPreviousReportWithHistory(run, reportPath);
            if (previousReport == null) {
                return;
            }
            copyHistoryToResultsPaths(resultsPaths, previousReport, workspace);
        } catch (Exception e) {
            listener.getLogger().println("Cannot find a history information about previous builds.");
            listener.getLogger().println(e);
        }
    }

    private void copyHistoryToResultsPaths(final @Nonnull List<FilePath> resultsPaths,
                                           final @Nonnull FilePath previousReport,
                                           final @Nonnull FilePath workspace)
            throws IOException, InterruptedException {
        try (ZipFile archive = new ZipFile(previousReport.getRemote())) {
            for (FilePath resultsPath : resultsPaths) {
                copyHistoryToResultsPath(archive, resultsPath, workspace);
            }
        }
    }

    private void copyHistoryToResultsPath(final ZipFile archive,
                                          final @Nonnull FilePath resultsPath,
                                          final @Nonnull FilePath workspace)
            throws IOException, InterruptedException {
        final FilePath reportPath = workspace.child(getReport());
        for (final ZipEntry historyEntry : listEntries(archive, reportPath.getName() + "/history")) {
            final String historyFile = historyEntry.getName().replace(reportPath.getName() + "/", "");
            try (InputStream entryStream = archive.getInputStream(historyEntry)) {
                final FilePath historyCopy = resultsPath.child(historyFile);
                historyCopy.copyFrom(entryStream);
            }
        }
    }

    @Nullable
    private JDK getJdkInstallation() {
        return Jenkins.get().getJDK(getJdk());
    }

    /**
     * Configure java environment variables such as JAVA_HOME.
     */
    private void configureJdk(final Launcher launcher,
                              final TaskListener listener,
                              final EnvVars env) throws IOException, InterruptedException {
        final JDK jdk = BuildUtils.setUpTool(getJdkInstallation(), launcher, listener, env);
        if (jdk != null) {
            jdk.buildEnvVars(env);
        }
    }
}
