package ru.yandex.qatools.allure.jenkins.config;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Artem Eroshenko eroshenkoam@yandex-team.ru
 */
public class AllureGlobalConfig {

    private List<PropertyConfig> properties;

    public List<PropertyConfig> getProperties() {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        return properties;
    }

    public void setProperties(List<PropertyConfig> properties) {
        this.properties = properties;
    }

    public static AllureGlobalConfig newInstance() {
        return new AllureGlobalConfig();
    }
}
