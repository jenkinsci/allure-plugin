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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import ru.yandex.qatools.allure.jenkins.callables.AddExecutorInfo;
import ru.yandex.qatools.allure.jenkins.callables.AddTestRunInfo;
import ru.yandex.qatools.allure.jenkins.callables.AllureReportArchive;
import ru.yandex.qatools.allure.jenkins.callables.FindByGlob;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.config.ResultPolicy;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;
import ru.yandex.qatools.allure.jenkins.exception.AllurePluginException;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;
import ru.yandex.qatools.allure.jenkins.utils.BuildSummary;
import ru.yandex.qatools.allure.jenkins.utils.BuildUtils;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ru.yandex.qatools.allure.jenkins.callables.AllureReportArchive.REPORT_DIRECTORY_NOT_FOUND;
import static ru.yandex.qatools.allure.jenkins.utils.ZipUtils.listEntries;

/**
 * User: eroshenkoam.
 * Date: 10/8/13, 6:20 PM
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings({"ClassDataAbstractionCoupling", "ClassFanOutComplexity", "PMD.GodClass", "PMD.TooManyMethods"})
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

    private String reportName;

    private ResultPolicy resultPolicy;

    @Nullable
    private Integer unstableThresholdPercent;
    @Nullable
    private Integer failureThresholdPercent;
    @Nullable
    private Integer unstableThresholdCount;
    @Nullable
    private Integer failureThresholdCount;

    @DataBoundSetter
    public void setResultPolicy(final ResultPolicy resultPolicy) {
        this.resultPolicy = resultPolicy;
    }

    public ResultPolicy getResultPolicy() {
        return this.resultPolicy == null
            ? ResultPolicy.UNSTABLE_IF_FAILED_OR_BROKEN
            : this.resultPolicy;
    }

    @DataBoundSetter
    public void setUnstableThresholdPercent(final Integer value) {
        this.unstableThresholdPercent = value;
    }

    @DataBoundSetter
    public void setFailureThresholdPercent(final Integer value) {
        this.failureThresholdPercent = value;
    }

    @DataBoundSetter
    public void setUnstableThresholdCount(final Integer value) {
        this.unstableThresholdCount = value;
    }

    @DataBoundSetter
    public void setFailureThresholdCount(final Integer value) {
        this.failureThresholdCount = value;
    }

    @Nullable public Integer getUnstableThresholdPercent() {
        return unstableThresholdPercent;
    }
    @Nullable public Integer getFailureThresholdPercent() {
        return failureThresholdPercent;
    }
    @Nullable public Integer getUnstableThresholdCount() {
        return unstableThresholdCount;
    }
    @Nullable public Integer getFailureThresholdCount() {
        return failureThresholdCount;
    }

    @DataBoundConstructor
    public AllureReportPublisher(final @NonNull List<ResultsConfig> results) {
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
        final @NonNull Launcher launcher,
        final @NonNull TaskListener listener,
        final @NonNull EnvVars env)
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

    @DataBoundSetter
    public void setReportName(final String reportName) {
        if (StringUtils.isBlank(reportName)) {
            this.reportName = null;
        } else {
            this.reportName = reportName.trim();
        }
    }

    @Nullable
    public String getReportName() {
        return this.reportName;
    }

    @NonNull
    public AllureReportConfig getConfig() {
        return config;
    }

    @Override
    @NonNull
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    @NonNull
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    @Override
    public void perform(final @NonNull Run<?, ?> run,
        final @NonNull FilePath workspace,
        final @NonNull EnvVars env,
        final @NonNull Launcher launcher,
        final @NonNull TaskListener listener) throws InterruptedException, IOException {
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

        for (final ResultsConfig resultsConfig : resultsConfigs) {
            final String expandedPath = env.expand(resultsConfig.getPath());
            results.addAll(workspace.act(new FindByGlob(expandedPath)));
        }
        prepareResults(results, run, workspace, listener);
        generateReport(results, run, workspace, env, launcher, listener);
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
    private void copyResultsToParentIfNeeded(final @NonNull List<FilePath> results,
        final @NonNull Run<?, ?> run,
        final @NonNull TaskListener listener
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

                final EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(build, listener);
                generateReport(resultsPaths, build, workspace, buildEnvVars, launcher, listener);
                for (FilePath resultsPath : resultsPaths) {
                    FilePathUtils.deleteRecursive(resultsPath, listener.getLogger());
                }
                return true;
            }
        };
    }

    @SuppressWarnings({"TrailingComment", "PMD.NcssCount"})
    private void generateReport(
        final @NonNull List<FilePath> resultsPaths,
        final @NonNull Run<?, ?> run,
        final @NonNull FilePath workspace,
        final @NonNull EnvVars env,
        final @NonNull Launcher launcher,
        final @NonNull TaskListener listener
    ) throws IOException, InterruptedException {
        final ReportBuildPolicy reportBuildPolicy = getReportBuildPolicy();

        if (!reportBuildPolicy.isNeedToBuildReport(run)) {
            listener.getLogger().printf("Allure report generation reject by policy [%s]%n",
                reportBuildPolicy.getTitle());
            return;
        }

        setAllureProperties(env);
        configureJdk(launcher, listener, env);
        final AllureCommandlineInstallation commandline = getCommandline(launcher, listener, env);

        final String reportDirPath = getReport();
        final FilePath reportDirectoryInWorkspace = workspace.child(reportDirPath);

        final ReportBuilder builder = new ReportBuilder(launcher, listener, workspace, env, commandline);
        if (getConfigPath() != null && workspace.child(getConfigPath()).exists()) {
            final FilePath configFilePath = workspace.child(getConfigPath()).absolutize();
            listener.getLogger().println("Allure config file: " + configFilePath.absolutize());
            builder.setConfigFilePath(configFilePath);
        }

        final int exitCode = builder.build(resultsPaths, reportDirectoryInWorkspace);
        if (exitCode != 0) {
            throw new AllurePluginException("Can not generate Allure Report, exit code: " + exitCode);
        }
        listener.getLogger().println("Allure report was successfully generated.");

        saveAllureArtifact(run, workspace, listener, launcher);

        final String reportName = reportDirectoryInWorkspace.getName();

        final FilePath reportUnderBuild = new FilePath(run.getRootDir()).child(reportName);

        final AllureReportBuildAction buildAction = new AllureReportBuildAction(
            FilePathUtils.extractSummary(run, reportName)
        );
        buildAction.setReportPath(reportUnderBuild);
        run.addAction(buildAction);
        applyResultStatus(run, buildAction.getBuildSummary());
    }

    private void applyResultStatus(final Run<?, ?> run, final BuildSummary summary) {
        final Result thresholdResult = decideByThresholds(summary);
        if (thresholdResult != null) {
            run.setResult(thresholdResult);
            return;
        }
        final Result target = getResultPolicy().decide(summary);
        if (target != null) {
            run.setResult(target);
        }
    }

    private void saveAllureArtifact(final Run<?, ?> run,
        final FilePath workspace,
        final TaskListener listener,
        final Launcher launcher) throws IOException, InterruptedException {
        listener.getLogger().println("Archiving Allure report via ArtifactManager…");

        final String reportDirPath = getReport();
        final FilePath reportPathWs = workspace.child(reportDirPath);

        if (!reportPathWs.exists()) {
            listener.error(REPORT_DIRECTORY_NOT_FOUND + reportPathWs.getRemote());
            return;
        }

        final String reportName = reportPathWs.getName();

        workspace.act(new AllureReportArchive(reportDirPath, REPORT_ARCHIVE_NAME));

        final Map<String, String> artifacts =
            Collections.singletonMap(REPORT_ARCHIVE_NAME, REPORT_ARCHIVE_NAME);

        final BuildListener buildListener =
            (listener instanceof BuildListener) ? (BuildListener) listener : new BuildListenerAdapter(listener);

        run.pickArtifactManager().archive(workspace, launcher, buildListener, artifacts);
        listener.getLogger().println("Allure artifact archived via ArtifactManager.");

        final FilePath zipPath = workspace.child(REPORT_ARCHIVE_NAME);
        if (zipPath.exists()) {
            zipPath.delete();
        }

        final FilePath reportUnderBuild = new FilePath(run.getRootDir()).child(reportName);
        if (reportUnderBuild.exists()) {
            reportUnderBuild.deleteRecursive();
        }
        reportPathWs.copyRecursiveTo(reportUnderBuild);
        listener.getLogger().println("Allure report copied to: " + reportUnderBuild.getRemote());
    }

    private void setAllureProperties(final EnvVars envVars) {
        final StringBuilder options = new StringBuilder();
        final Map<String, String> properties = new HashMap<>();
        for (PropertyConfig config : getDescriptor().getProperties()) {
            properties.put(config.getKey(), config.getValue());
        }
        for (PropertyConfig config : getProperties()) {
            properties.put(config.getKey(), config.getValue());
        }
        for (Map.Entry<String, String> property : properties.entrySet()) {
            final String value = envVars.expand(property.getValue());
            options.append(String.format("\"-D%s=%s\" ", property.getKey(), value));
        }
        envVars.put("ALLURE_OPTS", options.toString());
    }

    @NonNull
    @Override
    public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
        return Collections.singleton(new AllureReportProjectAction(
            project
        ));
    }

    private void prepareResults(final @NonNull List<FilePath> resultsPaths,
        final @NonNull Run<?, ?> run,
        final @NonNull FilePath workspace,
        final @NonNull TaskListener listener)
        throws IOException, InterruptedException {
        addHistory(resultsPaths, run, workspace, listener);
        addTestRunInfo(resultsPaths, run);
        addExecutorInfo(resultsPaths, run);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void addTestRunInfo(final @NonNull List<FilePath> resultsPaths,
        final @NonNull Run<?, ?> run)
        throws IOException, InterruptedException {
        final long start = run.getStartTimeInMillis();
        final long stop = run.getTimeInMillis();
        for (FilePath path : resultsPaths) {
            path.act(new AddTestRunInfo(run.getFullDisplayName(), start, stop));
        }
    }

    private void addExecutorInfo(final @NonNull List<FilePath> resultsPaths,
        final @NonNull Run<?, ?> run)
        throws IOException, InterruptedException {

        final String rootUrl = Jenkins.get().getRootUrl();
        final String buildUrl = rootUrl + run.getUrl();
        final String reportUrl = buildUrl + ALLURE_PREFIX;
        final String buildId = run.getId();

        final String effectiveReportName =
            StringUtils.isNotBlank(getReportName()) ? getReportName() : "AllureReport";

        final AddExecutorInfo callable = new AddExecutorInfo(
            rootUrl,
            run.getFullDisplayName(),
            buildUrl,
            reportUrl,
            buildId,
            effectiveReportName
        );

        for (FilePath path : resultsPaths) {
            path.act(callable);
        }
    }

    private void addHistory(final @NonNull List<FilePath> resultsPaths,
        final @NonNull Run<?, ?> run,
        final @NonNull FilePath workspace,
        final @NonNull TaskListener listener) {
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

    private void copyHistoryToResultsPaths(final @NonNull List<FilePath> resultsPaths,
        final @NonNull FilePath previousReport,
        final @NonNull FilePath workspace)
        throws IOException, InterruptedException {
        try (ZipFile archive = new ZipFile(previousReport.getRemote())) {
            for (FilePath resultsPath : resultsPaths) {
                copyHistoryToResultsPath(archive, resultsPath, workspace);
            }
        }
    }

    private void copyHistoryToResultsPath(final ZipFile archive,
        final @NonNull FilePath resultsPath,
        final @NonNull FilePath workspace)
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

    @Nullable
    private Result decideByThresholds(final @NonNull BuildSummary summary) {
        if (failureThresholdCount == null
            && failureThresholdPercent == null
            && unstableThresholdCount == null
            && unstableThresholdPercent == null) {
            return null;
        }

        final long problems = summary.getFailedCount() + summary.getBrokenCount();
        final long total = problems + summary.getPassedCount() + summary.getSkipCount() + summary.getUnknownCount();
        final double ratio = total > 0 ? (problems * 100.0d) / (double) total : 0.0d;

        final Result byCount = evaluateThresholdPair(problems, failureThresholdCount, unstableThresholdCount);
        final Result byPercent = evaluateThresholdPair(ratio, failureThresholdPercent, unstableThresholdPercent);
        return worstResult(byCount, byPercent);
    }

    @Nullable
    private static Result evaluateThresholdPair(final long actualValue,
        @Nullable final Integer failureThreshold,
        @Nullable final Integer unstableThreshold) {
        if (failureThreshold != null && actualValue >= failureThreshold) {
            return Result.FAILURE;
        }
        if (unstableThreshold != null && actualValue >= unstableThreshold) {
            return Result.UNSTABLE;
        }
        return null;
    }

    @Nullable
    private static Result evaluateThresholdPair(final double actualPercent,
        @Nullable final Integer failureThresholdPercentValue,
        @Nullable final Integer unstableThresholdPercentValue) {
        if (failureThresholdPercentValue != null && actualPercent >= failureThresholdPercentValue) {
            return Result.FAILURE;
        }
        if (unstableThresholdPercentValue != null && actualPercent >= unstableThresholdPercentValue) {
            return Result.UNSTABLE;
        }
        return null;
    }

    @Nullable
    private static Result worstResult(@Nullable final Result firstResult, @Nullable final Result secondResult) {
        if (Result.FAILURE.equals(firstResult) || Result.FAILURE.equals(secondResult)) {
            return Result.FAILURE;
        }
        if (Result.UNSTABLE.equals(firstResult) || Result.UNSTABLE.equals(secondResult)) {
            return Result.UNSTABLE;
        }
        return null;
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
