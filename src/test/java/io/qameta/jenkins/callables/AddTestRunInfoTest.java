package io.qameta.jenkins.callables;

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static ru.yandex.qatools.matchers.nio.PathMatchers.exists;

/**
 * @author charlie (Dmitry Baev).
 */
public class AddTestRunInfoTest {

    public static final TypeReference TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {
    };

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldAddTestRunInfoFile() throws Exception {
        String name = "some-build-name";
        int start = 123;
        int stop = 321;
        AddTestRunInfo info = new AddTestRunInfo(name, start, stop);
        FilePath path = info.invoke(folder.newFolder(), null);
        Path testRunInfo = Paths.get(path.getRemote());

        ObjectMapper mapper = new ObjectMapper();
        assertThat(testRunInfo, exists());
        try (InputStream is = Files.newInputStream(testRunInfo)) {
            Map<String, Object> map = mapper.readValue(is, TYPE_REFERENCE);
            assertThat(map, hasEntry("name", name));
            assertThat(map, hasEntry("start", start));
            assertThat(map, hasEntry("stop", stop));
        }
    }

    @Test
    public void shouldNotFailIfAlreadyExists() throws Exception {
        File dir = folder.newFolder();
        Path file = dir.toPath().resolve(AddTestRunInfo.TESTRUN_JSON);
        Files.write(file, "Hello".getBytes(StandardCharsets.UTF_8));

        String name = "some-build-name";
        int start = 123;
        int stop = 321;
        AddTestRunInfo info = new AddTestRunInfo(name, start, stop);
        FilePath path = info.invoke(dir, null);
        Path testRunInfo = Paths.get(path.getRemote());

        ObjectMapper mapper = new ObjectMapper();
        assertThat(testRunInfo, exists());
        try (InputStream is = Files.newInputStream(testRunInfo)) {
            Map<String, Object> map = mapper.readValue(is, TYPE_REFERENCE);
            assertThat(map, hasEntry("name", name));
            assertThat(map, hasEntry("start", start));
            assertThat(map, hasEntry("stop", stop));
        }
    }
}
