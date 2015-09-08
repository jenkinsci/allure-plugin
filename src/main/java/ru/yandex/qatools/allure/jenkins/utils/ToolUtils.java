package ru.yandex.qatools.allure.jenkins.utils;

import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.JDK;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class ToolUtils {

    public static final String JAVA_HOME = "JAVA_HOME";

    private ToolUtils() {
    }

    public static JDK findJava(String name, Map<String, String> envVars, AbstractProject<?, ?> project) {
        if (isNotBlank(name)) {
            return Jenkins.getInstance().getJDK(name);
        }

        if (project.getJDK() != null) {
            return project.getJDK();
        }

        if (envVars.containsKey(JAVA_HOME)) {
            return new JDK(JDK.DEFAULT_NAME, envVars.get(JAVA_HOME));
        }

        return null;
    }

    public static File findJavaExecutable(String name, Map<String, String> envVars,
                                          AbstractProject<?, ?> project, BuildListener listener)
            throws IOException, InterruptedException {
        JDK jdk = findJava(name, envVars, project);
        if (jdk == null) {
            throw new IOException("Can not find jdk");
        }
        jdk.forNode(Computer.currentComputer().getNode(), listener);
        if (!jdk.getExists()) {
            throw new IOException("Bad jdk in path: " + jdk.getHome());
        }
        return new File(jdk.getBinDir(), File.separatorChar == 92 ? "java.exe" : "java");
    }

}
