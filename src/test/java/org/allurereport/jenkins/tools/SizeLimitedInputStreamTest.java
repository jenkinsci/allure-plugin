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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SizeLimitedInputStreamTest {

    private static final long TEST_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
    private static final String TEST_ARCHIVE = "Test archive";

    @Test
    public void readsContentWithinByteLimit() throws Exception {
        final byte[] content = "archive".getBytes(StandardCharsets.UTF_8);
        final SizeLimitedInputStream input = new SizeLimitedInputStream(
                new ByteArrayInputStream(content),
                content.length,
                TEST_TIMEOUT,
                TEST_ARCHIVE
        );

        assertThat(input.readAllBytes()).containsExactly(content);
        assertThat(input.getBytesRead()).isEqualTo(content.length);
    }

    @Test
    public void rejectsContentBeyondByteLimit() {
        final byte[] content = "oversized".getBytes(StandardCharsets.UTF_8);
        final SizeLimitedInputStream input = new SizeLimitedInputStream(
                new ByteArrayInputStream(content),
                3,
                TEST_TIMEOUT,
                TEST_ARCHIVE
        );

        assertThatThrownBy(input::readAllBytes)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exceeds the limit of 3 bytes");
    }

    @Test
    public void rejectsReadsAfterTheTotalTimeLimit() throws Exception {
        final SizeLimitedInputStream input = new SizeLimitedInputStream(
                new ByteArrayInputStream(new byte[]{1}),
                1,
                1,
                TEST_ARCHIVE
        );
        Thread.sleep(10);

        assertThatThrownBy(input::read)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exceeded the total time limit");
    }
}
