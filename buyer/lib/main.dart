import 'package:flutter/material.dart';
import 'package:fintap_buyer/catalog_screen.dart';
import 'package:fintap_buyer/register_screen.dart';
import 'package:fintap_buyer/session.dart';
import 'package:fintap_buyer/theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const BuyerApp());
}

class BuyerApp extends StatelessWidget {
  const BuyerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinTap Buyer',
      debugShowCheckedModeBanner: false,
      theme: buyerTheme(),
      builder: (context, child) => BuyerBackdrop(child: child ?? const SizedBox.shrink()),
      home: const BuyerBootstrap(),
    );
  }
}

class BuyerBootstrap extends StatefulWidget {
  const BuyerBootstrap({super.key});

  @override
  State<BuyerBootstrap> createState() => _BuyerBootstrapState();
}

class _BuyerBootstrapState extends State<BuyerBootstrap> {
  bool loading = true;
  bool signedIn = false;

  @override
  void initState() {
    super.initState();
    buyerSession.restore().then((_) {
      if (!mounted) return;
      setState(() {
        signedIn = buyerSession.token != null;
        loading = false;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(
        backgroundColor: FtColors.navy,
        body: Center(child: CircularProgressIndicator(color: Colors.white)),
      );
    }
    return signedIn ? const CatalogScreen() : const RegisterScreen();
  }
}
