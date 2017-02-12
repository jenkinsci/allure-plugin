package ru.yandex.qatools.allure.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author charlie (Dmitry Baev).
 */
public class ReportBuilder {

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

    public int build(@Nonnull List<FilePath> resultsPaths, @Nonnull FilePath reportPath) //NOSONAR
            throws IOException, InterruptedException {
        String version = getVersion();
        ArgumentListBuilder arguments = getArguments(version, resultsPaths, reportPath);

        return launcher.launch().cmds(arguments)
                .envs(envVars).stdout(listener).pwd(workspace).join();
    }

    private ArgumentListBuilder getArguments(String version, @Nonnull List<FilePath> resultsPaths, @Nonnull FilePath reportPath)
            throws IOException, InterruptedException {
        return version.startsWith("2") ? getAllure2Arguments(resultsPaths, reportPath) :
                getAllure1Arguments(resultsPaths, reportPath);
    }

    private ArgumentListBuilder getAllure2Arguments(@Nonnull List<FilePath> resultsPaths, @Nonnull FilePath reportPath) //NOSONAR
            throws IOException, InterruptedException {
        ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add("generate");
        for (FilePath resultsPath : resultsPaths) {
            arguments.add(resultsPath.getRemote());
        }
        arguments.add("-o");
        arguments.add(reportPath.getRemote());
        return arguments;
    }

    private ArgumentListBuilder getAllure1Arguments(@Nonnull List<FilePath> resultsPaths, @Nonnull FilePath reportPath) //NOSONAR
            throws IOException, InterruptedException {
        ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add("generate");
        for (FilePath resultsPath : resultsPaths) {
            arguments.addQuoted(resultsPath.getRemote());
        }
        arguments.add("-o");
        arguments.addQuoted(reportPath.getRemote());
        return arguments;
    }

    private String getVersion() throws IOException, InterruptedException {
        ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add("version");
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            launcher.launch().cmds(arguments).stdout(stream).pwd(workspace).join();
            String version = new String(stream.toByteArray(), Charset.forName("UTF-8"));
            listener.getLogger().format("Allure version: %s", version);
            return version;
        }
    }

}
