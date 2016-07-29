package ru.yandex.qatools.allure.jenkins.callables;

import com.google.common.collect.ImmutableMap;
import hudson.FilePath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.yandex.qatools.commons.model.Environment;

import javax.xml.bind.JAXB;
import java.io.File;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author Artem Eroshenko eroshenkoam@yandex-team.ru
 */
public class CreateEnvironmentTest {

    private int buildNumber = 0;

    private String buildName = "test-build";

    private String projectUrl = "http://jenkins.company.com/project";

    private Map<String, String> parameters = ImmutableMap.of("project", "allure");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File environmentFile;

    @Before
    public void invokeCreateEnvironment() throws Exception {
        CreateEnvironment createEnvironment = buildCreateEnvironment();
        FilePath environmentFilePath = createEnvironment.invoke(tmp.newFolder(), null);
        environmentFile = new File(environmentFilePath.getRemote());
    }

    @Test
    public void environmentFileShouldBeCreatedWithParameters() throws Exception {

        assertThat("environment file exists", environmentFile.exists(), is(true));

        Environment environment = JAXB.unmarshal(environmentFile, Environment.class);

        assertThat(environment.getId(), equalTo(Integer.toString(buildNumber)));
        assertThat(environment.getName(), equalTo(buildName));
        assertThat(environment.getUrl(), startsWith(projectUrl));

        assertThat(environment.getParameter(), hasSize(parameters.size()));
    }

    private CreateEnvironment buildCreateEnvironment() {
        return new CreateEnvironment(buildNumber, buildName, projectUrl, parameters);
    }
}
