import 'package:material_ui/material_ui.dart' hide Card;

import 'components.dart';
import 'mini_numbers.dart';
import 'theme.dart';

class Product {
  const Product(this.id, this.name, this.price, this.currency, this.icon, this.badge);
  final String id, name, currency;
  final double price;
  final int icon, badge;

  /// "€12.00", "$24.00".
  String format(double amount) {
    final symbol = currency == 'EUR' ? '€' : r'$';
    return '${amount < 0 ? '-' : ''}$symbol${amount.abs().toStringAsFixed(2)}';
  }
}

const products = [
  Product('mug', 'Mug', 12.00, 'EUR', Ri.cup, 0),
  Product('notebook', 'Notebook', 8.50, 'EUR', Ri.bookOpen, 1),
  Product('t-shirt', 'T-shirt', 24.00, 'USD', Ri.tShirt, 2),
];

class ShopScreen extends StatelessWidget {
  const ShopScreen({super.key, required this.onOpen});
  final ValueChanged<Product> onOpen;

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    return ScreenBody(
      title: 'Corner Shop',
      subtitle: 'Three things, none of them real.',
      children: [
        for (final p in products)
          GestureDetector(
            key: Key('product-${p.id}'),
            behavior: HitTestBehavior.opaque,
            onTap: () => onOpen(p),
            child: Card(
              child: Row(
                children: [
                  IconBadge(p.icon, colorIndex: p.badge),
                  const SizedBox(width: Space.smPlus),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(p.name, style: AppText.cardTitle(c)),
                        Text(p.format(p.price), style: AppText.label(c)),
                      ],
                    ),
                  ),
                  Glyph(Ri.arrowRight, size: Sizes.badgeIcon, color: c.textMuted),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class ProductScreen extends StatefulWidget {
  const ProductScreen({super.key, required this.product, required this.onBack});
  final Product product;
  final VoidCallback onBack;

  @override
  State<ProductScreen> createState() => _ProductScreenState();
}

class _ProductScreenState extends State<ProductScreen> {
  String? _sent;

  void _buy() {
    final p = widget.product;
    tracker.purchase(p.price, p.currency);
    setState(() => _sent = 'Sent purchase · ${p.format(p.price)}');
  }

  void _refund() {
    final p = widget.product;
    tracker.refund(p.price, p.currency);
    setState(() => _sent = 'Sent refund · ${p.format(-p.price)}');
  }

  @override
  Widget build(BuildContext context) {
    final c = AppColors.of(context);
    final p = widget.product;
    return ListView(
      padding: const EdgeInsets.all(Space.lg),
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: GestureDetector(
            key: const Key('back'),
            behavior: HitTestBehavior.opaque,
            onTap: widget.onBack,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: Space.sm),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Glyph(Ri.arrowLeft, size: Sizes.badgeIcon, color: c.primary),
                  Text('Shop', style: AppText.body(c).copyWith(color: c.primary)),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: Space.sm),
        Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              IconBadge(p.icon, colorIndex: p.badge, large: true),
              const SizedBox(height: Space.md),
              Text(p.name, style: AppText.screenTitle(c)),
              Text(p.format(p.price), style: AppText.figure(c)),
              const SizedBox(height: Space.sm),
              Text(
                'Buying sends a purchase event with the price as revenue. '
                'Refunding sends the same amount as a negative.',
                style: AppText.body(c).copyWith(color: c.textSecondary),
              ),
            ],
          ),
        ),
        const SizedBox(height: Space.md),
        PillButton('Buy', icon: Ri.shoppingBag, onPressed: _buy),
        const SizedBox(height: Space.sm),
        PillButton('Refund', icon: Ri.refund, style: PillStyle.ghost, onPressed: _refund),
        if (_sent != null) ...[
          const SizedBox(height: Space.md),
          Center(child: Text(_sent!, style: AppText.small(c))),
        ],
      ],
    );
  }
}
