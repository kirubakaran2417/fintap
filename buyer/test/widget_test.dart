import 'package:flutter_test/flutter_test.dart';
import 'package:fintap_buyer/main.dart';

void main() {
  testWidgets('Buyer app shows catalog chrome', (tester) async {
    await tester.pumpWidget(const FinTapBuyerApp());
    expect(find.text('FinTap Buyer'), findsOneWidget);
    expect(find.text('Catalog'), findsOneWidget);
  });
}
