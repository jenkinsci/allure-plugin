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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NodePlatformTest {

    private static final String LINUX = "Linux";
    private static final String AMD64 = "amd64";
    private static final String AARCH64 = "aarch64";
    private static final String NODE_ARCHIVE_PREFIX = "node-v";

    @Test
    public void detectsLinuxX64() throws Exception {
        assertThat(NodePlatform.detect(LINUX, AMD64, false)).isEqualTo(NodePlatform.LINUX_X64);
        assertThat(NodePlatform.LINUX_X64.archiveFileName())
                .isEqualTo(NODE_ARCHIVE_PREFIX
                        + AllureRuntimeManifest.RECOMMENDED_NODE_VERSION + "-linux-x64.tar.gz");
    }

    @Test
    public void detectsLinuxArm64() throws Exception {
        assertThat(NodePlatform.detect(LINUX, AARCH64, false)).isEqualTo(NodePlatform.LINUX_ARM64);
    }

    @Test
    public void detectsMacX64() throws Exception {
        assertThat(NodePlatform.detect("Mac OS X", "x86_64", false)).isEqualTo(NodePlatform.DARWIN_X64);
    }

    @Test
    public void detectsMacArm64() throws Exception {
        assertThat(NodePlatform.detect("Darwin", "arm64", false)).isEqualTo(NodePlatform.DARWIN_ARM64);
    }

    @Test
    public void detectsWindowsX64() throws Exception {
        assertThat(NodePlatform.detect("Windows Server 2025", AMD64, false))
                .isEqualTo(NodePlatform.WINDOWS_X64);
        assertThat(NodePlatform.WINDOWS_X64.archiveFileName())
                .isEqualTo(NODE_ARCHIVE_PREFIX
                        + AllureRuntimeManifest.RECOMMENDED_NODE_VERSION + "-win-x64.zip");
    }

    @Test
    public void detectsWindowsArm64() throws Exception {
        assertThat(NodePlatform.detect("Windows 11", AARCH64, false)).isEqualTo(NodePlatform.WINDOWS_ARM64);
    }

    @Test
    public void rejectsMuslLinux() {
        assertThatThrownBy(() -> NodePlatform.detect(LINUX, AMD64, true))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("musl-based agents")
                .hasMessageContaining("existing Allure installation");
    }

    @Test
    public void rejectsUnsupportedArchitecture() {
        assertThatThrownBy(() -> NodePlatform.detect(LINUX, "s390x", false))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Linux/s390x");
    }

    @Test
    public void everyArchiveHasPinnedSha256() {
        assertThat(NodePlatform.values())
                .allSatisfy(platform -> assertThat(platform.getSha256())
                        .matches("[0-9a-f]{64}"));
    }

    @Test
    public void everyArchiveHasPinnedPositiveSize() {
        assertThat(NodePlatform.values())
                .allSatisfy(platform -> assertThat(platform.getArchiveSize()).isPositive());
    }
}
