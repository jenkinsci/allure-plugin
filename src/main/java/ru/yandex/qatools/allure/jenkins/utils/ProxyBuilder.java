package ru.yandex.qatools.allure.jenkins.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.settings.Proxy;

import java.util.Arrays;
import java.util.List;

import static ru.yandex.qatools.clay.maven.settings.FluentProxyBuilder.newProxy;

/**
 * eroshenkoam
 * 25/12/14
 */
public class ProxyBuilder {

    public static String HTTP = "http";

    public static String HTTP_PROXY_HOST = "http.proxyHost";

    public static String HTTP_PROXY_PORT = "http.proxyPort";

    public static String HTTP_PROXY_USER = "http.proxyUser";

    public static String HTTP_PROXY_PASSWORD = "http.proxyPassword";

    public static String HTTP_NON_PROXY_HOSTS = "http.nonProxyHosts";

    public static String HTTPS = "https";

    public static String HTTPS_PROXY_HOST = "https.proxyHost";

    public static String HTTPS_PROXY_PORT = "https.proxyPort";

    public static String HTTPS_PROXY_USER = "https.proxyUser";

    public static String HTTPS_PROXY_PASSWORD = "https.proxyPassword";

    public static String HTTPS_NON_PROXY_HOSTS = "https.nonProxyHosts";

    public static Proxy loadHttpProxySettings() {
        Proxy proxy = newProxy()
                .withId("allure-http")
                .withProtocol(HTTP)
                .withHost(System.getProperty(HTTP_PROXY_HOST))
                .withPort(Integer.parseInt(System.getProperty(HTTP_PROXY_PORT, "0")))
                .withUsername(System.getProperty(HTTP_PROXY_USER))
                .withPassword(System.getProperty(HTTP_PROXY_PASSWORD))
                .withNonProxyHosts(System.getProperty(HTTP_NON_PROXY_HOSTS))
                .build();
        proxy.setActive(StringUtils.isNotEmpty(proxy.getHost()));
        return proxy;
    }

    public static Proxy loadHttpsProxySettings() {
        Proxy proxy = newProxy()
                .withId("allure-https")
                .withProtocol(HTTPS)
                .withHost(System.getProperty(HTTPS_PROXY_HOST))
                .withPort(Integer.parseInt(System.getProperty(HTTPS_PROXY_PORT, "0")))
                .withUsername(System.getProperty(HTTPS_PROXY_USER))
                .withPassword(System.getProperty(HTTPS_PROXY_PASSWORD))
                .withNonProxyHosts(System.getProperty(HTTPS_NON_PROXY_HOSTS))
                .build();
        proxy.setActive(StringUtils.isNotEmpty(proxy.getHost()));
        return proxy;
    }

}
