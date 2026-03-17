/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class AllureVersionService {

    private static final Logger LOGGER = Logger.getLogger(AllureVersionService.class.getName());
    private static final String GITHUB_API =
            "https://api.github.com/repos/allure-framework/allure2/releases/latest";

    public static final String FALLBACK_VERSION = "2.30.0";
    
    private static final Duration CACHE_DURATION = Duration.ofHours(24);
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10_000;
    
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?(\\+[a-zA-Z0-9.]+)?$");

    private static final AtomicReference<CacheEntry> CACHE = new AtomicReference<>();

    private AllureVersionService() {
    }

    public static String getLatestStableVersion() {
        final CacheEntry entry = CACHE.get();
        if (entry != null && entry.isValid()) {
            LOGGER.fine("Using cached version: " + entry.version);
            return entry.version;
        }

        try {
            final String version = fetchFromGitHub();

            CACHE.set(new CacheEntry(version, Instant.now()));

            LOGGER.info("Fetched latest stable version from GitHub: " + version);
            return version;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch latest version from GitHub: " + e.getMessage(), e);

            if (entry != null) {
                LOGGER.info("Using stale cached version: " + entry.version);
                return entry.version;
            }

            LOGGER.info("Using fallback version: " + FALLBACK_VERSION);
            return FALLBACK_VERSION;
        }
    }

    @SuppressWarnings("PMD.NcssCount")
    private static String fetchFromGitHub() throws IOException {
        final URL url = new URL(GITHUB_API);

        final Proxy proxy = getJenkinsProxy(url);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);

        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "Jenkins-Allure-Plugin");

            final int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                final String rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining");
                if ("0".equals(rateLimitRemaining)) {
                    throw new IOException("GitHub API rate limit exceeded. Please try again later.");
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub API returned status code: " + responseCode);
            }

            try (InputStream inputStream = connection.getInputStream()) {
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(inputStream);

                final boolean prerelease = root.path("prerelease").asBoolean(false);
                if (prerelease) {
                    throw new IOException("Latest release is a pre-release");
                }

                final String tagName = root.path("tag_name").asText();
                if (tagName.isEmpty()) {
                    throw new IOException("tag_name not found in GitHub API response");
                }

                final String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                
                if (!SEMVER_PATTERN.matcher(version).matches()) {
                    throw new IOException("Invalid version format from GitHub API: " + version);
                }
                
                return version;
            }
        } finally {
            connection.disconnect();
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static Proxy getJenkinsProxy(final URL url) {
        try {
            final hudson.ProxyConfiguration proxyConfig = hudson.ProxyConfiguration.load();
            if (proxyConfig != null && proxyConfig.name != null) {
                final String host = url.getHost();
                final List<Pattern> noProxyPatterns = proxyConfig.getNoProxyHostPatterns();
                
                if (noProxyPatterns == null || noProxyPatterns.stream()
                        .filter(Objects::nonNull)
                        .noneMatch(pattern -> pattern.matcher(host).matches())) {
                    return new Proxy(
                        Proxy.Type.HTTP,
                        new InetSocketAddress(proxyConfig.name, proxyConfig.port)
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get Jenkins proxy configuration: " + e.getMessage(), e);
        }
        return Proxy.NO_PROXY;
    }

    static void clearCache() {
        CACHE.set(null);
    }

    private static final class CacheEntry {
        private final String version;
        private final Instant time;

        CacheEntry(final String version, final Instant time) {
            this.version = version;
            this.time = time;
        }

        boolean isValid() {
            return Duration.between(time, Instant.now()).compareTo(CACHE_DURATION) < 0;
        }
    }
}
