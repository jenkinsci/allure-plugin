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
package org.allurereport.jenkins.utils;

import java.util.Locale;

/**
 * Selects the response CSP for files served from an Allure report.
 *
 * <p>Report entrypoints are intentionally detected as any {@code index.html}
 * file, including plugin entrypoints such as {@code awesome/index.html} and
 * {@code playwright-trace-viewer/index.html}. Other active documents are
 * treated as untrusted attachment-like content and get a response sandbox so
 * direct navigation cannot execute them as Jenkins-origin pages.
 */
public final class ReportContentSecurityPolicy {

    private static final String INDEX_HTML = "index.html";
    private static final String SLASH = "/";
    private static final String SANDBOXED_ACTIVE_CONTENT_CSP =
            "sandbox allow-scripts; base-uri 'none'; form-action 'none'; "
                    + "object-src 'none'";
    private static final String SVG_SUFFIX = ".svg";
    private static final String HTM_SUFFIX = ".htm";
    private static final String HTML_SUFFIX = ".html";
    private static final String XHTML_SUFFIX = ".xhtml";

    private ReportContentSecurityPolicy() {
    }

    public static String forPath(final String relativePath) {
        return isSandboxedActiveContent(relativePath) ? SANDBOXED_ACTIVE_CONTENT_CSP : "";
    }

    private static boolean isSandboxedActiveContent(final String relativePath) {
        final String lowerCasePath = stripLeadingSlash(relativePath).toLowerCase(Locale.ROOT);
        return isActiveDocument(lowerCasePath) && !isIndexHtml(lowerCasePath);
    }

    private static boolean isActiveDocument(final String path) {
        return path.endsWith(HTML_SUFFIX)
                || path.endsWith(HTM_SUFFIX)
                || path.endsWith(XHTML_SUFFIX)
                || path.endsWith(SVG_SUFFIX);
    }

    private static boolean isIndexHtml(final String path) {
        return INDEX_HTML.equals(path) || path.endsWith(SLASH + INDEX_HTML);
    }

    private static String stripLeadingSlash(final String path) {
        String stripped = path == null ? "" : path.replace('\\', '/');
        while (stripped.startsWith(SLASH)) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }
}
