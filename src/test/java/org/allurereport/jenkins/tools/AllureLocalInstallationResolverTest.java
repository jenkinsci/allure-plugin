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

import hudson.Functions;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureLocalInstallationResolverTest {

    private static final String BIN_ALLURE = "bin/allure";
    private static final String UNIX_SCRIPT = "#!/bin/sh\n";
    private static final String NPM_PACKAGE_JSON = "lib/node_modules/allure/package.json";
    private static final String INVALID_ROOT = "invalid-root";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resolvesNpmGlobalPrefix() throws Exception {
        Assume.assumeFalse(Functions.isWindows());
        final File prefix = folder.newFolder("npm-prefix");
        final File executable = createFile(prefix, BIN_ALLURE, UNIX_SCRIPT);
        createFile(
                prefix,
                NPM_PACKAGE_JSON,
                "{\"name\":\"allure\",\"version\":\"3.16.0\"}"
        );

        assertThat(AllureLocalInstallationResolver.executable(prefix.getAbsolutePath()))
                .isEqualTo(executable.toPath());
        assertThat(AllureLocalInstallationResolver.metadataVersion(prefix.getAbsolutePath()))
                .isEqualTo("3.16.0");
    }

    @Test
    public void resolvesNpmPrefixFromItsBinDirectory() throws Exception {
        Assume.assumeFalse(Functions.isWindows());
        final File prefix = folder.newFolder("npm-prefix-from-bin");
        final File executable = createFile(prefix, BIN_ALLURE, UNIX_SCRIPT);
        createFile(
                prefix,
                NPM_PACKAGE_JSON,
                "{\"name\":\"allure\",\"version\":\"3.15.1\"}"
        );

        assertThat(AllureLocalInstallationResolver.executable(executable.getParent()))
                .isEqualTo(executable.toPath());
        assertThat(AllureLocalInstallationResolver.metadataVersion(executable.getParent()))
                .isEqualTo("3.15.1");
    }

    @Test
    public void resolvesNestedClassicDistribution() throws Exception {
        Assume.assumeFalse(Functions.isWindows());
        final File root = folder.newFolder("classic-root");
        final File distribution = new File(root, "allure-2.35.1");
        final File executable = createFile(distribution, BIN_ALLURE, UNIX_SCRIPT);
        createFile(distribution, "lib/allure-commandline-2.35.1.jar", "");

        assertThat(AllureLocalInstallationResolver.executable(root.getAbsolutePath()))
                .isEqualTo(executable.toPath());
        assertThat(AllureLocalInstallationResolver.metadataVersion(root.getAbsolutePath()))
                .isEqualTo("2.35.1");
    }

    @Test
    public void ignoresAllureNamedDirectoryWithoutExecutable() throws Exception {
        final File root = folder.newFolder(INVALID_ROOT);
        folder.newFolder(INVALID_ROOT, "allure-results");

        assertThat(AllureLocalInstallationResolver.executable(root.getAbsolutePath())).isNull();
    }

    private File createFile(final File root, final String relativePath, final String content) throws Exception {
        final File file = new File(root, relativePath);
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }
}
