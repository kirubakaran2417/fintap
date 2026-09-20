import 'package:digi_kadai/screens/welcome_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('FinTap welcome shows brand actions', (WidgetTester tester) async {
    await tester.pumpWidget(MaterialApp(theme: finTapTheme(), home: const WelcomeScreen()));
    expect(find.text('Create merchant account'), findsOneWidget);
    expect(find.text('I already have an account'), findsOneWidget);
  });
}
