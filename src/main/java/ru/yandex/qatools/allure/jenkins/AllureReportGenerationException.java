package ru.yandex.qatools.allure.jenkins;

public class AllureReportGenerationException extends Exception {
    public AllureReportGenerationException(String message) {
        super(message);
    }

    public AllureReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
