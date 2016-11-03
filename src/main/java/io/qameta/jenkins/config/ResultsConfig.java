package io.qameta.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
public class ResultsConfig implements Serializable {

    private final String path;

    @DataBoundConstructor
    public ResultsConfig(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
