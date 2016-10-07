package ru.yandex.qatools.allure.jenkins.exceptions;

/**
 * Created by lawyard on 07.10.16.
 *
 *  Common exception for allure uploaders
 *  Use it when catching exceptions different from IOException and InterruptedException
 */
public class AllureUploadException extends Exception {

    public AllureUploadException() {}

    public AllureUploadException(String message) {
        super(message);
    }
}