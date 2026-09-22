import 'package:flutter/material.dart';
import 'package:fintap_buyer/catalog_screen.dart';
import 'package:fintap_buyer/login_screen.dart';
import 'package:fintap_buyer/logo.dart';
import 'package:fintap_buyer/session.dart';
import 'package:fintap_buyer/theme.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final name = TextEditingController();
  final mobile = TextEditingController();
  final pin = TextEditingController();
  final confirmPin = TextEditingController();
  final address = TextEditingController();
  bool loading = false;
  String? error;

  @override
  void dispose() {
    name.dispose();
    mobile.dispose();
    pin.dispose();
    confirmPin.dispose();
    address.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (name.text.trim().isEmpty || mobile.text.trim().length < 10) {
      setState(() => error = 'Enter your name and a 10-digit mobile number');
      return;
    }
    if (pin.text.trim().length < 4 || pin.text != confirmPin.text) {
      setState(() => error = 'Use a 4–6 digit PIN and confirm it');
      return;
    }
    setState(() {
      loading = true;
      error = null;
    });
    try {
      await buyerSession.register(
        name: name.text.trim(),
        mobile: mobile.text.trim(),
        pin: pin.text.trim(),
        address: address.text.trim(),
      );
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
            child: Icon(Icons.shopping_bag_outlined, color: FtColors.navy, size: 36),
          ),
          const SizedBox(height: 16),
          const Text('Create your buyer account', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: FtColors.navy)),
          const SizedBox(height: 6),
          const Text('Register to request products from FinTap sellers.', style: TextStyle(color: FtColors.muted)),
          const SizedBox(height: 24),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  TextField(
                    controller: name,
                    decoration: const InputDecoration(labelText: 'Your name', prefixIcon: Icon(Icons.person_outline)),
                  ),
                  const SizedBox(height: 12),
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
                    decoration: const InputDecoration(labelText: '4–6 digit PIN', prefixIcon: Icon(Icons.lock_outline)),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: confirmPin,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Confirm PIN', prefixIcon: Icon(Icons.lock_outline)),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: address,
                    decoration: const InputDecoration(labelText: 'Delivery address', prefixIcon: Icon(Icons.home_outlined)),
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
            child: Text(loading ? 'Creating account…' : 'Register'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const BuyerLoginScreen()),
            ),
            child: const Text('Already registered? Sign in'),
          ),
        ],
      ),
    );
  }
}
