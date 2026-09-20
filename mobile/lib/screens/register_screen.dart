import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/login_screen.dart';
import 'package:digi_kadai/screens/onboard_screen.dart';
import 'package:digi_kadai/screens/shell.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final owner = TextEditingController();
  final mobile = TextEditingController();
  final pin = TextEditingController();
  final confirm = TextEditingController();
  bool loading = false;
  String? error;

  Future<void> _register() async {
    if (pin.text != confirm.text) {
      setState(() => error = 'PINs do not match');
      return;
    }
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await api.register(mobile: mobile.text.trim(), pin: pin.text.trim(), ownerName: owner.text.trim());
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
          const Text('Create your account', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, letterSpacing: -0.6)),
          const SizedBox(height: 6),
          const Text('Step 1 of 2 — merchant login. Store details come next.', style: TextStyle(color: FtColors.muted)),
          const SizedBox(height: 24),
          TextField(controller: owner, decoration: const InputDecoration(labelText: 'Your name', prefixIcon: Icon(Icons.person_outline))),
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
            decoration: const InputDecoration(labelText: 'Create PIN', prefixIcon: Icon(Icons.lock_outline)),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: confirm,
            obscureText: true,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Confirm PIN', prefixIcon: Icon(Icons.lock_outline)),
          ),
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(error!, style: const TextStyle(color: Color(0xFFB42318))),
          ],
          const SizedBox(height: 24),
          FilledButton(onPressed: loading ? null : _register, child: Text(loading ? 'Creating…' : 'Continue to store setup')),
          TextButton(
            onPressed: () => Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => const LoginScreen())),
            child: const Text('Already registered? Sign in'),
          ),
        ],
      ),
    );
  }
}
