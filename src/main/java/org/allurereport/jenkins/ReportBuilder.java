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
package org.allurereport.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.allurereport.jenkins.tools.AllureCommandlineInstallation;

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

    private FilePath configFilePath;

    public ReportBuilder(final @NonNull Launcher launcher,
                         final @NonNull TaskListener listener,
                         final @NonNull FilePath workspace,
                         final @NonNull EnvVars envVars,
                         final @NonNull AllureCommandlineInstallation commandline) {
        this.workspace = workspace;
        this.launcher = launcher;
        this.listener = listener;
        this.envVars = envVars;
        this.commandline = commandline;
    }

    public void setConfigFilePath(final FilePath configFilePath) {
        this.configFilePath = configFilePath;
    }

    public int build(final @NonNull List<FilePath> resultsPaths,
                     final @NonNull FilePath reportPath) //NOSONAR
            throws IOException, InterruptedException {
        final String version = commandline.getMajorVersion(launcher);
        final ArgumentListBuilder arguments = getArguments(version, resultsPaths, reportPath);

        return launcher.launch().cmds(arguments)
                .envs(envVars).stdout(listener).pwd(workspace).join();
    }

    private ArgumentListBuilder getArguments(final String version,
                                             final @NonNull List<FilePath> resultsPaths,
                                             final @NonNull FilePath reportPath)
            throws IOException, InterruptedException {
        return version.startsWith("2") ? getAllure2Arguments(resultsPaths, reportPath)
                : getAllure1Arguments(resultsPaths, reportPath);
    }

    private ArgumentListBuilder getAllure2Arguments(final @NonNull List<FilePath> resultsPaths,
                                                    final @NonNull FilePath reportPath) //NOSONAR
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
        if (configFilePath != null) {
            arguments.add(CONFIG_OPTION);
            arguments.add(configFilePath.getRemote());
        }
        return arguments;
    }

    private ArgumentListBuilder getAllure1Arguments(final @NonNull List<FilePath> resultsPaths,
                                                    final @NonNull FilePath reportPath) //NOSONAR
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
