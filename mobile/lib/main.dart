import 'package:digi_kadai/api/api_client.dart';
import 'package:digi_kadai/screens/login_screen.dart';
import 'package:digi_kadai/screens/onboard_screen.dart';
import 'package:digi_kadai/screens/shell.dart';
import 'package:digi_kadai/screens/welcome_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:flutter/material.dart';

final api = ApiClient();

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const FinTapApp());
}

class FinTapApp extends StatelessWidget {
  const FinTapApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinTap',
      debugShowCheckedModeBanner: false,
      theme: finTapTheme(),
      home: const Bootstrap(),
    );
  }
}

class Bootstrap extends StatefulWidget {
  const Bootstrap({super.key});

  @override
  State<Bootstrap> createState() => _BootstrapState();
}

class _BootstrapState extends State<Bootstrap> {
  Widget? child;

  @override
  void initState() {
    super.initState();
    _boot();
  }

  Future<void> _boot() async {
    await api.restore();
    if (api.token != null) {
      try {
        final me = await api.me();
        setState(() => child = me['onboarded'] == true ? const MerchantShell() : const OnboardScreen());
        return;
      } catch (_) {
        await api.clear();
      }
    }
    setState(() => child = api.lastMobile != null ? const LoginScreen() : const WelcomeScreen());
  }

  @override
  Widget build(BuildContext context) {
    return child ??
        const Scaffold(
          backgroundColor: FtColors.ink,
          body: Center(child: CircularProgressIndicator(color: Colors.white)),
        );
  }
}
