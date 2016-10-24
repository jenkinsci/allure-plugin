package io.qameta.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.ArgumentListBuilder;
import io.qameta.jenkins.callables.CreateConfig;
import io.qameta.jenkins.callables.CreateEnvironment;
import io.qameta.jenkins.config.AllureReportConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;
import io.qameta.jenkins.tools.AllureCommandlineInstallation;
import io.qameta.jenkins.utils.BuildUtils;
import io.qameta.jenkins.utils.FilePathUtils;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User: eroshenkoam
 * Date: 10/8/13, 6:20 PM
 * <p>
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings("unchecked")
public class AllureReportPublisher extends Recorder implements Serializable, MatrixAggregatable {

    private static final long serialVersionUID = 1L;

    private final AllureReportConfig config;

    public static final String ALLURE_PREFIX = "allure";

    public static final String CONFIG_PATH = "config";

    public static final String ENVIRONMENT_PATH = "environment";

    @DataBoundConstructor
    public AllureReportPublisher(@Nonnull AllureReportConfig config) {
        this.config = config;
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
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singletonList(new AllureReportProjectAction(project));
    }

    @Override
    @Nonnull
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        List<FilePath> resultsPaths = getConfig().getResultsPaths().stream()
                .map(path -> build.getWorkspace().child(path))
                .collect(Collectors.toList());

        boolean result = generateReport(resultsPaths, build, launcher, listener);

        /*
        Its chunk of code copies raw data to matrix build allure dir in order to generate aggregated report.

        It is not possible to move this code to MatrixAggregator->endRun, because endRun executed according
        its triggering queue (despite of the run can be completed so long ago), and by the beginning of
        executing the slave can be off already (for ex. with jclouds plugin).

        It is not possible to make a method like MatrixAggregator->simulatedEndRun and call its from here,
        because AllureReportPublisher is singleton for job, and it can't store state objects to communicate
        between perform and createAggregator, because for concurrent builds (Jenkins provides such feature)
        state objects will be corrupted.
         */
        if (build instanceof MatrixRun) {

            MatrixBuild parentBuild = ((MatrixRun) build).getParentBuild();
            FilePath aggregationResults = getAggregationResultDirectory(parentBuild);
            listener.getLogger().println(String.format("copy matrix build results to directory [%s]",
                    aggregationResults));
            for (FilePath resultsPath : resultsPaths) {
                FilePathUtils.copyRecursiveTo(resultsPath, aggregationResults, parentBuild, listener.getLogger());
            }
        }

