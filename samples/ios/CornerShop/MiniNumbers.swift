import Foundation

// The whole Mini Numbers tracker in one file: URLSession + JSONSerialization, nothing else.
// Copy this file into your own app, call `configure`, then `screen` / `event` / `purchase` ...

struct LogEntry: Identifiable {
    let id = UUID()
    let time: Date
    let type: String    // pageview, heartbeat, custom, outbound
    let label: String   // path, event name or host
    let status: String  // HTTP code, "not sent" or a short error
}

@MainActor
final class MiniNumbers: ObservableObject {
    static let shared = MiniNumbers()

    /// Newest first, at most 50 entries.
    @Published private(set) var log: [LogEntry] = []
    /// Events answered with 202 during this run.
    @Published private(set) var sentCount = 0
    /// In memory only: a new one on every launch and on `newSession()`.
    @Published private(set) var sessionId = MiniNumbers.makeSessionId()
    /// While on, nothing leaves the device and the log says "not sent".
    @Published var optOut = UserDefaults.standard.bool(forKey: "optOut") {
        didSet { UserDefaults.standard.set(optOut, forKey: "optOut") }
    }

    private var baseUrl = ""
    private var projectKey = ""
    private var currentPath = "/"
    private var heartbeatTask: Task<Void, Never>?

    func configure(baseUrl: String, projectKey: String) {
        self.baseUrl = baseUrl.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        self.projectKey = projectKey.trimmingCharacters(in: .whitespaces)
    }

    func screen(_ path: String) {
        currentPath = Self.cleanPath(path)
        send(type: "pageview", label: currentPath)
    }

    func event(_ name: String, properties: [String: Any] = [:]) {
        send(type: "custom", label: name, eventName: name, properties: properties)
    }

    func purchase(amount: Double, currency: String) {
        event("purchase", properties: ["revenue": amount, "currency": currency])
    }

    func refund(amount: Double, currency: String) {
        event("refund", properties: ["revenue": -amount, "currency": currency])
    }

    func outbound(_ url: String) {
        let host = URL(string: url)?.host ?? url
        send(type: "outbound", label: host, eventName: host, targetUrl: url)
    }

    /// Every 30 seconds while the app is in the foreground, for the screen the user is on.
    func startHeartbeat() {
        stopHeartbeat()
        heartbeatTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                if Task.isCancelled { break }
                send(type: "heartbeat", label: currentPath)
            }
        }
    }

    func stopHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
    }

    func newSession() {
        sessionId = Self.makeSessionId()
    }

    // MARK: - Payload (pure, covered by unit tests)

    /// Builds the JSON body for POST /collect. The rules the server enforces:
    ///  - `sessionId` goes on every event.
    ///  - `properties` is a STRING holding JSON ("{\"plan\":\"free\"}"), not a nested object. Left out when empty.
    ///  - `pageview` and `heartbeat` never carry `eventName` (the server rejects them if they do).
    ///  - `referrer` is never sent.
    ///  - `path` starts with "/" and has no spaces.
    nonisolated static func payload(type: String, path: String, sessionId: String,
                                    eventName: String? = nil, properties: [String: Any] = [:],
                                    targetUrl: String? = nil) -> [String: Any] {
        var body: [String: Any] = ["type": type, "path": cleanPath(path), "sessionId": sessionId]
        guard type != "pageview", type != "heartbeat" else { return body }
        if let eventName { body["eventName"] = eventName }
        if let targetUrl { body["targetUrl"] = targetUrl }
        if !properties.isEmpty,
           let data = try? JSONSerialization.data(withJSONObject: properties, options: [.sortedKeys]),
           let json = String(data: data, encoding: .utf8) {
            body["properties"] = json
        }
        return body
    }

    nonisolated static func cleanPath(_ path: String) -> String {
        let noSpaces = path.replacingOccurrences(of: " ", with: "-")
        return noSpaces.hasPrefix("/") ? noSpaces : "/" + noSpaces
    }

    /// 32 random lowercase hex characters.
    nonisolated static func makeSessionId() -> String {
        (0..<16).map { _ in String(format: "%02x", UInt8.random(in: .min ... .max)) }.joined()
    }

    /// The server tells devices apart by User-Agent. Without one the device shows as "Unknown", and a name
    /// containing "bot", "crawl" or "spider" is answered with 202 and silently dropped. So: a plain
    /// mobile-looking string, and no such words in the app name.
    nonisolated static var userAgent: String {
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "MiniNumbersSample/1.0 (iPhone; CPU iPhone OS \(v.majorVersion)_\(v.minorVersion) like Mac OS X) Mobile"
    }

    // MARK: - Sending

    private func send(type: String, label: String, eventName: String? = nil,
                      properties: [String: Any] = [:], targetUrl: String? = nil) {
        let time = Date()
        if optOut { return add(time, type, label, "not sent") }
        guard !projectKey.isEmpty else { return add(time, type, label, "no key") }
        guard let url = URL(string: baseUrl + "/collect") else { return add(time, type, label, "bad url") }

        let body = Self.payload(type: type, path: currentPath, sessionId: sessionId,
                                eventName: eventName, properties: properties, targetUrl: targetUrl)
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(projectKey, forHTTPHeaderField: "X-Project-Key")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        // Fire and forget: a failure is logged, never retried.
        Task {
            var status: String
            do {
                let (_, response) = try await URLSession.shared.data(for: request)
                status = String((response as? HTTPURLResponse)?.statusCode ?? 0)
            } catch {
                status = (error as? URLError).map { "error \($0.code.rawValue)" } ?? "error"
            }
            if status == "202" { sentCount += 1 }
            add(time, type, label, status)
        }
    }

    private func add(_ time: Date, _ type: String, _ label: String, _ status: String) {
        log.insert(LogEntry(time: time, type: type, label: label, status: status), at: 0)
        if log.count > 50 { log.removeLast(log.count - 50) }
    }
}
