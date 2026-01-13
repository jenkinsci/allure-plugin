(function () {
    'use strict';

    var PANEL_ID = 'pw-trace-fallback-panel';
    var PANEL_DISMISSED_STORAGE_KEY = 'pw_trace_fallback_panel_dismissed';

    var TRACE_IFRAME_ID = 'pw-trace-iframe';
    var DROP_TEXT = 'Drop Playwright Trace to load';
    var TRACE_VIEWER_URL = 'https://trace.playwright.dev/next/';

    try {
        if (window.sessionStorage) {
            window.sessionStorage.removeItem(PANEL_DISMISSED_STORAGE_KEY);
        }
    } catch (error) {
        // ignore
    }

    function getHashQueryString() {
        var hashValue = window.location.hash || '';
        var queryIndex = hashValue.indexOf('?');
        if (queryIndex >= 0) {
            return hashValue.substring(queryIndex + 1);
        }

        var attachmentIndex = hashValue.indexOf('attachment=');
        if (attachmentIndex >= 0) {
            return hashValue.substring(attachmentIndex);
        }

        return '';
    }

    function getHashQueryParameter(parameterName) {
        try {
            return new URLSearchParams(getHashQueryString()).get(parameterName);
        } catch (error) {
            return null;
        }
    }

    function getAttachmentUidFromHash() {
        return getHashQueryParameter('attachment');
    }

    function getTestCaseUidFromHash() {
        var valueFromParameter =
            getHashQueryParameter('testCaseUid') ||
            getHashQueryParameter('testCaseId') ||
            getHashQueryParameter('testcaseUid') ||
            getHashQueryParameter('testcaseId');

        if (valueFromParameter) {
            return valueFromParameter;
        }

        try {
            var hashPath = (window.location.hash || '').split('?')[0];
            var match =
                hashPath.match(/testcase\/([^/?#&]+)/i) ||
                hashPath.match(/testresult\/([^/?#&]+)/i);
            return match ? match[1] : null;
        } catch (error) {
            return null;
        }
    }

    function documentContainsDropText() {
        try {
            return Boolean(
                document.body &&
                document.body.textContent &&
                document.body.textContent.indexOf(DROP_TEXT) !== -1
            );
        } catch (error) {
            return false;
        }
    }

    function isPanelDismissedForThisSession() {
        try {
            return Boolean(
                window.sessionStorage &&
                window.sessionStorage.getItem(PANEL_DISMISSED_STORAGE_KEY) === 'true'
            );
        } catch (error) {
            return false;
        }
    }

    function isTraceFallbackActivated() {
        try {
            if (isPanelDismissedForThisSession()) {
                return false;
            }

            if ((window.location.hash || '').indexOf('attachment=') !== -1) {
                return true;
            }

            var traceIframe = document.getElementById(TRACE_IFRAME_ID);
            if (
                traceIframe &&
                traceIframe.tagName &&
                traceIframe.tagName.toLowerCase() === 'iframe'
            ) {
                return true;
            }

            return documentContainsDropText();
        } catch (error) {
            return false;
        }
    }

    function buildDefaultDownloadInfo(attachmentUid) {
        return {
            url: new URL(
                'data/attachments/' + attachmentUid + '.zip',
                window.location.href
            ).toString(),
            fileName: attachmentUid + '.zip'
        };
    }

    async function tryResolveAttachmentSourceFromTestCaseJson(attachmentUid) {
        try {
            var testCaseUid = getTestCaseUidFromHash();
            if (!testCaseUid) {
                return null;
            }

            var testCaseJsonUrl = new URL(
                'data/test-cases/' + testCaseUid + '.json',
                window.location.href
            ).toString();

            var response = await fetch(testCaseJsonUrl, { credentials: 'same-origin' });
            if (!response || !response.ok) {
                return null;
            }

            var jsonData = await response.json();

            var attachments = null;
            if (jsonData && Array.isArray(jsonData.attachments)) {
                attachments = jsonData.attachments;
            } else if (
                jsonData &&
                jsonData.testCase &&
                Array.isArray(jsonData.testCase.attachments)
            ) {
                attachments = jsonData.testCase.attachments;
            } else if (
                jsonData &&
                jsonData.item &&
                Array.isArray(jsonData.item.attachments)
            ) {
                attachments = jsonData.item.attachments;
            }

            if (!attachments) {
                return null;
            }

            for (var index = 0; index < attachments.length; index++) {
                var attachment = attachments[index];
                if (attachment && attachment.uid === attachmentUid && attachment.source) {
                    return { source: attachment.source };
                }
            }

            return null;
        } catch (error) {
            return null;
        }
    }

    function findPreferredContainerElement() {
        try {
            var traceIframe = document.getElementById(TRACE_IFRAME_ID);
            if (traceIframe) {
                return traceIframe.parentElement || document.body;
            }
        } catch (error) {
            // ignore
        }
        return document.body;
    }

    function markPanelDismissedForThisSession() {
        try {
            if (window.sessionStorage) {
                window.sessionStorage.setItem(PANEL_DISMISSED_STORAGE_KEY, 'true');
            }
        } catch (error) {
            // ignore
        }
    }

    function removePanelIfPresent() {
        try {
            var existingPanel = document.getElementById(PANEL_ID);
            if (existingPanel && existingPanel.parentNode) {
                existingPanel.parentNode.removeChild(existingPanel);
            }
        } catch (error) {
            // ignore
        }
    }

    function ensurePanelElementExists() {
        var existingPanel = document.getElementById(PANEL_ID);
        if (existingPanel) {
            return existingPanel;
        }

        var panelElement = document.createElement('div');
        panelElement.id = PANEL_ID;
        panelElement.innerHTML =
            '' +
            '<div class="pw-trace-fallback__header">' +
            '<div class="pw-trace-fallback__title">Playwright trace</div>' +
            '<button type="button" class="pw-trace-fallback__close" aria-label="Close">×</button>' +
            '</div>' +
            '<div class="pw-trace-fallback__row">' +
            '<a class="pw-trace-fallback__download" target="_blank" rel="noopener noreferrer">Download trace.zip</a>' +
            '</div>' +
            '<div class="pw-trace-fallback__row">' +
            '<button type="button" class="pw-trace-fallback__button">Open Trace Viewer</button>' +
            '</div>' +
            '<div class="pw-trace-fallback__hint">' +
            'Then click Select file and choose the downloaded trace.zip.' +
            '</div>' +
            '<div class="pw-trace-fallback__status" style="margin-top:6px;color:rgba(0,0,0,0.7);display:none;"></div>';

        panelElement.addEventListener(
            'click',
            function (event) {
                try {
                    event.stopPropagation();
                } catch (error) {
                    // ignore
                }
            },
            false
        );

        var closeButton = panelElement.querySelector('.pw-trace-fallback__close');
        if (closeButton) {
            closeButton.addEventListener(
                'click',
                function (event) {
                    try {
                        event.preventDefault();
                        event.stopPropagation();
                    } catch (error) {
                        // ignore
                    }

                    markPanelDismissedForThisSession();
                    removePanelIfPresent();
                },
                false
            );
        }

        var openButton = panelElement.querySelector('.pw-trace-fallback__button');
        if (openButton) {
            openButton.addEventListener(
                'click',
                function (event) {
                    try {
                        event.preventDefault();
                        event.stopPropagation();
                    } catch (error) {
                        // ignore
                    }

                    var newWindow;
                    try {
                        newWindow = window.open(TRACE_VIEWER_URL, '_blank');
                    } catch (error) {
                        newWindow = null;
                    }

                    if (!newWindow) {
                        try {
                            var statusElement = panelElement.querySelector('.pw-trace-fallback__status');
                            if (statusElement) {
                                statusElement.style.display = 'block';
                                statusElement.textContent =
                                    'Popup was blocked. Please allow popups for this site and click "Open Trace Viewer" again.';
                            }
                        } catch (error) {
                            // ignore
                        }
                        return;
                    }

                    try {
                        newWindow.opener = null;
                    } catch (error) {
                        // ignore
                    }
                },
                false
            );
        }

        var containerElement = findPreferredContainerElement();
        if (containerElement && containerElement.appendChild) {
            containerElement.appendChild(panelElement);
        } else if (document.body) {
            document.body.appendChild(panelElement);
        }

        return panelElement;
    }

    function updatePanelDownloadLink(downloadUrl, fileName) {
        var panelElement = ensurePanelElementExists();
        var downloadLink = panelElement.querySelector('.pw-trace-fallback__download');
        if (!downloadLink) {
            return;
        }

        downloadLink.href = downloadUrl;
        downloadLink.textContent = 'Download ' + (fileName || 'trace.zip');

        try {
            downloadLink.setAttribute('download', fileName || 'trace.zip');
        } catch (error) {
            // ignore
        }
    }

    var updateScheduled = false;

    function updatePanel() {
        if (updateScheduled) {
            return;
        }
        updateScheduled = true;

        setTimeout(function () {
            updateScheduled = false;

            if (!isTraceFallbackActivated()) {
                if (isPanelDismissedForThisSession()) {
                    removePanelIfPresent();
                }
                return;
            }

            var attachmentUid = getAttachmentUidFromHash();
            if (!attachmentUid) {
                updatePanelDownloadLink('#', 'trace.zip');
                return;
            }

            (async function () {
                var downloadInfo = buildDefaultDownloadInfo(attachmentUid);

                var resolved;
                try {
                    resolved = await tryResolveAttachmentSourceFromTestCaseJson(attachmentUid);
                } catch (error) {
                    resolved = null;
                }

                if (resolved && resolved.source) {
                    downloadInfo.url = new URL(
                        'data/attachments/' + resolved.source,
                        window.location.href
                    ).toString();
                    downloadInfo.fileName = resolved.source;
                }

                updatePanelDownloadLink(downloadInfo.url, downloadInfo.fileName);
            })();
        }, 50);
    }

    window.addEventListener('hashchange', updatePanel);

    try {
        var mutationObserver = new MutationObserver(function () {
            updatePanel();
        });
        mutationObserver.observe(document.documentElement, {
            childList: true,
            subtree: true
        });
    } catch (error) {
        // ignore
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', updatePanel);
    } else {
        updatePanel();
    }
})();
