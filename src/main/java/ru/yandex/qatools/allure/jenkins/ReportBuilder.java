package ru.yandex.qatools.allure.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * @author charlie (Dmitry Baev).
 */
@SuppressWarnings("TrailingComment")
public class ReportBuilder {

    private static final String GENERATE_COMMAND = "generate";
    private static final String OUTPUT_DIR_OPTION = "-o";
    private static final String CLEAN_OPTION = "-c";
    private static final String CONFIG_OPTION = "--config";

    private final FilePath workspace;

    private final Launcher launcher;

    private final TaskListener listener;

    private final EnvVars envVars;

    private final AllureCommandlineInstallation commandline;

    public ReportBuilder(@Nonnull Launcher launcher, @Nonnull TaskListener listener, @Nonnull FilePath workspace,
                         @Nonnull EnvVars envVars, @Nonnull AllureCommandlineInstallation commandline) {
        this.workspace = workspace;
        this.launcher = launcher;
        this.listener = listener;
        this.envVars = envVars;
        this.commandline = commandline;
    }

    public int build(@Nonnull List<FilePath> resultsPaths, @Nonnull FilePath reportPath,
                     FilePath configPath) //NOSONAR
            throws IOException, InterruptedException {
        final String version = commandline.getMajorVersion(launcher);
        final ArgumentListBuilder arguments = getArguments(version, resultsPaths, reportPath, configPath);



        return launcher.launch().cmds(arguments)
                .envs(envVars).stdout(listener).pwd(workspace).join();
    }

    private ArgumentListBuilder getArguments(String version, @Nonnull List<FilePath> resultsPaths,
                                             @Nonnull FilePath reportPath, FilePath configPath)
            throws IOException, InterruptedException {
        return version.startsWith("2") ? getAllure2Arguments(resultsPaths, reportPath, configPath)
                : getAllure1Arguments(resultsPaths, reportPath);
    }

    private ArgumentListBuilder getAllure2Arguments(@Nonnull List<FilePath> resultsPaths,
                                                    @Nonnull FilePath reportPath,
                                                    FilePath configPath) //NOSONAR
            throws IOException, InterruptedException {
        final ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add(GENERATE_COMMAND);
        for (FilePath resultsPath : resultsPaths) {
            arguments.add(resultsPath.getRemote());
        }
        arguments.add(CLEAN_OPTION);
        arguments.add(OUTPUT_DIR_OPTION);
        arguments.add(reportPath.getRemote());
        if (configPath != null) {
            arguments.add(CONFIG_OPTION);
            arguments.add(configPath.getRemote());
        }
        return arguments;
    }

    private ArgumentListBuilder getAllure1Arguments(@Nonnull List<FilePath> resultsPaths,
                                                    @Nonnull FilePath reportPath) //NOSONAR
            throws IOException, InterruptedException {
        final ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add(GENERATE_COMMAND);
        for (FilePath resultsPath : resultsPaths) {
            arguments.addQuoted(resultsPath.getRemote());
        }
        arguments.add(OUTPUT_DIR_OPTION);
        arguments.addQuoted(reportPath.getRemote());
        return arguments;
    }

}
