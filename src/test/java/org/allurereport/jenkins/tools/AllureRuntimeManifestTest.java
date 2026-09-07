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

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureRuntimeManifestTest {

    private static final String PACKAGE_JSON = "package.json";
    private static final String JSON_ALLURE_DEPENDENCY = "\"allure\": \"";
    private static final String QUOTE = "\"";

    @Test
    public void parsesMajorVersions() {
        assertThat(AllureRuntimeManifest.majorVersion("2.35.1")).isEqualTo(2);
        assertThat(AllureRuntimeManifest.majorVersion("v3.16.0")).isEqualTo(3);
        assertThat(AllureRuntimeManifest.majorVersion("not-a-version"))
                .isEqualTo(AllureRuntimeManifest.UNKNOWN_MAJOR_VERSION);
        assertThat(AllureRuntimeManifest.majorVersion(null))
                .isEqualTo(AllureRuntimeManifest.UNKNOWN_MAJOR_VERSION);
    }

    @Test
    public void bundledRuntimeContainsPinnedAllureCliWithoutPlatformBinLinks() throws Exception {
        final Set<String> entries = new HashSet<>();
        String packageJson = null;
        try (InputStream resource = AllureRuntimeManifestTest.class.getResourceAsStream(
                AllureRuntimeManifest.runtimeResource())) {
            assertThat(resource).as("bundled Allure 3 runtime").isNotNull();
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                ZipEntry entry = zip.getNextEntry();
                while (entry != null) {
                    entries.add(entry.getName());
                    if (PACKAGE_JSON.equals(entry.getName())) {
                        final ByteArrayOutputStream output = new ByteArrayOutputStream();
                        zip.transferTo(output);
                        packageJson = output.toString(StandardCharsets.UTF_8);
                    }
                    zip.closeEntry();
                    entry = zip.getNextEntry();
                }
            }
        }

        assertThat(entries)
                .contains(PACKAGE_JSON, "package-lock.json", "node_modules/allure/cli.js")
                .noneMatch(name -> name.startsWith("node_modules/.bin/"));
        assertThat(packageJson)
                .contains(JSON_ALLURE_DEPENDENCY
                        + AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION + QUOTE);
    }

    @Test
    public void sourceManifestAndMavenBuildUseTheSameRecommendedVersions() throws Exception {
        final String projectDirectory = System.getProperty("basedir");
        final String pom = Files.readString(Paths.get(projectDirectory, "pom.xml"));
        final String packageJson = Files.readString(
                Paths.get(projectDirectory, "src/main/allure3-runtime", PACKAGE_JSON)
        );

        assertThat(pom).contains(
                "<allure2.recommended.version>" + AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION
                        + "</allure2.recommended.version>",
                "<allure3.recommended.version>" + AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION
                        + "</allure3.recommended.version>",
                "<allure3.node.version>" + AllureRuntimeManifest.RECOMMENDED_NODE_VERSION
                        + "</allure3.node.version>"
        );
        assertThat(packageJson).contains(
                "\"version\": \"" + AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION + QUOTE,
                JSON_ALLURE_DEPENDENCY + AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION + QUOTE
        );
    }
}
