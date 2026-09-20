import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/login_screen.dart';
import 'package:digi_kadai/screens/onboard_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Map<String, dynamic>? data;
  Map<String, dynamic>? me;
  Map<String, dynamic>? integrations;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final home = await api.home();
      final profile = await api.me();
      final status = await api.integrationStatus();
      setState(() {
        data = home;
        me = profile;
        integrations = status;
        error = null;
      });
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  Future<void> _logout() async {
    await api.clear();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const FinTapMark(light: true, compact: true),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
          IconButton(onPressed: _logout, icon: const Icon(Icons.logout)),
        ],
      ),
      body: data == null
          ? Center(child: error == null ? const CircularProgressIndicator() : Text(error!))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
                children: [
                  Text(me?['shopName']?.toString() ?? 'Your store', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
                  Text(
                    '${me?['ownerName'] ?? ''} · ${me?['city'] ?? ''}'.trim(),
                    style: const TextStyle(color: FtColors.muted),
                  ),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const OnboardScreen())),
                      icon: const Icon(Icons.edit_outlined, size: 16),
                      label: const Text('Edit store registration'),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      _metric('Today', inr.format(asNum(data!['todayRevenue']))),
                      const SizedBox(width: 8),
                      _metric('Customers', '${data!['todayCustomers']}'),
                      const SizedBox(width: 8),
                      _metric('ONDC', inr.format(asNum(data!['ondcGmv']))),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _liveCard(),
                  const SizedBox(height: 12),
                  Card(
                    color: const Color(0xFFECFDF8),
                    child: ListTile(
                      leading: const Icon(Icons.route, color: FtColors.teal),
                      title: const Text('Smart routing'),
                      subtitle: Text('${data!['routingNudge']}'),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.menu_book, color: FtColors.navy),
                      title: const Text('Khata outstanding'),
                      trailing: Text(
                        inr.format(asNum(data!['khataOutstanding'])),
                        style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
                      ),
                    ),
                  ),
                  const SizedBox(height: 18),
                  const Text('Recent activity', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  const SizedBox(height: 8),
                  ...((data!['recentPayments'] as List).map((item) {
                    final map = item as Map<String, dynamic>;
                    final card = map['rail'] == 'CARD';
                    return Card(
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: card ? const Color(0xFFE7F7F3) : const Color(0xFFEEF3F8),
                          child: Icon(card ? Icons.contactless : Icons.qr_code_2, color: FtColors.navy),
                        ),
                        title: Text(map['customerLabel']?.toString() ?? 'Customer'),
                        subtitle: Text('${map['rail']} · ${map['status']} · ${map['reference']}'),
                        trailing: Text(inr.format(asNum(map['amount'])), style: const TextStyle(fontWeight: FontWeight.w700)),
                      ),
                    );
                  })),
                ],
              ),
            ),
    );
  }

  Widget _liveCard() {
    final ondc = integrations?['ondc'] as Map<String, dynamic>? ?? {};
    final mc = integrations?['mastercardGateway'] as Map<String, dynamic>? ?? {};
    return Card(
      color: FtColors.ink,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Network status', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
            const SizedBox(height: 10),
            _pill('ONDC', ondc['live'] == true ? 'Live sandbox' : 'Local catalogue'),
            const SizedBox(height: 6),
            _pill('Mastercard', mc['gatewayReady'] == true ? 'MPGS connected' : 'Local SoftPOS'),
          ],
        ),
      ),
    );
  }

  Widget _pill(String label, String value) {
    return Row(
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: const BoxDecoration(color: FtColors.teal, shape: BoxShape.circle),
        ),
        const SizedBox(width: 8),
        Text('$label  ·  $value', style: TextStyle(color: Colors.white.withValues(alpha:0.86))),
      ],
    );
  }

  Widget _metric(String label, String value) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(color: FtColors.muted, fontSize: 12)),
              const SizedBox(height: 6),
              Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
            ],
          ),
        ),
      ),
    );
  }
}
