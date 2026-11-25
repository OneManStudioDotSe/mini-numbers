import SwiftUI

/// A Remix icon, drawn as a character of remixicon.ttf.
struct RemixIcon: View {
    let icon: Theme.Icon
    var size: CGFloat = 20

    var body: some View {
        Text(icon.glyph)
            .font(.custom("remixicon", size: size))
            .accessibilityHidden(true)
    }
}

/// Scrolling screen on bgPrimary with the title block (lg above, md below) and md between cards.
struct Screen<Content: View>: View {
    var title: String?
    var subtitle: String?
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.md) {
                if let title {
                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                        Text(title).font(Theme.Typography.screenTitle).foregroundStyle(Theme.textPrimary)
                        if let subtitle {
                            Text(subtitle).font(Theme.Typography.body).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    .padding(.top, Theme.Space.lg)
                    .padding(.bottom, Theme.Space.xs)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isHeader)
                }
                content
            }
            .padding(.horizontal, Theme.Space.lg)
            .padding(.bottom, Theme.Space.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.bgPrimary)
    }
}

/// bgSecondary, 1 px border, radius lg, padding md, no shadow. Optional title above the content.
struct Card<Content: View>: View {
    var title: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.sm) {
            if let title {
                Text(title).font(Theme.Typography.cardTitle).foregroundStyle(Theme.textPrimary)
            }
            content
        }
        .padding(Theme.Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.bgSecondary, in: RoundedRectangle(cornerRadius: Theme.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.lg).stroke(Theme.border, lineWidth: 1))
    }
}

/// Height 44, fully rounded, full width. `primary` is filled, `ghost` is bgTertiary.
struct PillButton: View {
    enum Style { case primary, ghost }

    let title: String
    var icon: Theme.Icon?
    var style: Style = .primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Theme.Space.sm) {
                if let icon { RemixIcon(icon: icon, size: 18) }
                Text(title).font(Theme.Typography.button)
            }
            .foregroundStyle(style == .primary ? Theme.onPrimary : Theme.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: Theme.controlHeight)
            .background(style == .primary ? Theme.primary : Theme.bgTertiary, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

/// 40 x 40 (56 x 56 large), radius md, badge colour at its tint with the icon in the badge colour.
struct IconBadge: View {
    let icon: Theme.Icon
    let badge: Theme.Badge
    var large = false

    var body: some View {
        RemixIcon(icon: icon, size: large ? 28 : 20)
            .foregroundStyle(badge.color)
            .frame(width: large ? 56 : 40, height: large ? 56 : 40)
            .background(badge.tint, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
    }
}

/// A label above a figure, on its own bordered tile.
struct StatTile: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Text(label).font(Theme.Typography.label).foregroundStyle(Theme.textSecondary)
            Text(value).font(Theme.Typography.figure).foregroundStyle(Theme.textPrimary)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .padding(Theme.Space.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.bgSecondary, in: RoundedRectangle(cornerRadius: Theme.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.lg).stroke(Theme.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

/// One line of the tracker log: type pill, label, time, status.
struct LogRow: View {
    let entry: LogEntry

    private static let timeFormat: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    private var statusColor: Color {
        switch entry.status {
        case "202": Theme.success
        case "not sent": Theme.textMuted
        default: Theme.danger
        }
    }

    var body: some View {
        HStack(spacing: Theme.Space.sm) {
            Text(entry.type)
                .font(Theme.Typography.smallMedium).foregroundStyle(Theme.textPrimary)
                .padding(.horizontal, Theme.Space.sm).padding(.vertical, Theme.Space.xs)
                .background(Theme.bgTertiary, in: Capsule())
            Text(entry.label)
                .font(Theme.Typography.body).foregroundStyle(Theme.textPrimary)
                .lineLimit(1).truncationMode(.tail)
            Spacer(minLength: Theme.Space.xs)
            Text(Self.timeFormat.string(from: entry.time))
                .font(Theme.Typography.small).foregroundStyle(Theme.textMuted)
            Text(entry.status)
                .font(Theme.Typography.label).foregroundStyle(statusColor)
        }
        .padding(.vertical, Theme.Space.xs)
    }
}

/// A label above an input. The border turns primary while the field has focus.
struct Field: View {
    let label: String
    @Binding var text: String
    var monospaced = false
    var keyboard: UIKeyboardType = .default
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Text(label).font(Theme.Typography.label).foregroundStyle(Theme.textSecondary)
            TextField("", text: $text)
                .font(monospaced ? Theme.Typography.mono : Theme.Typography.body)
                .foregroundStyle(Theme.textPrimary)
                .tint(Theme.primary)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focused)
                .padding(.horizontal, Theme.Space.md)
                .frame(height: Theme.controlHeight)
                .background(Theme.bgTertiary, in: RoundedRectangle(cornerRadius: Theme.Radius.md))
                .overlay(RoundedRectangle(cornerRadius: Theme.Radius.md)
                    .stroke(focused ? Theme.primary : Theme.border, lineWidth: 1))
                .accessibilityLabel(label)
        }
    }
}

/// Title and text on the left, the system switch (tinted primary) on the right.
struct ToggleRow: View {
    let title: String
    let text: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: Theme.Space.md) {
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text(title).font(Theme.Typography.body).foregroundStyle(Theme.textPrimary)
                Text(text).font(Theme.Typography.small).foregroundStyle(Theme.textSecondary)
            }
            Spacer(minLength: Theme.Space.sm)
            Toggle(title, isOn: $isOn).labelsHidden().tint(Theme.primary)
        }
    }
}
