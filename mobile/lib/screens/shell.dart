import 'package:digi_kadai/screens/home_screen.dart';
import 'package:digi_kadai/screens/insights_screen.dart';
import 'package:digi_kadai/screens/khata_screen.dart';
import 'package:digi_kadai/screens/ondc_screen.dart';
import 'package:digi_kadai/screens/pay_screen.dart';
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
      const OndcScreen(),
      const KhataScreen(),
      const InsightsScreen(),
    ];
    return Scaffold(
      body: pages[index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: _go,
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.contactless_outlined), selectedIcon: Icon(Icons.contactless), label: 'Pay'),
          NavigationDestination(icon: Icon(Icons.travel_explore_outlined), selectedIcon: Icon(Icons.travel_explore), label: 'ONDC'),
          NavigationDestination(icon: Icon(Icons.menu_book_outlined), selectedIcon: Icon(Icons.menu_book), label: 'Khata'),
          NavigationDestination(icon: Icon(Icons.auto_awesome_outlined), selectedIcon: Icon(Icons.auto_awesome), label: 'AI'),
        ],
      ),
    );
  }
}
