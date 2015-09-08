package ru.yandex.qatools.allure.jenkins.collables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import ru.yandex.qatools.commons.model.Environment;
import ru.yandex.qatools.commons.model.Parameter;

import javax.xml.bind.JAXB;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class CreateEnvironment extends MasterToSlaveFileCallable<FilePath> {

    public static final String ENVIRONMENT_FILE_NAME = "environment.xml";

    private final Map<String, String> parameters;

    private final String id;

    private final String url;

    private final String name;

    public CreateEnvironment(int number, String name, String url, Map<String, String> parameters) {
        this.parameters = parameters;
        this.id = number + "";
        this.name = name;
        this.url = url;
    }

    @Override
    public FilePath invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
        Environment environment = new Environment();
        environment.withId(id).withName(name).withUrl(url);
        for (String key : parameters.keySet()) {
            Parameter parameter = new Parameter();
            parameter.setKey(key);
            parameter.setValue(parameters.get(key));
            environment.getParameter().add(parameter);
        }

        Path environmentPath = Paths.get(file.getAbsolutePath()).resolve(ENVIRONMENT_FILE_NAME);
        if (Files.notExists(environmentPath)) {
            Files.createFile(environmentPath);
        }
        JAXB.marshal(environment, environmentPath.toFile());
        return new FilePath(environmentPath.toFile());
    }

}
