// The Mini Numbers tracker for this app: one file, dart:io and dart:convert only.
//
// It sends everything to `POST {baseUrl}/collect`. Copy this file into your own app.
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';

/// One line of the in-app log.
class LogEntry {
  LogEntry(this.type, this.label, this.status) : time = DateTime.now();
  final DateTime time;
  final String type; // pageview, heartbeat, custom, outbound
  final String label; // path, event name or host
  final String status; // "202", "not sent", "no key" or a short error
}

/// Builds the JSON body for `/collect`, or returns null when nothing may be sent.
///
/// It is a pure function (no clock, no network, no globals) so it can be tested alone.
///
/// Rules, all of them enforced by the server:
/// * `sessionId` goes on every event. It is 32 random hex characters, kept in memory only.
/// * `properties` is a STRING that holds JSON (`"{\"plan\":\"free\"}"`), not a nested object,
///   and it is left out when empty.
/// * Never send `referrer`. Never send `eventName` on `pageview` or `heartbeat`; the server
///   rejects the request if you do.
/// * `path` starts with `/` and has no spaces.
/// * While the user has opted out, or there is no project key, nothing is built.
Map<String, Object?>? buildPayload({
  required String type,
  required String path,
  required String sessionId,
  required String projectKey,
  required bool optOut,
  String? eventName,
  Map<String, Object?>? properties,
  String? targetUrl,
}) {
  if (optOut || projectKey.isEmpty) return null;
  final isEvent = type == 'custom' || type == 'outbound';
  return {
    'type': type,
    'path': path,
    'sessionId': sessionId,
    if (isEvent && eventName != null) 'eventName': eventName,
    if (isEvent && properties != null && properties.isNotEmpty) 'properties': jsonEncode(properties),
    if (type == 'outbound' && targetUrl != null) 'targetUrl': targetUrl,
  };
}

/// 32 lowercase hex characters from the secure random generator.
String newSessionId() {
  final random = Random.secure();
  return List.generate(32, (_) => random.nextInt(16).toRadixString(16)).join();
}

/// The User-Agent for the platform we run on. Without one the dashboard shows the device as
/// "Unknown", and Dart's own `Dart/x.y (dart:io)` would be no better. A name that contains
/// "bot", "crawl" or "spider" is answered with 202 and silently dropped, so avoid those.
String buildUserAgent() {
  if (Platform.isIOS) {
    // operatingSystemVersion looks like "Version 26.0 (Build 23A344)".
    final m = RegExp(r'(\d+)\.(\d+)').firstMatch(Platform.operatingSystemVersion);
    final major = m?.group(1) ?? '17';
    final minor = m?.group(2) ?? '0';
    return 'MiniNumbersSample/1.0 (iPhone; CPU iPhone OS ${major}_$minor like Mac OS X) Mobile';
  }
  // On Android Platform.operatingSystemVersion is the kernel string, so the release is a
  // generic one; reading the real release and model would need a plugin.
  return 'MiniNumbersSample/1.0 (Linux; Android 14; Flutter) Mobile';
}

class MiniNumbers extends ChangeNotifier {
  String baseUrl = '';
  String projectKey = '';
  String sessionId = newSessionId();
  String currentPath = '/';

  /// Events answered 202 during this run.
  int sentCount = 0;

  /// Newest first, at most 50 entries.
  final List<LogEntry> log = [];

  bool _optOut = false;
  bool get optOut => _optOut;
  set optOut(bool value) {
    _optOut = value;
    notifyListeners();
  }

  Timer? _heartbeat;
  final HttpClient _client = HttpClient()..connectionTimeout = const Duration(seconds: 10);

  void configure(String baseUrl, String projectKey) {
    this.baseUrl = baseUrl.trim().replaceAll(RegExp(r'/+$'), '');
    this.projectKey = projectKey.trim();
    notifyListeners();
  }

  /// A new random session id (the old one is forgotten).
  void newSession() {
    sessionId = newSessionId();
    notifyListeners();
  }

  void screen(String path) {
    currentPath = path;
    _send('pageview', path);
  }

  void event(String name, [Map<String, Object?>? properties]) =>
      _send('custom', currentPath, eventName: name, properties: properties);

  void purchase(double amount, String currency) =>
      event('purchase', {'revenue': amount, 'currency': currency});

  void refund(double amount, String currency) => event('refund', {'revenue': -amount, 'currency': currency});

  void outbound(String url) => _send('outbound', currentPath, eventName: Uri.parse(url).host, targetUrl: url);

  /// A heartbeat every 30 s while the app is in the foreground keeps the visit "live".
  void startHeartbeat() {
    _heartbeat?.cancel();
    _heartbeat = Timer.periodic(const Duration(seconds: 30), (_) => _send('heartbeat', currentPath));
  }

  void stopHeartbeat() {
    _heartbeat?.cancel();
    _heartbeat = null;
  }

  Future<void> _send(
    String type,
    String path, {
    String? eventName,
    Map<String, Object?>? properties,
    String? targetUrl,
  }) async {
    final label = eventName ?? path;
    final payload = buildPayload(
      type: type,
      path: path,
      sessionId: sessionId,
      projectKey: projectKey,
      optOut: _optOut,
      eventName: eventName,
      properties: properties,
      targetUrl: targetUrl,
    );
    if (payload == null) {
      _record(type, label, _optOut ? 'not sent' : 'no key');
      return;
    }
    try {
      final request = await _client.postUrl(Uri.parse('$baseUrl/collect'));
      request.headers
        ..set(HttpHeaders.contentTypeHeader, 'application/json')
        ..set('X-Project-Key', projectKey)
        ..set(HttpHeaders.userAgentHeader, buildUserAgent());
      request.write(jsonEncode(payload));
      final response = await request.close().timeout(const Duration(seconds: 10));
      await response.drain<void>();
      if (response.statusCode == 202) sentCount++;
      _record(type, label, '${response.statusCode}'); // not retried when it is not 202
    } catch (e) {
      _record(type, label, e is TimeoutException ? 'timeout' : 'error');
    }
  }

  void _record(String type, String label, String status) {
    log.insert(0, LogEntry(type, label, status));
    if (log.length > 50) log.removeRange(50, log.length);
    notifyListeners();
  }

  @override
  void dispose() {
    stopHeartbeat();
    _client.close(force: true);
    super.dispose();
  }
}

/// The one tracker the whole app shares.
final tracker = MiniNumbers();
