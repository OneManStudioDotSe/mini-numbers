/** Mini Numbers Analytics Tracker — https://github.com/user/mini-numbers */
(function() {
    var s = document.currentScript;
    var key = s.getAttribute('data-project-key');
    var endpoint = s.getAttribute('data-api-endpoint') || window.location.origin + '/collect';
    if (!key) return;

    // Session ID (per tab, no cookies)
    var sid = sessionStorage.getItem('mn_sid');
    if (!sid) {
        var a = new Uint8Array(16);
        crypto.getRandomValues(a);
        sid = Array.from(a, function(b) { return ('0' + b.toString(16)).slice(-2); }).join('');
        sessionStorage.setItem('mn_sid', sid);
    }

    // Track function
    function track(type) {
        navigator.sendBeacon(
            endpoint + '?key=' + key,
            JSON.stringify({
                path: location.pathname,
                referrer: document.referrer || null,
                sessionId: sid,
                type: type || 'pageview'
            })
        );
    }

    // Initial pageview
    track('pageview');

    // Heartbeat (pauses when tab is hidden)
    var hb = setInterval(function() { track('heartbeat'); }, 30000);

    document.addEventListener('visibilitychange', function() {
        if (document.hidden) {
            clearInterval(hb);
        } else {
            hb = setInterval(function() { track('heartbeat'); }, 30000);
        }
    });

    // SPA support via History API
    var lastPath = location.pathname;
    function onNav() {
        if (lastPath !== location.pathname) {
            lastPath = location.pathname;
            track('pageview');
        }
    }

    var origPush = history.pushState;
    var origReplace = history.replaceState;
    history.pushState = function() { origPush.apply(this, arguments); onNav(); };
    history.replaceState = function() { origReplace.apply(this, arguments); onNav(); };
    window.addEventListener('popstate', onNav);
})();
