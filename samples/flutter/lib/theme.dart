// Every colour, size and text style of the app lives here (it mirrors
// src/main/resources/static/css/variables.css and themes.css of Mini Numbers).
// Screens and components never write a colour or a number of their own, so
// restyling the app means editing this file only.
import 'package:material_ui/material_ui.dart';

class AppColors {
  const AppColors({
    required this.primary,
    required this.onPrimary,
    required this.bgPrimary,
    required this.bgSecondary,
    required this.bgTertiary,
    required this.textPrimary,
    required this.textSecondary,
    required this.textMuted,
    required this.border,
    required this.success,
    required this.danger,
    required this.badges,
    required this.badgeTint,
    required this.brightness,
  });

  final Color primary, onPrimary;
  final Color bgPrimary, bgSecondary, bgTertiary;
  final Color textPrimary, textSecondary, textMuted;
  final Color border, success, danger;

  /// badge1 (ember), badge2 (blue), badge3 (green), badge4 (amber).
  final List<Color> badges;
  final double badgeTint;
  final Brightness brightness;

  static const light = AppColors(
    primary: Color(0xFFC9340F),
    onPrimary: Color(0xFFFFFFFF),
    bgPrimary: Color(0xFFF4F4F5),
    bgSecondary: Color(0xFFFFFFFF),
    bgTertiary: Color(0xFFEEEEF0),
    textPrimary: Color(0xFF151517),
    textSecondary: Color(0xFF4A4A52),
    textMuted: Color(0xFF6A6A73),
    border: Color(0xFFE3E3E7),
    success: Color(0xFF0B7A53),
    danger: Color(0xFFC81E4A),
    badges: [Color(0xFFC9340F), Color(0xFF2F7DF6), Color(0xFF12A36D), Color(0xFFC87A00)],
    badgeTint: 0.12,
    brightness: Brightness.light,
  );

  static const dark = AppColors(
    primary: Color(0xFFFF6E52),
    onPrimary: Color(0xFF190A06),
    bgPrimary: Color(0xFF0E0E0F),
    bgSecondary: Color(0xFF19191B),
    bgTertiary: Color(0xFF232326),
    textPrimary: Color(0xFFF5F5F6),
    textSecondary: Color(0xFFB6B6BD),
    textMuted: Color(0xFF9797A0),
    border: Color(0xFF29292D),
    success: Color(0xFF3ECF8E),
    danger: Color(0xFFFF6B8A),
    badges: [Color(0xFFFF6E52), Color(0xFF5AA7FF), Color(0xFF3ECF8E), Color(0xFFF5A524)],
    badgeTint: 0.16,
    brightness: Brightness.dark,
  );

  /// The palette for the current system setting.
  static AppColors of(BuildContext context) =>
      MediaQuery.platformBrightnessOf(context) == Brightness.dark ? dark : light;
}

class Space {
  static const double xs = 4, sm = 8, smPlus = 12, md = 16, lg = 24, xl = 32;
}

class Radii {
  static const double sm = 8, md = 12, lg = 16, full = 999;
}

class Sizes {
  static const double borderWidth = 1;
  static const double buttonHeight = 44, fieldHeight = 44;
  static const double buttonIcon = 18;
  static const double badge = 40, badgeIcon = 20;
  static const double badgeLarge = 56, badgeIconLarge = 28;
  static const double tabIcon = 22, tabBarHeight = 56;
}

/// Text styles, 28/500 and so on, as (size, weight) from the spec.
class AppText {
  static const _family = 'PlusJakartaSans';
  static const monoFamily = 'Menlo'; // system monospace on iOS; Android falls back to 'monospace'
  static const monoFallback = ['monospace', 'Roboto Mono'];

  static TextStyle _s(double size, FontWeight w, Color c) =>
      TextStyle(fontFamily: _family, fontSize: size, fontWeight: w, color: c, height: 1.3, letterSpacing: 0);

  static TextStyle screenTitle(AppColors c) => _s(28, FontWeight.w500, c.textPrimary);
  static TextStyle cardTitle(AppColors c) => _s(17, FontWeight.w500, c.textPrimary);
  static TextStyle figure(AppColors c) => _s(28, FontWeight.w500, c.textPrimary);
  static TextStyle body(AppColors c) => _s(15, FontWeight.w400, c.textPrimary);
  static TextStyle label(AppColors c) => _s(13, FontWeight.w500, c.textSecondary);
  static TextStyle small(AppColors c) => _s(12, FontWeight.w400, c.textMuted);
  static TextStyle pill(AppColors c) => _s(12, FontWeight.w500, c.textSecondary);
  static TextStyle button(Color color) => _s(15, FontWeight.w600, color);
  static TextStyle mono(AppColors c, {Color? color}) => TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 13,
    letterSpacing: 0,
    color: color ?? c.textPrimary,
  );
}

/// Remix Icon codepoints (remixicon.ttf), drawn as text by the Glyph component.
class Ri {
  static const store = 0xF1A5, pulse = 0xF035, settings = 0xF0E6;
  static const cup = 0xEC06, bookOpen = 0xEADB, tShirt = 0xF1D9;
  static const arrowLeft = 0xEA64, arrowRight = 0xEA6E;
  static const externalLink = 0xECAF, userAdd = 0xF25E;
  static const shoppingBag = 0xF116, refund = 0xF066, refresh = 0xF064;
}

/// The Material theme only carries what Material widgets read on their own
/// (selection handles, the switch, ink), taken from the same tokens.
ThemeData buildTheme(AppColors c) => ThemeData(
  useMaterial3: true,
  brightness: c.brightness,
  fontFamily: 'PlusJakartaSans',
  scaffoldBackgroundColor: c.bgPrimary,
  colorScheme: ColorScheme.fromSeed(
    seedColor: c.primary,
    brightness: c.brightness,
    primary: c.primary,
    onPrimary: c.onPrimary,
    surface: c.bgSecondary,
  ),
  textSelectionTheme: TextSelectionThemeData(cursorColor: c.primary, selectionHandleColor: c.primary),
  splashFactory: NoSplash.splashFactory,
);
