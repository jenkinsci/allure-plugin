package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.tools.ant.DirectoryScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * eroshenkoam
 * 26.06.17
 */
public class FindByGlob extends MasterToSlaveFileCallable<List<FilePath>> {

    private final String includes;

    public FindByGlob(String includes) {
        this.includes = includes;
    }

    @Override
    public List<FilePath> invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        FilePath root = new FilePath(file);

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(file);
        scanner.setIncludes(new String[]{includes});
        scanner.setCaseSensitive(false);
        scanner.scan();

        String[] paths = scanner.getIncludedDirectories();
        List<FilePath> files = new ArrayList<>();
        for (String path : paths) {
            files.add(root.child(path));
        }
        return files;
    }

}
