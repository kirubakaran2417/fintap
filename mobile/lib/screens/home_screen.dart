import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/evidence_screen.dart';
import 'package:digi_kadai/screens/login_screen.dart';
import 'package:digi_kadai/screens/onboard_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, this.onNavigate});

  final ValueChanged<int>? onNavigate;

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

  String get _hello {
    final hour = DateTime.now().hour;
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const FinTapMark(light: true, compact: true),
        actions: [
          IconButton(
            tooltip: 'Demo evidence',
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const EvidenceScreen())),
            icon: const Icon(Icons.fact_check_outlined),
          ),
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
                  Text('$_hello${me?['ownerName'] == null ? '' : ', ${me!['ownerName']}'}', style: const TextStyle(color: FtColors.muted)),
                  Text(me?['shopName']?.toString() ?? 'Your store', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
                  Text(
                    '${me?['city'] ?? 'Add city'} · Merchant dashboard',
                    style: const TextStyle(color: FtColors.muted),
                  ),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const OnboardScreen())),
                      icon: const Icon(Icons.edit_outlined, size: 16),
                      label: const Text('Store details'),
                    ),
                  ),
                  _hero(),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      _mini('This week', inr.format(asNum(data!['weekRevenue']))),
                      const SizedBox(width: 8),
                      _mini('This month', inr.format(asNum(data!['monthRevenue']))),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _weekChart(),
                  const SizedBox(height: 12),
                  _split(),
                  const SizedBox(height: 12),
                  _grid(),
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
                  const SizedBox(height: 16),
                  const Text('Quick actions', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _action(Icons.contactless, 'Accept pay', () => widget.onNavigate?.call(1)),
                      _action(Icons.travel_explore, 'ONDC store', () => widget.onNavigate?.call(2)),
                      _action(Icons.menu_book, 'Khata', () => widget.onNavigate?.call(3)),
                      _action(Icons.auto_awesome, 'Insights', () => widget.onNavigate?.call(4)),
                    ],
                  ),
                  const SizedBox(height: 18),
                  const Text('Recent activity', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  const SizedBox(height: 8),
                  ..._recent(),
                ],
              ),
            ),
    );
  }

  Widget _hero() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF071526), Color(0xFF0B3A67), Color(0xFF0F9D8A)],
        ),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('TODAY’S COLLECTION', style: TextStyle(color: Colors.white.withValues(alpha: 0.7), fontSize: 12, letterSpacing: 1.1, fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Text(inr.format(asNum(data!['todayRevenue'])), style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800)),
          const SizedBox(height: 12),
          Row(
            children: [
              _heroChip('${data!['todayCustomers']} customers'),
              const SizedBox(width: 8),
              _heroChip('${data!['pendingPayments'] ?? 0} pending'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _heroChip(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(99)),
      child: Text(text, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }

  Widget _mini(String label, String value) {
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

  Widget _weekChart() {
    final days = (data!['last7Days'] as List?) ?? [];
    if (days.isEmpty) return const SizedBox.shrink();
    final max = days.map((d) => asNum((d as Map)['amount'])).fold<num>(1, (a, b) => a > b ? a : b);
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Last 7 days', style: TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 14),
            SizedBox(
              height: 120,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (final item in days)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: _bar(item as Map<String, dynamic>, max),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _bar(Map<String, dynamic> day, num max) {
    final amount = asNum(day['amount']);
    final height = 8.0 + (80.0 * (amount / max));
    return Column(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Container(
          height: height,
          decoration: BoxDecoration(
            color: amount == 0 ? const Color(0xFFE8EEF3) : FtColors.teal,
            borderRadius: BorderRadius.circular(8),
          ),
        ),
        const SizedBox(height: 6),
        Text('${day['label']}', style: const TextStyle(fontSize: 11, color: FtColors.muted)),
      ],
    );
  }

  Widget _split() {
    final card = asNum(data!['cardToday']);
    final upi = asNum(data!['upiToday']);
    final total = card + upi;
    final cardPct = total == 0 ? 0.5 : card / total;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Today by rail', style: TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Row(
                children: [
                  Expanded(flex: (cardPct * 100).round().clamp(1, 99), child: Container(height: 10, color: FtColors.navy)),
                  Expanded(flex: ((1 - cardPct) * 100).round().clamp(1, 99), child: Container(height: 10, color: FtColors.teal)),
                ],
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(child: Text('Card  ${inr.format(card)}', style: const TextStyle(fontSize: 13))),
                Text('UPI  ${inr.format(upi)}', style: const TextStyle(fontSize: 13)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _grid() {
    return Column(
      children: [
        Row(
          children: [
            _stat('ONDC GMV', inr.format(asNum(data!['ondcGmv'])), Icons.storefront_outlined),
            const SizedBox(width: 8),
            _stat('Open orders', '${data!['ondcOpenOrders'] ?? 0}', Icons.local_shipping_outlined),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            _stat('Catalogue', '${data!['catalogPublished'] ?? 0}/${data!['catalogTotal'] ?? 0} live', Icons.qr_code_2),
            const SizedBox(width: 8),
            _stat('Khata due', inr.format(asNum(data!['khataOutstanding'])), Icons.menu_book_outlined),
          ],
        ),
      ],
    );
  }

  Widget _stat(String label, String value, IconData icon) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, size: 18, color: FtColors.navy),
              const SizedBox(height: 10),
              Text(label, style: const TextStyle(color: FtColors.muted, fontSize: 12)),
              const SizedBox(height: 4),
              Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _action(IconData icon, String label, VoidCallback onTap) {
    return ActionChip(
      avatar: Icon(icon, size: 18, color: FtColors.navy),
      label: Text(label),
      onPressed: onTap,
      backgroundColor: Colors.white,
      side: const BorderSide(color: Color(0xFFD7E0E8)),
    );
  }

  Widget _liveCard() {
    final ondc = integrations?['ondc'] as Map<String, dynamic>? ?? {};
    final rzp = integrations?['razorpay'] as Map<String, dynamic>? ?? {};
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
            _pill('Mastercard', mc['gatewayReady'] == true ? 'MPGS connected' : 'Not configured'),
            const SizedBox(height: 6),
            _pill('Razorpay', rzp['ready'] == true ? 'Test keys live' : 'Not configured'),
            TextButton(
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const EvidenceScreen())),
              child: const Text('Open demo evidence', style: TextStyle(color: Colors.white)),
            ),
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
        Text('$label  ·  $value', style: TextStyle(color: Colors.white.withValues(alpha: 0.86))),
      ],
    );
  }

  List<Widget> _recent() {
    final rows = (data!['recentPayments'] as List?) ?? [];
    if (rows.isEmpty) {
      return [
        Card(
          child: ListTile(
            leading: const Icon(Icons.payments_outlined, color: FtColors.navy),
            title: const Text('No collections yet'),
            subtitle: const Text('Accept a UPI or card payment to populate this dashboard.'),
            trailing: TextButton(onPressed: () => widget.onNavigate?.call(1), child: const Text('Pay')),
          ),
        ),
      ];
    }
    return rows.map((item) {
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
    }).toList();
  }
}
