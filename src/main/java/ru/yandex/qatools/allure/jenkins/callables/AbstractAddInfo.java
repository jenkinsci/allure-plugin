package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import net.sf.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author charlie (Dmitry Baev).
 */
public abstract class AbstractAddInfo extends MasterToSlaveFileCallable<FilePath> {

    @Override
    public FilePath invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        Path outputDirectory = Paths.get(file.toURI()).toRealPath();
        Files.createDirectories(outputDirectory);
        Path testRun = outputDirectory.resolve(getFileName());
        try (Writer writer = Files.newBufferedWriter(testRun, StandardCharsets.UTF_8)) {
            JSONObject.fromObject(getData())
                    .write(writer)
                    .flush();
        }
        return new FilePath(testRun.toFile());
    }

    protected abstract Object getData();

    protected abstract String getFileName();

}
