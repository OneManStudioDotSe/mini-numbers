import 'dart:convert';

import 'package:corner_shop/mini_numbers.dart';
import 'package:flutter_test/flutter_test.dart';

const _session = '0123456789abcdef0123456789abcdef';

Map<String, Object?>? build(
  String type, {
  String key = 'k',
  bool optOut = false,
  String? eventName,
  Map<String, Object?>? properties,
  String? targetUrl,
}) => buildPayload(
  type: type,
  path: '/shop',
  sessionId: _session,
  projectKey: key,
  optOut: optOut,
  eventName: eventName,
  properties: properties,
  targetUrl: targetUrl,
);

void main() {
  test('pageview has a session id and no eventName', () {
    final p = build('pageview', eventName: 'oops')!;
    expect(p['type'], 'pageview');
    expect(p['path'], '/shop');
    expect(p['sessionId'], _session);
    expect(p.containsKey('eventName'), isFalse);
    expect(p.containsKey('referrer'), isFalse);
  });

  test('heartbeat never carries an eventName', () {
    expect(build('heartbeat', eventName: 'oops')!.containsKey('eventName'), isFalse);
  });

  test('properties is a JSON string, not an object', () {
    final p = build('custom', eventName: 'signup', properties: {'plan': 'free', 'source': 'sample-app'})!;
    expect(p['eventName'], 'signup');
    expect(p['properties'], isA<String>());
    expect(jsonDecode(p['properties'] as String), {'plan': 'free', 'source': 'sample-app'});
  });

  test('empty properties are left out', () {
    expect(build('custom', eventName: 'x', properties: {})!.containsKey('properties'), isFalse);
  });

  test('purchase is positive and refund is negative', () {
    final buy = build('custom', eventName: 'purchase', properties: {'revenue': 12.0, 'currency': 'EUR'})!;
    final refund = build('custom', eventName: 'refund', properties: {'revenue': -12.0, 'currency': 'EUR'})!;
    expect(jsonDecode(buy['properties'] as String)['revenue'], 12.0);
    expect(jsonDecode(refund['properties'] as String)['revenue'], -12.0);
  });

  test('outbound has the host as eventName and the targetUrl', () {
    final p = build('outbound', eventName: 'github.com', targetUrl: 'https://github.com/x')!;
    expect(p['eventName'], 'github.com');
    expect(p['targetUrl'], 'https://github.com/x');
  });

  test('nothing is built when opted out or without a key', () {
    expect(build('pageview', optOut: true), isNull);
    expect(build('pageview', key: ''), isNull);
  });

  test('session ids are 32 lowercase hex characters', () {
    expect(newSessionId(), matches(RegExp(r'^[0-9a-f]{32}$')));
    expect(newSessionId(), isNot(newSessionId()));
  });
}
