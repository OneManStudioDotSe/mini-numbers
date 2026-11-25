import 'dart:io' show Platform;

import 'package:material_ui/material_ui.dart' hide Card;
import 'package:shared_preferences/shared_preferences.dart';

import 'components.dart';
import 'mini_numbers.dart';
import 'theme.dart';

/// Developer overrides: `--dart-define=SERVER_URL=... --dart-define=PROJECT_KEY=...`.
const definedServerUrl = String.fromEnvironment('SERVER_URL');
const definedProjectKey = String.fromEnvironment('PROJECT_KEY');

const _urlKey = 'server_url', _projectKeyKey = 'project_key', _optOutKey = 'opt_out';

/// iOS reaches the host machine as localhost; the Android emulator calls it 10.0.2.2.
String get defaultServerUrl => Platform.isAndroid ? 'http://10.0.2.2:8092' : 'http://localhost:8092';

/// The stored settings, or the launch-time overrides when they are given.
Future<(String, String, bool)> loadSettings() async {
  final prefs = await SharedPreferences.getInstance();
  final url = definedServerUrl.isNotEmpty ? definedServerUrl : prefs.getString(_urlKey) ?? defaultServerUrl;
  final key = definedProjectKey.isNotEmpty ? definedProjectKey : prefs.getString(_projectKeyKey) ?? '';
  return (url, key, prefs.getBool(_optOutKey) ?? false);
}

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final _url = TextEditingController(text: tracker.baseUrl);
  late final _key = TextEditingController(text: tracker.projectKey);

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_urlKey, _url.text.trim());
    await prefs.setString(_projectKeyKey, _key.text.trim());
    tracker.configure(_url.text, _key.text);
  }

  Future<void> _setOptOut(bool value) async {
    tracker.optOut = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_optOutKey, value);
  }

  @override
  void dispose() {
    _url.dispose();
    _key.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return ListenableBuilder(
      listenable: tracker,
      builder: (context, _) => ScreenBody(
        title: 'Settings',
        subtitle: 'Where the events go.',
        children: [
          Card(
            title: 'Server',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Field(label: 'Server address', controller: _url),
                const SizedBox(height: Space.smPlus),
                Field(label: 'Project key', controller: _key, mono: true),
                const SizedBox(height: Space.md),
                PillButton('Save', onPressed: _save),
                const SizedBox(height: Space.sm),
                Text(
                  'Find the key in the dashboard under Settings → Project → Tracking ID.',
                  style: AppText.small(c),
                ),
              ],
            ),
          ),
          Card(
            title: 'Privacy',
            child: ToggleRow(
              title: "Don't track me",
              text: 'While this is on, the app sends nothing.',
              value: tracker.optOut,
              onChanged: _setOptOut,
            ),
          ),
          Card(
            title: 'Session',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(tracker.sessionId, style: AppText.mono(c, color: c.textMuted)),
                const SizedBox(height: Space.smPlus),
                PillButton(
                  'Start a new session',
                  icon: Ri.refresh,
                  style: PillStyle.ghost,
                  onPressed: tracker.newSession,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
