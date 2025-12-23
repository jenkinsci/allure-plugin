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
package org.allurereport.jenkins.testdata;

import hudson.FilePath;
import hudson.model.JDK;
import hudson.scm.SCM;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.allurereport.jenkins.AllureReportPublisher;
import org.allurereport.jenkins.config.ResultsConfig;
import org.allurereport.jenkins.tools.AllureCommandlineInstallation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author charlie (Dmitry Baev).
 */
public final class TestUtils {

    private TestUtils() {
    }

    public static JDK getJdk(JenkinsRule jRule) {
        return jRule.jenkins.getJDKs().get(0);
    }

    public static AllureCommandlineInstallation getAllureCommandline(final JenkinsRule jRule,
                                                                     final TemporaryFolder folder) throws Exception {
        final Path allureHome = folder.newFolder("some spaces in here").toPath();
        final FilePath allure = jRule.jenkins.getRootPath().createTempFile("allure", "zip");
        //noinspection ConstantConditions
        allure.copyFrom(TestUtils.class.getClassLoader().getResource("allure-commandline.zip"));
        allure.unzip(new FilePath(allureHome.toFile()));

        final AllureCommandlineInstallation installation = new AllureCommandlineInstallation(
                "Default", allureHome.toAbsolutePath().toString(), JenkinsRule.NO_PROPERTIES);
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation);
        return installation;
    }

    public static SCM getSimpleFileScm(final String resourceName,
                                       final String path) throws IOException {
        //noinspection ConstantConditions
        return new SingleFileSCM(path, TestUtils.class.getClassLoader().getResource(resourceName));
    }

    public static AllureReportPublisher createAllurePublisher(final String jdk,
                                                              final String commandline,
                                                              final String... resultsPaths) throws Exception {
        final AllureReportPublisher publisher = createAllurePublisherWithoutCommandline(jdk, resultsPaths);
        publisher.setCommandline(commandline);
        return publisher;
    }

    public static AllureReportPublisher createAllurePublisherWithoutCommandline(final String jdk,
                                                                                final String... resultsPaths)
            throws Exception {
        final List<ResultsConfig> results = ResultsConfig.convertPaths(Arrays.asList(resultsPaths));
        final AllureReportPublisher publisher = new AllureReportPublisher(results);
        publisher.setJdk(jdk);
        return publisher;
    }
}
