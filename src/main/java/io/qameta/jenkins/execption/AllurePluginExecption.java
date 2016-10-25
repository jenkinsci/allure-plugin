package io.qameta.jenkins.execption;

/**
 * @author charlie (Dmitry Baev).
 */
public class AllurePluginExecption extends RuntimeException {

    public AllurePluginExecption(String message) {
        super(message);
    }
}
