/**
 How to use it:
 <script
    async
    src="https://api.yourdomain.com/static/tracker.js"
    data-project-key="test-key-123">
 </script>
 **/

(function() {
    // 1. Configuration: Get the API Key from the script tag's data attribute
    const script = document.currentScript;
    const apiKey = script.getAttribute('data-project-key');
    const endpoint = "https://your-ktor-api-domain.com/collect"; // TODO: UPDATE THIS

    if (!apiKey) {
        console.error("Minimalist Analytics: Missing data-project-key");
        return;
    }

    // 2. Session management (No Cookies)
    // sessionStorage persists for the duration of the page session (tab)
    let sessionId = sessionStorage.getItem('ma_session_id');
    if (!sessionId) {
        sessionId = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
        sessionStorage.setItem('ma_session_id', sessionId);
    }

    // 3. The Tracking Function
    const track = (type = 'pageview') => {
        const payload = JSON.stringify({
            path: window.location.pathname,
            referrer: document.referrer || null,
            sessionId: sessionId,
            type: type
        });

        // useBeacon is standard for analytics; it's non-blocking and reliable on page unload
        if (navigator.sendBeacon) {
            navigator.sendBeacon(endpoint + "?key=" + apiKey, payload);
        } else {
            // Fallback for very old browsers
            fetch(endpoint, {
                method: 'POST',
                body: payload,
                headers: { 'Content-Type': 'application/json', 'X-Project-Key': apiKey },
                keepalive: true
            });
        }
    };

    // 4. Initial Load
    track('pageview');

    // 5. Heartbeat (Track duration)
    // Sends a ping every 30 seconds
    setInterval(() => {
        track('heartbeat');
    }, 30000);

    // 6. SPA Support (Single Page Applications)
    // Detects URL changes without a full page reload
    let lastPath = window.location.pathname;
    const observer = new MutationObserver(() => {
        if (lastPath !== window.location.pathname) {
            lastPath = window.location.pathname;
            track('pageview');
        }
    });
    observer.observe(document, { subtree: true, childList: true });

})();
