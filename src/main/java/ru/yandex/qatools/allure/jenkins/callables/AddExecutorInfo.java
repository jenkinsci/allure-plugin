package ru.yandex.qatools.allure.jenkins.callables;

import java.util.HashMap;

/**
 * @author charlie (Dmitry Baev).
 */
public class AddExecutorInfo extends AbstractAddInfo {

    private static final String EXECUTOR_JSON = "executor.json";

    private final String url;

    private final String buildName;

    private final String buildUrl;

    private final String reportUrl;

    public AddExecutorInfo(String url, String buildName, String buildUrl, String reportUrl) {
        this.url = url;
        this.buildName = buildName;
        this.buildUrl = buildUrl;
        this.reportUrl = reportUrl;
    }

    @Override
    protected Object getData() {
        final HashMap<String, Object> data = new HashMap<>();
        data.put("name", "Jenkins");
        data.put("type", "jenkins");
        data.put("url", url);
        data.put("buildName", buildName);
        data.put("buildUrl", buildUrl);
        data.put("reportUrl", reportUrl);
        return data;
    }

    @Override
    protected String getFileName() {
        return EXECUTOR_JSON;
    }
}
