import 'dart:convert';

import 'package:digi_kadai/screens/customers_screen.dart';
import 'package:digi_kadai/screens/insights_screen.dart';
import 'package:digi_kadai/screens/khata_screen.dart';
import 'package:digi_kadai/screens/ondc_screen.dart';
import 'package:digi_kadai/screens/pay_screen.dart';
import 'package:digi_kadai/screens/register_screen.dart';
import 'package:digi_kadai/screens/shell.dart';
import 'package:digi_kadai/screens/welcome_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => binding.setSurfaceSize(const Size(390, 844)));
  tearDown(() => binding.setSurfaceSize(null));

  testWidgets('FinTap welcome shows brand actions', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: WelcomeScreen()));
    expect(find.text('Create merchant account'), findsOneWidget);
    expect(find.text('I already have an account'), findsOneWidget);
  });

  testWidgets('Welcome and registration fit a narrow viewport', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 568));
    await tester.pumpWidget(const MaterialApp(home: WelcomeScreen()));
    await tester.ensureVisible(find.text('Create merchant account'));
    await tester.tap(find.text('Create merchant account'));
    await tester.pumpAndSettle();
    expect(find.byType(RegisterScreen), findsOneWidget);
    expect(find.text('Welcome to FinTap'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Payment keypad deletes digits and limits decimal places', (tester) async {
    await http.runWithClient(() async {
      await tester.pumpWidget(const MaterialApp(home: PayScreen()));
      await tester.pumpAndSettle();
      for (final digit in ['1', '2', '.', '3', '4', '5']) {
        await tester.tap(find.widgetWithText(TextButton, digit));
        await tester.pumpAndSettle();
      }
      expect(find.text('\u20b912.34'), findsOneWidget);
      await tester.tap(find.byTooltip('Delete last digit'));
      await tester.pumpAndSettle();
      expect(find.text('\u20b912.3'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }, () => MockClient(_response));
  });

  testWidgets('All six navigation destinations open the matching screen', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    await http.runWithClient(() async {
      await tester.pumpWidget(const MaterialApp(home: MerchantShell()));
      await tester.pumpAndSettle();
      final destinations = <String, Type>{
        'Pay': PayScreen,
        'Customers': CustomersScreen,
        'Khata': KhataScreen,
        'ONDC': OndcScreen,
        'Insights': InsightsScreen,
      };
      for (final destination in destinations.entries) {
        await tester.tap(find.descendant(of: find.byType(BottomNavigationBar), matching: find.text(destination.key)));
        await tester.pumpAndSettle();
        expect(find.byType(destination.value), findsOneWidget);
        expect(tester.takeException(), isNull);
      }
    }, () => MockClient(_response));
  });

  testWidgets('Customer search and segment filters change visible profiles', (tester) async {
    await http.runWithClient(() async {
      await tester.pumpWidget(const MaterialApp(home: CustomersScreen()));
      await tester.pumpAndSettle();
      expect(find.text('Priya'), findsOneWidget);
      expect(find.text('Ramesh'), findsOneWidget);
      await tester.tap(find.widgetWithText(ChoiceChip, 'At risk'));
      await tester.pumpAndSettle();
      expect(find.text('Priya'), findsOneWidget);
      expect(find.text('Ramesh'), findsNothing);
      await tester.tap(find.widgetWithText(ChoiceChip, 'All'));
      await tester.enterText(find.byType(TextField), 'ramesh');
      await tester.pumpAndSettle();
      expect(find.text('Ramesh'), findsOneWidget);
      expect(find.text('Priya'), findsNothing);
      await tester.tap(find.text('Ramesh'));
      await tester.pumpAndSettle();
      expect(find.text('Average basket'), findsOneWidget);
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
    }, () => MockClient(_response));
  });

  testWidgets('Insights period selector updates the displayed revenue period', (tester) async {
    await http.runWithClient(() async {
      await tester.pumpWidget(const MaterialApp(home: InsightsScreen()));
      await tester.pumpAndSettle();
      expect(find.text('Today revenue'), findsOneWidget);
      await tester.tap(find.text('Week'));
      await tester.pumpAndSettle();
      expect(find.text('This week revenue'), findsOneWidget);
      await tester.tap(find.text('Month'));
      await tester.pumpAndSettle();
      expect(find.text('This month revenue'), findsOneWidget);
    }, () => MockClient(_response));
  });

  testWidgets('Khata validates empty entries before submitting', (tester) async {
    await http.runWithClient(() async {
      await tester.pumpWidget(const MaterialApp(home: KhataScreen()));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Add khata entry'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save entry'));
      await tester.pumpAndSettle();
      expect(find.text('Enter a customer name'), findsOneWidget);
      expect(find.text('Enter an amount of at least 1'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(find.text('Save entry'), findsNothing);
    }, () => MockClient(_response));
  });

  testWidgets('ONDC decline sends the supported cancellation status', (tester) async {
    String? submittedStatus;
    await http.runWithClient(() async {
      await tester.pumpWidget(const MaterialApp(home: OndcScreen()));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Reject order'));
      await tester.pumpAndSettle();
      expect(submittedStatus, 'CANCELLED');
      await tester.tap(find.text('Catalogue'));
      await tester.pumpAndSettle();
      expect(find.text('Add product'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }, () => MockClient((request) async {
      if (request.url.path == '/api/ondc/orders') {
        return http.Response(jsonEncode([{'id': 1, 'orderRef': 'ORD-1', 'buyerApp': 'Test buyer', 'itemsSummary': 'Rice', 'amount': 300, 'status': submittedStatus ?? 'NEW'}]), 200);
      }
      if (request.url.path == '/api/ondc/orders/1/status') {
        submittedStatus = (jsonDecode(request.body) as Map<String, dynamic>)['status'] as String;
        return http.Response('{}', 200);
      }
      return _response(request);
    }));
  });
}

Future<http.Response> _response(http.Request request) async {
  final Object body;
  switch (request.url.path) {
    case '/api/home':
      body = {'todayRevenue': 100, 'weekRevenue': 700, 'monthRevenue': 3000, 'cardToday': 60, 'upiToday': 40, 'todayCustomers': 2, 'last7Days': [], 'recentPayments': []};
    case '/api/merchants/me':
      body = {'shopName': 'Test store', 'ownerName': 'Merchant'};
    case '/api/payments/routing':
      body = {'recommendedRail': 'CARD', 'reason': 'Test routing'};
    case '/api/customers':
      body = [
        {'displayName': 'Priya', 'visitCount': 5, 'lifetimeSpend': 1200, 'churnRisk': 0.7},
        {'displayName': 'Ramesh', 'visitCount': 8, 'lifetimeSpend': 3000, 'churnRisk': 0.1},
      ];
    case '/api/insights':
    case '/api/khata':
    case '/api/ondc/orders':
    case '/api/catalog':
      body = [];
    default:
      body = {};
  }
  return http.Response(jsonEncode(body), 200, headers: {'content-type': 'application/json'});
}
