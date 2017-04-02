package ru.yandex.qatools.allure.jenkins.callables;

import java.util.HashMap;

/**
 * @author charlie (Dmitry Baev).
 */
public class AddTestRunInfo extends AbstractAddInfo {

    public static final String TESTRUN_JSON = "testrun.json";

    private final String name;

    private final long start;

    private final long stop;

    public AddTestRunInfo(String name, long start, long stop) {
        this.name = name;
        this.start = start;
        this.stop = stop;
    }

    @Override
    protected Object getData() {
        final HashMap<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("start", start);
        data.put("stop", stop);
        return data;
    }

    @Override
    protected String getFileName() {
        return TESTRUN_JSON;
    }
}
