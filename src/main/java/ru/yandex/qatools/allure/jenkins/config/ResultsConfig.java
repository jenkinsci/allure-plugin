package ru.yandex.qatools.allure.jenkins.config;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * eroshenkoam.
 * 25/12/16
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

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(path).toHashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof ResultsConfig) {
            final ResultsConfig other = (ResultsConfig) object;
            return new EqualsBuilder().append(path, other.path).isEquals();
        } else {
            return false;
        }
    }

}
