import 'package:digi_kadai/screens/customers_screen.dart';
import 'package:digi_kadai/screens/home_screen.dart';
import 'package:digi_kadai/screens/insights_screen.dart';
import 'package:digi_kadai/screens/khata_screen.dart';
import 'package:digi_kadai/screens/ondc_screen.dart';
import 'package:digi_kadai/screens/pay_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:flutter/material.dart';

class MerchantShell extends StatefulWidget {
  const MerchantShell({super.key});

  @override
  State<MerchantShell> createState() => _MerchantShellState();
}

class _MerchantShellState extends State<MerchantShell> {
  int index = 0;

  void _go(int value) => setState(() => index = value);

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomeScreen(onNavigate: _go),
      const PayScreen(),
      const CustomersScreen(),
      const KhataScreen(),
      const OndcScreen(),
      const InsightsScreen(),
    ];
    return Scaffold(
      body: pages[index],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: index,
        onTap: _go,
        type: BottomNavigationBarType.fixed,
        backgroundColor: Colors.white,
        selectedItemColor: FtColors.navy,
        unselectedItemColor: FtColors.muted,
        selectedFontSize: 9,
        unselectedFontSize: 9,
        iconSize: 22,
        elevation: 4,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home_outlined), activeIcon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(icon: Icon(Icons.credit_card_outlined), activeIcon: Icon(Icons.credit_card), label: 'Pay'),
          BottomNavigationBarItem(icon: Icon(Icons.people_outline), activeIcon: Icon(Icons.people), label: 'Customers'),
          BottomNavigationBarItem(icon: Icon(Icons.menu_book_outlined), activeIcon: Icon(Icons.menu_book), label: 'Khata'),
          BottomNavigationBarItem(icon: Icon(Icons.storefront_outlined), activeIcon: Icon(Icons.storefront), label: 'ONDC'),
          BottomNavigationBarItem(icon: Icon(Icons.bar_chart_outlined), activeIcon: Icon(Icons.bar_chart), label: 'Insights'),
        ],
      ),
    );
  }
}
