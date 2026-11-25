// The building blocks every screen is made of. They read all values from theme.dart.
//
// Material also has a `Card`. We hide it on import and define our own with the same name,
// so screens that import this file get the spec's Card (no shadow, 1 px border).
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart' hide Card;

import 'theme.dart';

/// A Remix icon drawn as text.
class Glyph extends StatelessWidget {
  const Glyph(this.codepoint, {super.key, required this.size, required this.color});
  final int codepoint;
  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) => Text(
    String.fromCharCode(codepoint),
    style: TextStyle(fontFamily: 'RemixIcon', fontSize: size, color: color, height: 1),
  );
}

class Card extends StatelessWidget {
  const Card({super.key, this.title, required this.child});
  final String? title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: c.bgSecondary,
        border: Border.all(color: c.border, width: Sizes.borderWidth),
        borderRadius: BorderRadius.circular(Radii.lg),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null) ...[Text(title!, style: AppText.cardTitle(c)), const SizedBox(height: Space.sm)],
          child,
        ],
      ),
    );
  }
}

enum PillStyle { primary, ghost }

class PillButton extends StatelessWidget {
  const PillButton(
    this.label, {
    super.key,
    required this.onPressed,
    this.icon,
    this.style = PillStyle.primary,
  });
  final String label;
  final int? icon;
  final PillStyle style;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    final primary = style == PillStyle.primary;
    final fg = primary ? c.onPrimary : c.textPrimary;
    return Material(
      color: primary ? c.primary : c.bgTertiary,
      shape: const StadiumBorder(),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onPressed,
        child: SizedBox(
          height: Sizes.buttonHeight,
          width: double.infinity,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (icon != null) ...[
                Glyph(icon!, size: Sizes.buttonIcon, color: fg),
                const SizedBox(width: Space.sm),
              ],
              Text(label, style: AppText.button(fg)),
            ],
          ),
        ),
      ),
    );
  }
}

/// [colorIndex] picks badge1..badge4.
class IconBadge extends StatelessWidget {
  const IconBadge(this.icon, {super.key, required this.colorIndex, this.large = false});
  final int icon;
  final int colorIndex;
  final bool large;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    final color = c.badges[colorIndex];
    final box = large ? Sizes.badgeLarge : Sizes.badge;
    return Container(
      width: box,
      height: box,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: color.withValues(alpha: c.badgeTint),
        borderRadius: BorderRadius.circular(Radii.md),
      ),
      child: Glyph(icon, size: large ? Sizes.badgeIconLarge : Sizes.badgeIcon, color: color),
    );
  }
}

class StatTile extends StatelessWidget {
  const StatTile({super.key, required this.label, required this.value});
  final String label, value;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return Container(
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: c.bgSecondary,
        border: Border.all(color: c.border, width: Sizes.borderWidth),
        borderRadius: BorderRadius.circular(Radii.lg),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppText.label(c)),
          const SizedBox(height: Space.xs),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(value, style: AppText.figure(c), maxLines: 1),
          ),
        ],
      ),
    );
  }
}

/// One row of the event log.
class LogRow extends StatelessWidget {
  const LogRow({
    super.key,
    required this.type,
    required this.label,
    required this.time,
    required this.status,
  });
  final String type, label, time, status;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    final statusColor = status == '202' ? c.success : (status == 'not sent' ? c.textMuted : c.danger);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Space.xs),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: Space.sm, vertical: Space.xs),
            decoration: BoxDecoration(color: c.bgTertiary, borderRadius: BorderRadius.circular(Radii.full)),
            child: Text(type, style: AppText.pill(c)),
          ),
          const SizedBox(width: Space.sm),
          Expanded(
            child: Text(label, style: AppText.body(c), maxLines: 1, overflow: TextOverflow.ellipsis),
          ),
          const SizedBox(width: Space.sm),
          Text(time, style: AppText.small(c)),
          const SizedBox(width: Space.sm),
          Text(status, style: AppText.label(c).copyWith(color: statusColor)),
        ],
      ),
    );
  }
}

class Field extends StatelessWidget {
  const Field({super.key, required this.label, required this.controller, this.mono = false, this.fieldKey});
  final String label;
  final TextEditingController controller;
  final bool mono;
  final Key? fieldKey;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    OutlineInputBorder border(Color color) => OutlineInputBorder(
      borderRadius: BorderRadius.circular(Radii.md),
      borderSide: BorderSide(color: color, width: Sizes.borderWidth),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AppText.label(c)),
        const SizedBox(height: Space.xs),
        SizedBox(
          height: Sizes.fieldHeight,
          child: TextField(
            key: fieldKey,
            controller: controller,
            autocorrect: false,
            enableSuggestions: false,
            textAlignVertical: TextAlignVertical.center,
            style: mono ? AppText.mono(c) : AppText.body(c),
            decoration: InputDecoration(
              filled: true,
              fillColor: c.bgTertiary,
              isCollapsed: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: Space.md, vertical: Space.smPlus),
              enabledBorder: border(c.border),
              focusedBorder: border(c.primary),
            ),
          ),
        ),
      ],
    );
  }
}

class ToggleRow extends StatelessWidget {
  const ToggleRow({
    super.key,
    required this.title,
    required this.text,
    required this.value,
    required this.onChanged,
  });
  final String title, text;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: AppText.body(c)),
              const SizedBox(height: Space.xs),
              Text(text, style: AppText.small(c)),
            ],
          ),
        ),
        const SizedBox(width: Space.md),
        Switch(
          value: value,
          onChanged: onChanged,
          activeThumbColor: c.onPrimary,
          activeTrackColor: c.primary,
        ),
      ],
    );
  }
}

/// A tab's scrolling page: bgPrimary behind it, the title block (lg above, md below),
/// then the children, padded lg horizontally with md between them.
class ScreenBody extends StatelessWidget {
  const ScreenBody({super.key, required this.title, required this.subtitle, required this.children});
  final String title, subtitle;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: c.brightness == Brightness.dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(Space.lg, Space.lg, Space.lg, Space.lg),
        children: [
          Text(title, style: AppText.screenTitle(c)),
          const SizedBox(height: Space.xs),
          Text(subtitle, style: AppText.body(c).copyWith(color: c.textSecondary)),
          const SizedBox(height: Space.md),
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) const SizedBox(height: Space.md),
            children[i],
          ],
        ],
      ),
    );
  }
}
