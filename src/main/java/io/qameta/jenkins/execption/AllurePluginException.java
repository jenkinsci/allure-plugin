package io.qameta.jenkins.execption;

/**
 * @author charlie (Dmitry Baev).
 */
public class AllurePluginException extends RuntimeException {

    public AllurePluginException(String message) {
        super(message);
    }
}
