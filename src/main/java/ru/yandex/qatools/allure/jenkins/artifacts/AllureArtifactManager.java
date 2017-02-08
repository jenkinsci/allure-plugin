package ru.yandex.qatools.allure.jenkins.artifacts;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Run;
import jenkins.model.StandardArtifactManager;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author ehborisov
 */
public class AllureArtifactManager extends StandardArtifactManager {

    public AllureArtifactManager(Run<?,?> build) {
        super(build);
    }

    @Override
    public void archive(FilePath workspace, Launcher launcher, BuildListener listener,
                        final Map<String, String> artifacts) throws IOException, InterruptedException {
        File artifactsDir = build.getArtifactsDir();
        for(Map.Entry<String, String> entry : artifacts.entrySet()){
            workspace.child(entry.getValue()).copyTo(new FilePath(artifactsDir).child(entry.getKey()));
        }
    }
}
