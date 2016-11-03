package io.qameta.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author eroshenkoam (Artem Eroshenko)
 */
public class AllureGlobalConfig implements Serializable {

    private final List<PropertyConfig> properties;

    @DataBoundConstructor
    public AllureGlobalConfig(List<PropertyConfig> properties) {
        this.properties = Objects.isNull(properties) ? Collections.emptyList() : properties;
    }

    public List<PropertyConfig> getProperties() {
        return properties;
    }

    public static AllureGlobalConfig newInstance() {
        return new AllureGlobalConfig(Collections.emptyList());
    }
}
