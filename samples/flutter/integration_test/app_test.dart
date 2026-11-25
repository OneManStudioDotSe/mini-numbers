// Walks the real UI against a running Mini Numbers server:
//   flutter test integration_test -d <device> \
//     --dart-define=SERVER_URL=http://localhost:8092 --dart-define=PROJECT_KEY=<key>
import 'package:corner_shop/events_screen.dart';
import 'package:corner_shop/main.dart';
import 'package:corner_shop/mini_numbers.dart';
import 'package:corner_shop/settings_screen.dart';
import 'package:material_ui/material_ui.dart' show Key, Switch;
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Pauses on a screen so a screenshot can be taken (set SHOT_PAUSE=seconds).
Future<void> look(WidgetTester t, String name) async {
  const seconds = int.fromEnvironment('SHOT_PAUSE');
  // ignore: avoid_print
  print('SCREEN $name');
  if (seconds > 0) await t.pump(const Duration(seconds: seconds));
}

Future<void> settle(WidgetTester t) async {
  // Real HTTP replies arrive outside the frame clock, so give them time.
  await t.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 600)));
  await t.pumpAndSettle();
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('shop, buy, refund, events, settings, opt out', (t) async {
    final opened = <Uri>[];
    openInBrowser = (uri) async => opened.add(uri); // keep the app in front
    // Start from opt-out OFF, whatever an earlier run left on the device.
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('opt_out');
    addTearDown(() => prefs.remove('opt_out'));
    final (url, key, _) = await loadSettings();
    tracker.configure(url, key);
    tracker.optOut = false;

    await t.pumpWidget(const CornerShopApp());
    await settle(t);
    await look(t, 'shop');

    await t.tap(find.byKey(const Key('product-mug')));
    await settle(t);
    await look(t, 'detail');
    await t.tap(find.text('Buy'));
    await settle(t);
    expect(find.text('Sent purchase · €12.00'), findsOneWidget);
    await t.tap(find.text('Refund'));
    await settle(t);
    expect(find.text('Sent refund · -€12.00'), findsOneWidget);
    await t.tap(find.byKey(const Key('back')));
    await settle(t);

    await t.tap(find.byKey(const Key('tab-Events')));
    await settle(t);
    await t.tap(find.text('Sign up'));
    await settle(t);
    await t.tap(find.text('Open the docs'));
    await settle(t);
    expect(opened.single.host, 'github.com');
    await look(t, 'events');
    expect(tracker.sentCount, greaterThanOrEqualTo(6));

    await t.tap(find.byKey(const Key('tab-Settings')));
    await settle(t);
    await look(t, 'settings');
    await t.tap(find.byType(Switch));
    await settle(t);
    expect(tracker.optOut, isTrue);

    final before = tracker.sentCount;
    await t.tap(find.byKey(const Key('tab-Shop')));
    await settle(t);
    await t.tap(find.byKey(const Key('product-notebook')));
    await settle(t);
    await t.tap(find.text('Buy'));
    await settle(t);
    await t.tap(find.byKey(const Key('tab-Events')));
    await settle(t);
    expect(tracker.sentCount, before);
    expect(find.text('not sent'), findsWidgets);
    await look(t, 'events-opted-out');
  });
}
