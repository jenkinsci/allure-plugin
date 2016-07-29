package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Artem Eroshenko eroshenkoam@yandex-team.ru
 */
public class CreateConfigTest {

    private File configFile;

    private Properties expectedProperties = new Properties() {{
        put("key", "value");
    }};

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void invokeCreateConfig() throws Exception {
        CreateConfig createConfig = new CreateConfig(expectedProperties);
        FilePath configFilePath = createConfig.invoke(tmp.newFolder(), null);
        configFile = new File(configFilePath.getRemote());
    }

    @Test
    public void configFileShouldCreatedWithParameters() throws Exception {
        assertThat("config file exists", configFile.exists(), is(true));

        Properties actualProperties = readProperties(configFile);
        assertThat(actualProperties.size(), is(expectedProperties.size()));
    }


    private Properties readProperties(File file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file.toURI()), Charset.forName("UTF-8"))) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }

}