        return result;
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {

            @Override
            public boolean endBuild() throws InterruptedException, IOException {

                FilePath results = getAggregationResultDirectory(build);
                boolean result = generateReport(Collections.singletonList(results), build, launcher, listener);
                FilePathUtils.deleteRecursive(results, listener.getLogger());
                return result;
            }
        };
    }

    private boolean generateReport(List<FilePath> resultsPaths, AbstractBuild<?, ?> build, Launcher launcher,
                                   BuildListener listener) throws IOException, InterruptedException {
        ReportBuildPolicy reportBuildPolicy = getConfig().getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(build)) {
            listener.getLogger().println(String.format("allure report generation reject by policy [%s]",
                    reportBuildPolicy.getTitle()));
            return true;
        }

        EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(build, listener);
        // discover commandline
        Optional<AllureCommandlineInstallation> installation =
                getDescriptor().getCommandlineInstallation(config.getCommandline());

        if (!installation.isPresent()) {
            launcher.getListener().getLogger().println("ERROR: Can not find any allure commandline installation.");
            return false;
        }

        copyHistory(resultsPaths, build, listener);

        // prepare environment, config and report paths
        FilePath tmpDirectory = build.getWorkspace().createTempDir(FilePathUtils.ALLURE_PREFIX, null);
        FilePath environmentDirectory = createEnvironment(tmpDirectory, build, buildEnvVars);
        FilePath reportDirectory = tmpDirectory.child("allure-report");

        // configure commandline
        Optional<AllureCommandlineInstallation> tool = BuildUtils.getBuildTool(installation.get(), buildEnvVars, listener);
        if (!tool.isPresent()) {
            launcher.getListener().getLogger().println("ERROR: Can not find allure commandline " +
                    "installation for environment.");
            return false;
        }
        AllureCommandlineInstallation commandline = tool.get();
        configureCommandline(buildEnvVars, launcher, commandline);
        configureJdk(build.getProject(), listener, buildEnvVars);

        // configure arguments
        ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add("generate");
        for (FilePath resultsPath : resultsPaths) {
            arguments.addQuoted(resultsPath.getRemote());
        }
        arguments.addQuoted(environmentDirectory.getRemote());
        arguments.add("-o").addQuoted(reportDirectory.getRemote());

        int exitCode;

        try {
            exitCode = launcher.launch().cmds(arguments).envs(buildEnvVars).stdout(listener)
                    .pwd(build.getWorkspace()).join();
            if (exitCode != 0) {
                return false;
            }
            // copy report on master
            reportDirectory.copyRecursiveTo(new FilePath(new File(build.getRootDir(), "allure-report")));
            // execute actions for report
            build.addAction(new AllureReportBuildBadgeAction(build));
        } catch (IOException e) { //NOSONAR
            listener.getLogger().println("Report generation failed");
            e.printStackTrace(listener.getLogger());  //NOSONAR
            return false;
        } finally {
            FilePathUtils.deleteRecursive(tmpDirectory, listener.getLogger());
        }
        return true;
    }

    private void copyHistory(List<FilePath> resultsPaths, AbstractBuild<?, ?> build, BuildListener listener)
            throws IOException, InterruptedException {
        AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
        if (Objects.isNull(previousBuild)) {
            return;
        }

        FilePath history = new FilePath(new File(previousBuild.getRootDir(), "allure-report/data/history.json"));
        if (history.exists()) {
            resultsPaths.forEach(resultsPath -> {
                try {
                    history.copyTo(new FilePath(resultsPath, "history.json"));
                } catch (IOException | InterruptedException e) {
                    listener.getLogger().println("Can't copy history");
                }
            });
        } else {
            listener.getLogger().println("Can't find history file " + history);
        }
    }

    private FilePath createEnvironment(FilePath tmpDirectory, AbstractBuild<?, ?> build, EnvVars buildEnvVars)
            throws IOException, InterruptedException {
        FilePath environmentDirectory = tmpDirectory.child(ENVIRONMENT_PATH);
        environmentDirectory.act(new CreateEnvironment(
                build.getNumber(),
                build.getFullDisplayName(),
                build.getProject().getAbsoluteUrl(),
                getConfig().getIncludeProperties() ? buildEnvVars : new HashMap<>()
        ));
        return environmentDirectory;
    }

    private FilePath createConfig(FilePath tmpDirectory) throws IOException, InterruptedException {
        return tmpDirectory.child(CONFIG_PATH).act(new CreateConfig(
                getDescriptor().getConfig().getProperties(),
                getConfig().getProperties()
        ));
    }

    /**
     * Configure commandline environment
     */
    private void configureCommandline(EnvVars env, Launcher launcher, AllureCommandlineInstallation commandline)
            throws IOException, InterruptedException {
        env.put("ALLURE_HOME", commandline.getHomeDir(launcher).getAbsolutePath());
    }

    /**
     * Configure java environment variables such as JAVA_HOME.
     */
    private void configureJdk(AbstractProject<?, ?> project, TaskListener listener, EnvVars env)
            throws IOException, InterruptedException {
        JDK jdk = Optional.ofNullable(config.getJdk())
                .map(Jenkins.getInstance()::getJDK)
                .orElse(project.getJDK());
        if (Objects.isNull(jdk) || !jdk.getExists()) {
            return;
        }
        Optional<Node> node = Optional.ofNullable(Computer.currentComputer())
                .map(Computer::getNode);
        if (node.isPresent()) {
            jdk.forNode(node.get(), listener);
        }
        jdk.forEnvironment(env);
    }

    private FilePath getAggregationResultDirectory(AbstractBuild<?, ?> build) {
        String curBuildNumber = Integer.toString(build.getNumber());
        return build.getWorkspace().child(ALLURE_PREFIX + curBuildNumber);
    }
}