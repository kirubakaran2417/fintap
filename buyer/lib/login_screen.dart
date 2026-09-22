import 'package:flutter/material.dart';
import 'package:fintap_buyer/catalog_screen.dart';
import 'package:fintap_buyer/logo.dart';
import 'package:fintap_buyer/register_screen.dart';
import 'package:fintap_buyer/session.dart';
import 'package:fintap_buyer/theme.dart';

class BuyerLoginScreen extends StatefulWidget {
  const BuyerLoginScreen({super.key});

  @override
  State<BuyerLoginScreen> createState() => _BuyerLoginScreenState();
}

class _BuyerLoginScreenState extends State<BuyerLoginScreen> {
  final mobile = TextEditingController();
  final pin = TextEditingController();
  bool loading = false;
  String? error;

  @override
  void dispose() {
    mobile.dispose();
    pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      await buyerSession.login(mobile: mobile.text.trim(), pin: pin.text.trim());
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const CatalogScreen()),
        (_) => false,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const FinTapMark(light: true, compact: true),
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 8),
          const Align(
            alignment: Alignment.centerLeft,
            child: Icon(Icons.storefront_outlined, color: FtColors.navy, size: 36),
          ),
          const SizedBox(height: 16),
          const Text('Welcome back', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: FtColors.navy)),
          const SizedBox(height: 6),
          const Text('Sign in to request products.', style: TextStyle(color: FtColors.muted)),
          const SizedBox(height: 24),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  TextField(
                    controller: mobile,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(labelText: 'Mobile number', prefixIcon: Icon(Icons.phone_android)),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: pin,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'PIN', prefixIcon: Icon(Icons.lock_outline)),
                  ),
                ],
              ),
            ),
          ),
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(error!, style: const TextStyle(color: FtColors.danger)),
          ],
          const SizedBox(height: 24),
          FilledButton(
            onPressed: loading ? null : _submit,
            child: Text(loading ? 'Signing in…' : 'Sign in'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const RegisterScreen()),
            ),
            child: const Text('New here? Create an account'),
          ),
        ],
      ),
    );
  }
}
