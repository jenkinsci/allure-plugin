package ru.yandex.qatools.allure.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.apache.commons.lang.Validate;
import ru.yandex.qatools.allure.jenkins.callables.CreateConfig;
import ru.yandex.qatools.allure.jenkins.callables.CreateEnvironment;
import ru.yandex.qatools.allure.jenkins.config.AllureGlobalConfig;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.REPORT_PATH;
import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.getMasterReportFilePath;
import static ru.yandex.qatools.allure.jenkins.utils.FilePathUtils.deleteRecursive;

@SuppressWarnings("WeakerAccess")
public class ReportGenerator {
    private static final String CONFIG_PATH = "config";
    private static final String ENVIRONMENT_PATH = "environment";

    private final AllureReportConfig config;
    private final FilePath workspace;
    private final Run<?, ?> run;
    private final Launcher launcher;
    private final TaskListener listener;
    private final List<FilePath> resultsPaths;

    private ReportGenerator(AllureReportConfig config, FilePath workspace, Run<?, ?> run, Launcher launcher, TaskListener listener, List<FilePath> resultsPaths) {
        this.config = config;
        this.workspace = workspace;
        this.run = run;
        this.launcher = launcher;
        this.listener = listener;
        this.resultsPaths = resultsPaths;
    }

    private boolean invalidBuildPolicy(ReportBuildPolicy reportBuildPolicy) {
        if (!reportBuildPolicy.isNeedToBuildReport(run)) {
            listener.getLogger().printf(
                    "allure report generation reject by policy [%s]%n",
                    reportBuildPolicy.getTitle()
            );
            return true;
        }
        return false;
    }

    private boolean invalidCommandLine(AllureCommandlineInstallation commandline) {
        if (commandline == null) {
            launcher.getListener().getLogger().println("ERROR: Can not find any allure commandline installation.");
            return true;
        }
        return false;
    }

    private FilePath createTmpDirectory() throws AllureReportGenerationException {
        FilePath tmpDirectory;
        try {
            tmpDirectory = workspace.createTempDir(FilePathUtils.ALLURE_PREFIX, null);
        } catch (Exception e) {
            throw new AllureReportGenerationException("Failed to create tmp directory for report", e);
        }
        return tmpDirectory;
    }

    private AllureCommandlineInstallation setupCommandLine(AllureCommandlineInstallation commandline) throws IOException, InterruptedException {
        return commandline
                .forNode(run.getExecutor().getOwner().getNode(), listener)
                .forEnvironment(run.getEnvironment(listener));
    }

    private Map<String, String> getBuildVariables() {
        if (config.getIncludeProperties() && run instanceof AbstractBuild<?, ?>)
            return ((AbstractBuild<?, ?>) run).getBuildVariables();
        else
            return Collections.emptyMap();
    }

    private String getProjectUrl() {
        if (run instanceof AbstractBuild<?, ?>)
            return ((AbstractBuild) run).getProject().getAbsoluteUrl();
        else
            return run.getParent().getAbsoluteUrl();
    }

    private FilePath getEnvironmentDirectory(FilePath tmpDirectory) throws IOException, InterruptedException {
        FilePath environmentDirectory = tmpDirectory.child(ENVIRONMENT_PATH);
        Map<String, String> buildVars = getBuildVariables();
        environmentDirectory.act(
                new CreateEnvironment(
                        run.getNumber(),
                        run.getFullDisplayName(),
                        getProjectUrl(),
                        buildVars
                )
        );
        return environmentDirectory;
    }

    private void configureJDK(EnvVars envVars, Job<?, ?> project) {
        JDK jdk = findJDK(project);
        if (jdk != null && jdk.getExists()) {
            jdk.buildEnvVars(envVars);
        }
    }

    private JDK findJDK(Job<?, ?> project) {
        if (config.hasJdk()) {
            return Jenkins.getInstance().getJDK(config.getJdk());
        }

        if (project instanceof AbstractProject<?, ?>) {
            return ((AbstractProject<?, ?>) project).getJDK();
        }

        return null;
    }

    private EnvVars setupBuildEnv(AllureCommandlineInstallation commandline, FilePath configFile) throws IOException, InterruptedException {
        EnvVars buildEnv = run.getEnvironment(listener);
        configureJDK(buildEnv, run.getParent());

        buildEnv.put("ALLURE_HOME", commandline.getHome());
        buildEnv.put("ALLURE_CONFIG", configFile.getRemote());
        return buildEnv;
    }

