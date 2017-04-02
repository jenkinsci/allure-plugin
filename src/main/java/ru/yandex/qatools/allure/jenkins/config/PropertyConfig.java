package ru.yandex.qatools.allure.jenkins.config;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * @author Artem Eroshenko eroshenkoam@yandex-team.ru
 */
public class PropertyConfig implements Serializable {

    private String key;

    private String value;

    @DataBoundConstructor
    public PropertyConfig(String key, String value) {
        this.value = value;
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(key).append(value).toHashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof PropertyConfig) {
            final PropertyConfig other = (PropertyConfig) object;
            return new EqualsBuilder().append(key, other.key).append(value, other.value).isEquals();
        } else {
            return false;
        }
    }

}
