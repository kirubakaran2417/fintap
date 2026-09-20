import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/onboard_screen.dart';
import 'package:digi_kadai/screens/register_screen.dart';
import 'package:digi_kadai/screens/shell.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final mobile = TextEditingController();
  final pin = TextEditingController();
  bool loading = false;
  String? error;

  @override
  void initState() {
    super.initState();
    if (api.lastMobile != null) {
      mobile.text = api.lastMobile!;
    }
  }

  Future<void> _login() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await api.login(mobile.text.trim(), pin.text.trim());
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => result['onboarded'] == true ? const MerchantShell() : const OnboardScreen()),
        (_) => false,
      );
    } catch (e) {
      setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const FinTapMark(light: true, compact: true)),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text('Welcome back', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, letterSpacing: -0.6)),
          const SizedBox(height: 6),
          const Text('Sign in with your registered mobile and PIN.', style: TextStyle(color: FtColors.muted)),
          const SizedBox(height: 28),
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
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(error!, style: const TextStyle(color: Color(0xFFB42318))),
          ],
          const SizedBox(height: 24),
          FilledButton(onPressed: loading ? null : _login, child: Text(loading ? 'Signing in…' : 'Sign in')),
          const SizedBox(height: 16),
          TextButton(
            onPressed: () => Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => const RegisterScreen())),
            child: const Text('New merchant? Create an account'),
          ),
          TextButton(
            onPressed: () {
              mobile.text = '9876543210';
              pin.text = '1234';
            },
            child: const Text('Fill demo shop (Lakshmi Kirana)'),
          ),
        ],
      ),
    );
  }
}