    private ArgumentListBuilder setupArguments(AllureCommandlineInstallation commandline, FilePath tmpDirectory, FilePath reportDirectory) throws IOException, InterruptedException {
        // create environment file
        FilePath environmentDirectory = getEnvironmentDirectory(tmpDirectory);

        // generate report
        ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add("generate");

        for (FilePath resultsPath : resultsPaths) {
            arguments.addQuoted(resultsPath.getRemote());
        }
        arguments.addQuoted(environmentDirectory.getRemote());

        arguments.add("-o").addQuoted(reportDirectory.getRemote());
        return arguments;
    }

    private EnvVars getBuildEnv(AllureCommandlineInstallation commandline, FilePath tmpDirectory) throws IOException, InterruptedException {
        FilePath configFile = tmpDirectory
                .child(CONFIG_PATH)
                .act(new CreateConfig(AllureGlobalConfig.newInstance().getProperties(), config.getProperties()));
        return setupBuildEnv(commandline, configFile);
    }

    private AllureCommandlineInstallation getCommandlineInstallation(String name) {
        List<AllureCommandlineInstallation> installations = AllureCommandlineInstallation.getCommandlineInstallations();

        for (AllureCommandlineInstallation installation : installations) {
            if (installation.getName().equals(name)) {
                return installation;
            }
        }
        // If no installation match then take the first one
        if (!installations.isEmpty()) {
            return installations.get(0);
        }

        return null;
    }

    private void doGenerate(AllureCommandlineInstallation commandline, FilePath tmpDirectory) throws IOException, InterruptedException, AllureReportGenerationException {
        // create tmp report path
        FilePath reportDirectory = tmpDirectory.child(REPORT_PATH);
        ArgumentListBuilder arguments = setupArguments(commandline, tmpDirectory, reportDirectory);
        EnvVars buildEnv = getBuildEnv(commandline, tmpDirectory);

        int exitCode = launcher
                .launch()
                .cmds(arguments)
                .envs(buildEnv)
                .stdout(listener)
                .pwd(workspace)
                .join();

        if (exitCode != 0)
            throw new AllureReportGenerationException("Allure commandline exit code: " + exitCode);

        // copy report on master
        FilePath reportFilePath = getMasterReportFilePath(run);
        if (reportFilePath != null)
            reportDirectory.copyRecursiveTo(reportFilePath);
        // execute actions for report
        run.addAction(new AllureBuildAction(run));
    }

    public void generateReport() throws AllureReportGenerationException {
        ReportBuildPolicy reportBuildPolicy = config.getReportBuildPolicy();
        if (invalidBuildPolicy(reportBuildPolicy))
            return ;

        // discover commandline
        AllureCommandlineInstallation commandline = getCommandlineInstallation(config.getCommandline());
        if (invalidCommandLine(commandline))
            return ;

        // create config file
        FilePath tmpDirectory = createTmpDirectory();

        try {
            doGenerate(setupCommandLine(commandline), tmpDirectory);
        } catch (AllureReportGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new AllureReportGenerationException("Failed to generate Allure report", e);
        } finally {
            deleteRecursive(tmpDirectory, listener.getLogger());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AllureReportConfig config;
        private FilePath workspace;
        private Run<?, ?> run;
        private Launcher launcher;
        private TaskListener listener;

        private Builder() {
        }

        public Builder config(AllureReportConfig config) {
            this.config = config;
            return this;
        }

        public Builder workspace(FilePath workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder run(Run<?, ?> run) {
            this.run = run;
            return this;
        }

        public Builder launcher(Launcher launcher) {
            this.launcher = launcher;
            return this;
        }

        public Builder listener(TaskListener listener) {
            this.listener = listener;
            return this;
        }

        public ReportGenerator createGenerator(List<FilePath> resultsPaths) {
            Validate.notEmpty(resultsPaths);
            Validate.notNull(config);
            Validate.notNull(workspace);
            Validate.notNull(run);
            Validate.notNull(launcher);
            Validate.notNull(listener);

            return new ReportGenerator(config, workspace, run, launcher, listener, resultsPaths);
        }
    }
}
