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

import hudson.Extension;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginPackagingTest {

    @Test
    public void shouldDeclareMatrixProjectAsOptionalPluginDependency() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/the.hpl")) {
            assertThat(input).as("plugin test manifest").isNotNull();

            final Manifest manifest = new Manifest(input);
            final String dependencies = manifest.getMainAttributes().getValue("Plugin-Dependencies");

            assertThat(dependencies).isNotBlank();

            final String matrixProjectDependency = Arrays.stream(dependencies.split(","))
                .map(String::trim)
                .filter(dependency -> dependency.startsWith("matrix-project:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("matrix-project dependency is missing"));

            assertThat(matrixProjectDependency).contains("resolution:=optional");
        }
    }

    @Test
    public void shouldKeepMainPublisherFreeFromMatrixTypes() {
        assertThat(AllureReportPublisher.class.getInterfaces())
            .extracting(Class::getName)
            .doesNotContain("hudson.matrix.MatrixAggregatable");

        assertThat(Stream.of(AllureReportPublisher.class.getDeclaredMethods())
            .flatMap(method -> Stream.concat(
                Stream.of(method.getReturnType()),
                Stream.of(method.getParameterTypes())
            ))
            .map(Class::getName))
            .noneMatch(type -> type.startsWith("hudson.matrix."));
    }

    @Test
    public void shouldMarkMatrixExtensionAsOptional() {
        final Extension extension = AllureMatrixAggregatable.class.getAnnotation(Extension.class);

        assertThat(extension).isNotNull();
        assertThat(extension.optional()).isTrue();
    }
}
