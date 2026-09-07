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
package org.allurereport.jenkins.tools;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureCommandlineInstallationTest {

    private static final String TOOL_HOME = "/opt/jenkins-tools/allure";
    private static final String ALLURE_HOME = "ALLURE_HOME";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void managedRecommendationReportsExactVersionWithoutRunningTool() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED
        );
        final AllureCommandlineInstallation installation = installation(installer);
        final Launcher launcher = new Launcher.LocalLauncher(TaskListener.NULL);

        assertThat(installation.getVersion(launcher))
                .isEqualTo(AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION);
        assertThat(installation.getMajorVersion(launcher)).isEqualTo("3");
    }

    @Test
    public void managedAllure2RecommendationReportsExactVersionWithoutRunningTool() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED_ALLURE_2
        );
        final AllureCommandlineInstallation installation = installation(installer);
        final Launcher launcher = new Launcher.LocalLauncher(TaskListener.NULL);

        assertThat(installation.getVersion(launcher))
                .isEqualTo(AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION);
        assertThat(installation.getMajorVersion(launcher)).isEqualTo("2");
    }

    @Test
    public void managedAllure3ExportsVersionedReleaseAsAllureHome() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED
        );
        final AllureCommandlineInstallation installation = installation(installer);
        final EnvVars environment = new EnvVars();

        installation.buildEnvVars(environment);

        assertThat(environment.get(ALLURE_HOME))
                .isEqualTo(TOOL_HOME + "/releases/"
                        + AllureRuntimeManifest.releaseId(AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION));
    }

    @Test
    public void fixedAllure2KeepsConfiguredHome() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_FIXED
        );
        installer.setVersion("2.35.1");
        final AllureCommandlineInstallation installation = installation(installer);
        final EnvVars environment = new EnvVars();

        installation.buildEnvVars(environment);

        assertThat(environment.get(ALLURE_HOME)).isEqualTo(TOOL_HOME);
    }

    @Test
    public void managedAllure3CommandPrefixBypassesBatchLauncher() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED
        );
        final Path toolHome = folder.newFolder("managed-tool").toPath();
        final NodePlatform platform = NodePlatform.detect(
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                false
        );
        final List<String> relativePaths = AllureManagedInstaller.commandRelativePaths(
                AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION,
                platform
        );
        for (String relativePath : relativePaths) {
            final Path commandPart = toolHome.resolve(relativePath);
            Files.createDirectories(commandPart.getParent());
            Files.writeString(commandPart, "fixture");
        }
        final AllureCommandlineInstallation installation = installation(installer, toolHome.toString());

        final List<String> command = installation.getCommandPrefix(
                new Launcher.LocalLauncher(TaskListener.NULL)
        );

        assertThat(command).containsExactly(
                toolHome.resolve(relativePaths.get(0)).toAbsolutePath().toString(),
                toolHome.resolve(relativePaths.get(1)).toAbsolutePath().toString()
        );
        assertThat(command.get(0)).doesNotEndWith(".cmd").doesNotEndWith(".bat");
    }

    private AllureCommandlineInstallation installation(final AllureManagedInstaller installer) throws Exception {
        return installation(installer, TOOL_HOME);
    }

    private AllureCommandlineInstallation installation(final AllureManagedInstaller installer,
                                                        final String home) throws Exception {
        final InstallSourceProperty source = new InstallSourceProperty(Collections.singletonList(installer));
        final List<ToolProperty<?>> properties = Collections.singletonList(source);
        return new AllureCommandlineInstallation("Allure", home, properties);
    }
}
