/** Mini Numbers Analytics Tracker — https://github.com/user/mini-numbers */
(function() {
    var s = document.currentScript;
    var key = s.getAttribute('data-project-key');
    var endpoint = s.getAttribute('data-api-endpoint') || window.location.origin + '/collect';
    if (!key) return;

    // Configurable options via data attributes
    var heartbeatInterval = parseInt(s.getAttribute('data-heartbeat-interval')) || 30000;
    var spaEnabled = s.getAttribute('data-disable-spa') !== 'true';

    // Session ID (per tab, no cookies)
    var sid = sessionStorage.getItem('mn_sid');
    if (!sid) {
        var a = new Uint8Array(16);
        crypto.getRandomValues(a);
        sid = Array.from(a, function(b) { return ('0' + b.toString(16)).slice(-2); }).join('');
        sessionStorage.setItem('mn_sid', sid);
    }

    // UTM parameter extraction and persistence
    function getUtmParams() {
        var params = new URLSearchParams(location.search);
        var map = {utm_source:'utmSource', utm_medium:'utmMedium', utm_campaign:'utmCampaign', utm_term:'utmTerm', utm_content:'utmContent'};
        var utm = {};
        for (var k in map) { var v = params.get(k); if (v) utm[map[k]] = v; }
        if (Object.keys(utm).length) sessionStorage.setItem('mn_utm', JSON.stringify(utm));
        try { return JSON.parse(sessionStorage.getItem('mn_utm') || '{}'); } catch(e) { return {}; }
    }

    // Send event to server
    function send(type, eventName, extra) {
        var payload = {
            path: location.pathname,
            referrer: document.referrer || null,
            sessionId: sid,
            type: type || 'pageview'
        };
        if (eventName) payload.eventName = eventName;
        // Merge UTM params for pageview events
        if (type === 'pageview') {
            var utm = getUtmParams();
            for (var k in utm) payload[k] = utm[k];
        }
        // Merge any extra fields (scrollDepth, targetUrl, properties)
        if (extra) { for (var k in extra) payload[k] = extra[k]; }
        navigator.sendBeacon(endpoint + '?key=' + key, JSON.stringify(payload));
    }

    // Initial pageview
    send('pageview');

    // Heartbeat (pauses when tab is hidden)
    var hb = setInterval(function() { send('heartbeat'); }, heartbeatInterval);

    document.addEventListener('visibilitychange', function() {
        if (document.hidden) {
            clearInterval(hb);
        } else {
            hb = setInterval(function() { send('heartbeat'); }, heartbeatInterval);
        }
    });

    // Scroll depth tracking
    var scrollThresholds = [25, 50, 75, 100];
    var scrollFired = {};

    function getScrollPercent() {
        var h = document.documentElement;
        var b = document.body;
        var st = window.pageYOffset || h.scrollTop || b.scrollTop || 0;
        var sh = Math.max(h.scrollHeight, b.scrollHeight) - Math.max(h.clientHeight, b.clientHeight);
        return sh > 0 ? Math.round((st / sh) * 100) : 0;
    }

    window.addEventListener('scroll', function() {
        var pct = getScrollPercent();
        for (var i = 0; i < scrollThresholds.length; i++) {
            var t = scrollThresholds[i];
            if (pct >= t && !scrollFired[t]) {
                scrollFired[t] = true;
                send('scroll', null, { scrollDepth: t });
            }
        }
    });

    // Outbound link and file download tracking
    var fileExts = /\.(pdf|zip|xlsx?|docx?|pptx?|csv|rar|7z|tar|gz|dmg|exe|mp3|mp4|avi|mov)$/i;

    document.addEventListener('click', function(e) {
        var link = e.target.closest ? e.target.closest('a') : null;
        if (!link || !link.href) return;
        try {
            var url = new URL(link.href, location.origin);
            // File download detection
            if (fileExts.test(url.pathname)) {
                var fname = url.pathname.split('/').pop() || url.pathname;
                send('download', fname.substring(0, 100), { targetUrl: link.href.substring(0, 1024) });
                return;
            }
            // Outbound link detection (different hostname)
            if (url.hostname && url.hostname !== location.hostname && url.protocol.indexOf('http') === 0) {
                send('outbound', url.hostname, { targetUrl: link.href.substring(0, 1024) });
            }
        } catch(ex) { /* ignore invalid URLs */ }
    });

    // SPA support via History API (can be disabled)
    if (spaEnabled) {
        var lastPath = location.pathname;
        function onNav() {
            if (lastPath !== location.pathname) {
                lastPath = location.pathname;
                scrollFired = {}; // Reset scroll tracking for new page
                send('pageview');
            }
        }

        var origPush = history.pushState;
        var origReplace = history.replaceState;
        history.pushState = function() { origPush.apply(this, arguments); onNav(); };
        history.replaceState = function() { origReplace.apply(this, arguments); onNav(); };
        window.addEventListener('popstate', onNav);
    }

    // Public API for custom event tracking with optional properties
    window.MiniNumbers = {
        track: function(name, props) {
            var extra = {};
            if (props && typeof props === 'object') {
                extra.properties = JSON.stringify(props);
            }
            send('custom', name, extra);
        }
    };
})();
