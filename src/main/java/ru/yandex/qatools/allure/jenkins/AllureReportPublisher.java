package ru.yandex.qatools.allure.jenkins;

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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import ru.yandex.qatools.allure.jenkins.callables.CreateConfig;
import ru.yandex.qatools.allure.jenkins.callables.CreateEnvironment;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.REPORT_PATH;
import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.getMasterReportFilePath;
import static ru.yandex.qatools.allure.jenkins.utils.FilePathUtils.copyRecursiveTo;
import static ru.yandex.qatools.allure.jenkins.utils.FilePathUtils.deleteRecursive;

/**
 * User: eroshenkoam
 * Date: 10/8/13, 6:20 PM
 * <p/>
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings("unchecked")
public class AllureReportPublisher extends Recorder implements Serializable, MatrixAggregatable {

    private static final long serialVersionUID = 1L;

    private final AllureReportConfig config;

    public static final String ALLURE_PREFIX = "allure";

    public static final String CONFIG_PATH = "config";

    @DataBoundConstructor
    public AllureReportPublisher(AllureReportConfig config) {
        this.config = config;
    }

    public AllureReportConfig getConfig() {
        return config == null ? AllureReportConfig.newInstance(getDescriptor().getResultsPatternDefault()) : config;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singletonList(new AllureProjectAction(project));
    }

    @Override
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        FilePath jobAggregationResults = getAggregationResultDirectory(build);
        for (String resultsPath : getConfig().getResultsPaths()) {
            FilePath resultFilePath = build.getWorkspace().child(resultsPath);
            if (resultFilePath.exists()) {
                copyRecursiveTo(resultFilePath, jobAggregationResults, build, listener.getLogger());
            }
        }

        boolean result = generateReport(jobAggregationResults, build, launcher, listener);


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
            FilePath matrixAggregationResults = getAggregationResultDirectory(parentBuild);
            listener.getLogger().println(String.format("copy matrix build results to directory [%s]",
                    matrixAggregationResults));
            copyRecursiveTo(jobAggregationResults, matrixAggregationResults, parentBuild, listener.getLogger());

        }

        deleteRecursive(jobAggregationResults, listener.getLogger());
        return result;
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {

            @Override
            public boolean endBuild() throws InterruptedException, IOException {

                FilePath matrixAggregationResults = getAggregationResultDirectory(build);
                boolean result = generateReport(matrixAggregationResults, build, launcher, listener);
                deleteRecursive(matrixAggregationResults, listener.getLogger());
                return result;
            }
        };
    }

    private boolean generateReport(FilePath results, AbstractBuild<?, ?> build, Launcher launcher,
                                   BuildListener listener) throws IOException, InterruptedException {

        if (!results.exists() || results.getUsableDiskSpace() == 0) {
            listener.getLogger().println("results directory [%s] is empty");
            return true;
        }

        ReportBuildPolicy reportBuildPolicy = getConfig().getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(build)) {
            listener.getLogger().println(String.format("allure report generation reject by policy [%s]",
                    reportBuildPolicy.getTitle()));
            return true;
        }

        // create environment file
        Map<String, String> buildVars = getConfig().getIncludeProperties() ?
                build.getBuildVariables() : new HashMap<String, String>();
        results.act(new CreateEnvironment(build.getNumber(), build.getFullDisplayName(),
                build.getProject().getAbsoluteUrl(), buildVars));

        // discover commandline
        AllureCommandlineInstallation commandline = getDescriptor().
                getCommandlineInstallation(getConfig().getCommandline());
        commandline = commandline.forNode(Computer.currentComputer().getNode(), listener);
        commandline = commandline.forEnvironment(build.getEnvironment(listener));


        // create config file
        FilePath tmpDirectory = build.getWorkspace().createTempDir(FilePathUtils.ALLURE_PREFIX, null);

        try {
            FilePath configDirectory = tmpDirectory.child(CONFIG_PATH);
            String issuePattern = getDescriptor().getIssuesTrackerPatternDefault();
            String tmsPattern = getDescriptor().getTmsPatternDefault();
            configDirectory.act(new CreateConfig(prepareProperties(issuePattern, tmsPattern)));

            EnvVars buildEnv = build.getEnvironment(listener);
            configureJDK(buildEnv, build.getProject());

            buildEnv.put("ALLURE_HOME", commandline.getHome());
            buildEnv.put("ALLURE_CONFIG", configDirectory.getRemote());

            // create tmp report path
            FilePath reportDirectory = tmpDirectory.child(REPORT_PATH);

            // generate report
            ArgumentListBuilder arguments = new ArgumentListBuilder();
            arguments.add(commandline.getExecutable(launcher));
            arguments.add("generate");
            arguments.addQuoted(results.getRemote());
            arguments.add("-o").addQuoted(reportDirectory.getRemote());
            launcher.launch().cmds(arguments).envs(buildEnv).stdout(listener).pwd(build.getWorkspace()).join();

            // copy report on master
            reportDirectory.copyRecursiveTo(getMasterReportFilePath(build));

            // execute actions for report
            build.addAction(new AllureBuildAction(build));
        } catch (IOException e) {
            listener.getLogger().println(String.format("Report generation failed die error: %s", e.getMessage()));
            return false;
        } finally {
            deleteRecursive(tmpDirectory, listener.getLogger());
        }
        return true;
    }

    private void configureJDK(EnvVars envVars, AbstractProject<?, ?> project) {
        JDK jdk = findJDK(project);
        if (jdk != null && jdk.getExists()) {
            jdk.buildEnvVars(envVars);
        }
    }

    private JDK findJDK(AbstractProject<?, ?> project) {
        if (getConfig().hasJdk()) {
            return Jenkins.getInstance().getJDK(getConfig().getJdk());
        }
        if (project.getJDK() != null) {
            return project.getJDK();
        }
        return null;
    }

    private Properties prepareProperties(String issuePattern, String tmsPattern) {
        Properties properties = new Properties();
        properties.put("allure.issues.tracker.pattern", issuePattern);
        properties.put("allure.tests.management.pattern", tmsPattern);
        return properties;
    }

    private FilePath getAggregationResultDirectory(AbstractBuild<?, ?> build) {
        String curBuildNumber = Integer.toString(build.getNumber());
        return build.getWorkspace().child(ALLURE_PREFIX + curBuildNumber);
    }
}
