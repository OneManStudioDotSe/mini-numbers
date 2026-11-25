import 'package:material_ui/material_ui.dart' hide Card;

import 'components.dart';
import 'events_screen.dart';
import 'mini_numbers.dart';
import 'settings_screen.dart';
import 'shop_screen.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final (url, key, optOut) = await loadSettings();
  tracker.configure(url, key);
  tracker.optOut = optOut;
  runApp(const CornerShopApp());
}

class CornerShopApp extends StatelessWidget {
  const CornerShopApp({super.key});

  @override
  Widget build(BuildContext context) {
    // Light or dark follows the system setting.
    final brightness = MediaQuery.platformBrightnessOf(context);
    final colors = brightness == Brightness.dark ? AppColors.dark : AppColors.light;
    return MaterialApp(
      title: 'Corner Shop',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(colors),
      home: const Shell(),
    );
  }
}

/// The three tabs plus the product detail (which lives inside the Shop tab).
class Shell extends StatefulWidget {
  const Shell({super.key});

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> with WidgetsBindingObserver {
  static const _tabs = [
    ('Shop', Ri.store, '/shop'),
    ('Events', Ri.pulse, '/events'),
    ('Settings', Ri.settings, '/settings'),
  ];

  int _tab = 0;
  Product? _product;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    tracker.screen('/shop');
    tracker.startHeartbeat();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// The heartbeat runs only while the app is in the foreground.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      tracker.startHeartbeat();
    } else if (state == AppLifecycleState.paused) {
      tracker.stopHeartbeat();
    }
  }

  void _selectTab(int i) {
    if (i == _tab && _product == null) return;
    setState(() {
      _tab = i;
      _product = null;
    });
    tracker.screen(_tabs[i].$3);
  }

  void _open(Product p) {
    setState(() => _product = p);
    tracker.screen('/shop/${p.id}');
  }

  void _back() {
    setState(() => _product = null);
    tracker.screen('/shop');
  }

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    final body = switch (_tab) {
      0 =>
        _product == null
            ? ShopScreen(onOpen: _open)
            : ProductScreen(key: ValueKey(_product!.id), product: _product!, onBack: _back),
      1 => const EventsScreen(),
      _ => const SettingsScreen(),
    };
    return Scaffold(
      backgroundColor: c.bgPrimary,
      body: SafeArea(bottom: false, child: body),
      bottomNavigationBar: _TabBar(selected: _tab, onSelect: _selectTab, items: _tabs),
    );
  }
}

/// Custom tab bar: bgSecondary, a 1 px top border, primary tint on the selected tab.
class _TabBar extends StatelessWidget {
  const _TabBar({required this.selected, required this.onSelect, required this.items});
  final int selected;
  final ValueChanged<int> onSelect;
  final List<(String, int, String)> items;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return Container(
      decoration: BoxDecoration(
        color: c.bgSecondary,
        border: Border(
          top: BorderSide(color: c.border, width: Sizes.borderWidth),
        ),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: Sizes.tabBarHeight,
          child: Row(
            children: [
              for (var i = 0; i < items.length; i++)
                Expanded(
                  child: GestureDetector(
                    key: Key('tab-${items[i].$1}'),
                    behavior: HitTestBehavior.opaque,
                    onTap: () => onSelect(i),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Glyph(
                          items[i].$2,
                          size: Sizes.tabIcon,
                          color: i == selected ? c.primary : c.textMuted,
                        ),
                        const SizedBox(height: Space.xs),
                        Text(
                          items[i].$1,
                          style: AppText.label(c).copyWith(color: i == selected ? c.primary : c.textMuted),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
