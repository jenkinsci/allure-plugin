package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import ru.yandex.qatools.commons.model.Environment;
import ru.yandex.qatools.commons.model.Parameter;

import javax.xml.bind.JAXB;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class CreateEnvironment extends MasterToSlaveFileCallable<FilePath> {

    public static final String ENVIRONMENT_FILE_NAME = "environment.xml";

    private final Map<String, String> parameters; //NOSONAR

    private final String projectUrl;

    private final String id;

    private final String name;

    public CreateEnvironment(int number, String name, String projectUrl, Map<String, String> parameters) {
        this.parameters = parameters;
        this.projectUrl = projectUrl;
        this.id = Integer.toString(number);
        this.name = name;
    }

    @Override
    public FilePath invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
        Environment environment = new Environment();
        environment.withId(id).withName(name).withUrl(getAbsoluteUrl());
        for (String key : parameters.keySet()) {
            Parameter parameter = new Parameter();
            parameter.setKey(key);
            parameter.setValue(parameters.get(key));
            environment.getParameter().add(parameter);
        }

        Path environmentPath = Paths.get(file.getAbsolutePath()).resolve(ENVIRONMENT_FILE_NAME);

        if (Files.notExists(environmentPath.getParent())) {
            Files.createDirectories(environmentPath.getParent());
        }

        if (Files.notExists(environmentPath)) {
            Files.createFile(environmentPath);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(environmentPath, Charset.forName("UTF-8"))) {
            JAXB.marshal(environment, writer);
        }
        return new FilePath(environmentPath.toFile());
    }

    private String getAbsoluteUrl() {
        return String.format("%s%s/", projectUrl, id);
    }
}
