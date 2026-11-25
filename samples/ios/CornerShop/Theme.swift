import SwiftUI
import UIKit

/// Every colour, size, font and icon of the app. Screens never write one directly.
/// Mirrors src/main/resources/static/css/variables.css and themes.css.
enum Theme {
    // MARK: Colours (light, dark; follow the system setting)

    private static func hex(_ value: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((value >> 16) & 0xFF) / 255, green: CGFloat((value >> 8) & 0xFF) / 255,
                blue: CGFloat(value & 0xFF) / 255, alpha: alpha)
    }

    private static func pair(_ light: UInt32, _ dark: UInt32) -> UIColor {
        UIColor { $0.userInterfaceStyle == .dark ? hex(dark) : hex(light) }
    }

    static let primary = Color(pair(0xc9340f, 0xff6e52))
    static let onPrimary = Color(pair(0xffffff, 0x190a06))
    static let bgPrimary = Color(pair(0xf4f4f5, 0x0e0e0f))
    static let bgSecondary = Color(pair(0xffffff, 0x19191b))
    static let bgTertiary = Color(pair(0xeeeef0, 0x232326))
    static let textPrimary = Color(pair(0x151517, 0xf5f5f6))
    static let textSecondary = Color(pair(0x4a4a52, 0xb6b6bd))
    static let textMuted = Color(pair(0x6a6a73, 0x9797a0))
    static let border = Color(pair(0xe3e3e7, 0x29292d))
    static let success = Color(pair(0x0b7a53, 0x3ecf8e))
    static let danger = Color(pair(0xc81e4a, 0xff6b8a))

    /// A badge colour and its tinted background (12 % in light, 16 % in dark).
    enum Badge {
        case ember, blue, green, amber

        private var colors: (light: UInt32, dark: UInt32) {
            switch self {
            case .ember: (0xc9340f, 0xff6e52)
            case .blue: (0x2f7df6, 0x5aa7ff)
            case .green: (0x12a36d, 0x3ecf8e)
            case .amber: (0xc87a00, 0xf5a524)
            }
        }

        var color: Color { Color(Theme.pair(colors.light, colors.dark)) }

        var tint: Color {
            Color(UIColor { trait in
                let dark = trait.userInterfaceStyle == .dark
                return Theme.hex(dark ? colors.dark : colors.light, alpha: dark ? 0.16 : 0.12)
            })
        }
    }

    // MARK: Spacing and radii

    enum Space {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let smPlus: CGFloat = 12
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
    }

    enum Radius {
        static let sm: CGFloat = 8
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
    }

    static let controlHeight: CGFloat = 44

    // MARK: Type (Plus Jakarta Sans, registered through UIAppFonts)

    enum Typography {
        static let screenTitle = Font.custom("PlusJakartaSans-Medium", size: 28)
        static let cardTitle = Font.custom("PlusJakartaSans-Medium", size: 17)
        static let figure = Font.custom("PlusJakartaSans-Medium", size: 28)
        static let body = Font.custom("PlusJakartaSans-Regular", size: 15)
        static let label = Font.custom("PlusJakartaSans-Medium", size: 13)
        static let small = Font.custom("PlusJakartaSans-Regular", size: 12)
        static let smallMedium = Font.custom("PlusJakartaSans-Medium", size: 12)
        static let button = Font.custom("PlusJakartaSans-SemiBold", size: 15)
        static let mono = Font.system(size: 14, design: .monospaced)
    }

    // MARK: Icons (remixicon.ttf, drawn as text)

    enum Icon: UInt32 {
        case store = 0xF1A5, pulse = 0xF035, settings = 0xF0E6
        case cup = 0xEC06, book = 0xEADB, tShirt = 0xF1D9
        case back = 0xEA64, chevron = 0xEA6E, externalLink = 0xECAF
        case userAdd = 0xF25E, bag = 0xF116, refund = 0xF066, refresh = 0xF064

        var glyph: String { String(UnicodeScalar(rawValue)!) }
    }
}
