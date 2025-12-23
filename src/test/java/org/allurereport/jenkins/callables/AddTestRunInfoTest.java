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
package org.allurereport.jenkins.callables;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author charlie (Dmitry Baev).
 */
public class AddTestRunInfoTest {

    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {
    };
    private static final String SOME_BUILD_NAME = "some-build-name";
    private static final String START = "start";
    private static final String STOP = "stop";
    private static final String NAME = "name";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldAddTestRunInfoFile() throws Exception {
        final String name = SOME_BUILD_NAME;
        final int start = 123;
        final int stop = 321;
        final AddTestRunInfo info = new AddTestRunInfo(name, start, stop);
        final FilePath path = info.invoke(folder.newFolder(), null);
        final Path testRunInfo = Paths.get(path.getRemote());

        final ObjectMapper mapper = new ObjectMapper();
        assertThat(testRunInfo).exists();

        try (InputStream is = Files.newInputStream(testRunInfo)) {
            final Map<String, Object> map = mapper.readValue(is, TYPE_REFERENCE);
            assertThat(map).containsEntry(START, start).containsEntry(STOP, stop).containsEntry(NAME, name);
        }
    }

    @Test
    public void shouldNotFailIfAlreadyExists() throws Exception {
        final File dir = folder.newFolder();
        final Path file = dir.toPath().resolve(AddTestRunInfo.TESTRUN_JSON);
        Files.write(file, "Hello".getBytes(StandardCharsets.UTF_8));

        final String name = SOME_BUILD_NAME;
        final int start = 123;
        final int stop = 321;
        final AddTestRunInfo info = new AddTestRunInfo(name, start, stop);
        final FilePath path = info.invoke(dir, null);
        final Path testRunInfo = Paths.get(path.getRemote());

        final ObjectMapper mapper = new ObjectMapper();
        assertThat(testRunInfo).exists();

        try (InputStream is = Files.newInputStream(testRunInfo)) {
            final Map<String, Object> map = mapper.readValue(is, TYPE_REFERENCE);
            assertThat(map).containsEntry(START, start).containsEntry(STOP, stop).containsEntry(NAME, name);
        }
    }
}
