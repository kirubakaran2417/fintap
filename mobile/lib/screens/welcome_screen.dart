import 'package:digi_kadai/screens/login_screen.dart';
import 'package:digi_kadai/screens/register_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        width: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [FtColors.navy, FtColors.primaryDark, FtColors.purple],
          ),
        ),
        child: SafeArea(
          child: LayoutBuilder(builder: (context, constraints) => SingleChildScrollView(
            padding: const EdgeInsets.all(28),
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: (constraints.maxHeight - 56).clamp(0, double.infinity).toDouble()),
              child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                  child: const FinTapLogo(size: 64),
                )),
                const SizedBox(height: 22),
                const Text(
                  'FinTap',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 34,
                    height: 1.15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                const Text('Digi Kadai', textAlign: TextAlign.center, style: TextStyle(color: Colors.white70, fontSize: 18)),
                const SizedBox(height: 40),
                FilledButton(
                  style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: FtColors.navy),
                  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RegisterScreen())),
                  child: const Text('Create merchant account'),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 52,
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.white,
                      side: const BorderSide(color: Colors.white54),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const LoginScreen())),
                    child: const Text('I already have an account'),
                  ),
                ),
                const SizedBox(height: 30),
                const Icon(Icons.storefront_outlined, color: Colors.white54, size: 24),
              ],
            ),
            ),
          )),
        ),
      ),
    );
  }
}
