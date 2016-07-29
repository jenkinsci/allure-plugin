package ru.yandex.qatools.allure.jenkins.config;

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

}
