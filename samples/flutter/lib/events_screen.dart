import 'package:material_ui/material_ui.dart' hide Card;
import 'package:url_launcher/url_launcher.dart';

import 'components.dart';
import 'mini_numbers.dart';
import 'theme.dart';

const docsUrl = 'https://github.com/OneManStudioDotSe/mini-numbers';

/// Opens an address in the system browser. A variable so the integration test can swap it
/// out and keep the app in front.
Future<void> Function(Uri uri) openInBrowser = (uri) async =>
    launchUrl(uri, mode: LaunchMode.externalApplication);

String _hms(DateTime t) =>
    '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';

class EventsScreen extends StatelessWidget {
  const EventsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return ListenableBuilder(
      listenable: tracker,
      builder: (context, _) => ScreenBody(
        title: 'Events',
        subtitle: 'Tap a button, watch it go.',
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: StatTile(label: 'Sent', value: '${tracker.sentCount}'),
              ),
              const SizedBox(width: Space.md),
              Expanded(
                child: StatTile(label: 'Session', value: tracker.sessionId.substring(0, 8)),
              ),
            ],
          ),
          Card(
            title: 'Try it',
            child: Column(
              children: [
                PillButton(
                  'Sign up',
                  icon: Ri.userAdd,
                  onPressed: () => tracker.event('signup', {'plan': 'free', 'source': 'sample-app'}),
                ),
                const SizedBox(height: Space.sm),
                PillButton(
                  'Open the docs',
                  icon: Ri.externalLink,
                  style: PillStyle.ghost,
                  onPressed: () {
                    tracker.outbound(docsUrl);
                    openInBrowser(Uri.parse(docsUrl));
                  },
                ),
              ],
            ),
          ),
          Card(
            title: 'Log',
            child: tracker.log.isEmpty
                ? Text('Nothing sent yet.', style: AppText.small(c))
                : Column(
                    children: [
                      for (final e in tracker.log)
                        LogRow(type: e.type, label: e.label, time: _hms(e.time), status: e.status),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}
