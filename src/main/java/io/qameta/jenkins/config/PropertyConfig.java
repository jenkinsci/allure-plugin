package io.qameta.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * @author eroshenkoam (Artem Eroshenko)
 */
public class PropertyConfig implements Serializable {

    private final String key;

    private final String value;

    @DataBoundConstructor
    public PropertyConfig(String key, String value) {
        this.value = value;
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
