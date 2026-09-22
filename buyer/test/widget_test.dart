import 'package:flutter_test/flutter_test.dart';
import 'package:fintap_buyer/main.dart';

void main() {
  testWidgets('buyer app title renders', (tester) async {
    await tester.pumpWidget(const BuyerApp());
    expect(find.text('FinTap Buyer'), findsOneWidget);
  });
}
