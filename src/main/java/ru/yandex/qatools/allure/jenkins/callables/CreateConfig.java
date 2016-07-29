package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import ru.yandex.qatools.allure.jenkins.Messages;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class CreateConfig extends MasterToSlaveFileCallable<FilePath> {

    public static final String CONFIG_FILE_NAME = "allure.properties";

    private Properties properties;

    public CreateConfig(Properties properties) {
        this.properties = properties;
    }

    public CreateConfig(List<PropertyConfig> globalProperties, List<PropertyConfig> jobProperties) {
        this.properties = merge(globalProperties, jobProperties);
    }

    @Override
    public FilePath invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
        Path configPath = Paths.get(file.getAbsolutePath()).resolve(CONFIG_FILE_NAME);
        if (Files.notExists(configPath.getParent())) {
            Files.createDirectories(configPath.getParent());
        }
        if (Files.notExists(configPath)) {
            Files.createFile(configPath);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(configPath, Charset.forName("UTF-8"))) {
            properties.store(writer, Messages.CreateConfig_Comment());
        }
        return new FilePath(configPath.toFile());
    }

    private Properties merge(List<PropertyConfig> first, List<PropertyConfig> second) {
        Properties propertiesToMerge = new Properties();
        propertiesToMerge.putAll(convert(first));
        propertiesToMerge.putAll(convert(second));
        return propertiesToMerge;
    }

    private Properties convert(List<PropertyConfig> propertyConfigs) {
        Properties result = new Properties();
        for (PropertyConfig propertyConfig : propertyConfigs) {
            result.put(propertyConfig.getKey(), propertyConfig.getValue());
        }
        return result;
    }
}
