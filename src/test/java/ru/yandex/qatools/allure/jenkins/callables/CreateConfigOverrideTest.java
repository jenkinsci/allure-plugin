package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Artem Eroshenko eroshenkoam@yandex-team.ru
 */
public class CreateConfigOverrideTest {

    public static final String DEFAULT_KEY = "key";

    public static final String DEFAULT_VALUE = "value";

    public static final String OVERRIDDEN_VALUE = "overridden";

    private File configFile;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void invokeCreateConfig() throws Exception {
        List<PropertyConfig> jobProperties = Arrays.asList(new PropertyConfig(DEFAULT_KEY, DEFAULT_VALUE));
        List<PropertyConfig> globalProperties = Arrays.asList(new PropertyConfig(DEFAULT_KEY, OVERRIDDEN_VALUE));
        CreateConfig createConfig = new CreateConfig(jobProperties, globalProperties);
        FilePath configFilePath = createConfig.invoke(tmp.newFolder(), null);
        configFile = new File(configFilePath.getRemote());
    }

    @Test
    public void jobPropertiesShouldBeOverriddenByGlobalProperties() throws Exception {
        assertThat("config file exists", configFile.exists(), is(true));

        Properties actualProperties = readProperties(configFile);
        assertThat(actualProperties.getProperty(DEFAULT_KEY), is(OVERRIDDEN_VALUE));
    }


    private Properties readProperties(File file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file.toURI()), Charset.forName("UTF-8"))) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }

}
