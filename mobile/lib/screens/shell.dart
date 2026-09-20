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

  @override
  Widget build(BuildContext context) {
    const pages = [
      HomeScreen(),
      PayScreen(),
      OndcScreen(),
      KhataScreen(),
      InsightsScreen(),
    ];
    return Scaffold(
      body: pages[index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.grid_view_outlined), selectedIcon: Icon(Icons.grid_view_rounded), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.contactless_outlined), selectedIcon: Icon(Icons.contactless), label: 'Pay'),
          NavigationDestination(icon: Icon(Icons.travel_explore_outlined), selectedIcon: Icon(Icons.travel_explore), label: 'ONDC'),
          NavigationDestination(icon: Icon(Icons.menu_book_outlined), selectedIcon: Icon(Icons.menu_book), label: 'Khata'),
          NavigationDestination(icon: Icon(Icons.auto_awesome_outlined), selectedIcon: Icon(Icons.auto_awesome), label: 'AI'),
        ],
      ),
    );
  }
}
