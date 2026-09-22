import 'package:flutter_test/flutter_test.dart';
import 'package:fintap_buyer/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('buyer app opens register', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const BuyerApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('Create your buyer account'), findsOneWidget);
  });
}
