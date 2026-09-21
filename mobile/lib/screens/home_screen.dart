import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/evidence_screen.dart';
import 'package:digi_kadai/screens/login_screen.dart';
import 'package:digi_kadai/screens/onboard_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
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
      if (!mounted) return;
      setState(() {
        data = home;
        me = profile;
        integrations = status;
        error = null;
      });
    } catch (e) {
      if (!mounted) return;
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
          IconButton(tooltip: 'Refresh', onPressed: _load, icon: const Icon(Icons.refresh)),
          IconButton(tooltip: 'Sign out', onPressed: _logout, icon: const Icon(Icons.logout)),
        ],
      ),
      body: data == null
          ? Center(child: error == null ? const CircularProgressIndicator() : EmptyState(icon: Icons.cloud_off_outlined, message: error!, onRetry: _load))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.only(bottom: 24),
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  _hero(),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 14),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _action(Icons.credit_card, 'Accept payment', FtColors.navy, () => widget.onNavigate?.call(1)),
                            _action(Icons.people_outline, 'Customers', FtColors.teal, () => widget.onNavigate?.call(2)),
                            _action(Icons.storefront_outlined, 'ONDC orders', FtColors.orange, () => widget.onNavigate?.call(4)),
                            _action(Icons.bar_chart, 'Insights', FtColors.purple, () => widget.onNavigate?.call(5)),
                          ],
                        ),
                        const SectionHeading('Recent transactions'),
                        ..._recent(),
                        const SectionHeading('Business overview'),
                        Row(children: [
                          _mini('This week', inr.format(asNum(data!['weekRevenue']))),
                          const SizedBox(width: 8),
                          _mini('This month', inr.format(asNum(data!['monthRevenue']))),
                        ]),
                        const SizedBox(height: 10),
                        _weekChart(),
                        const SizedBox(height: 10),
                        _split(),
                        const SizedBox(height: 10),
                        _grid(),
                        if (data!['routingNudge'] != null) ...[
                          const SizedBox(height: 10),
                          ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 8),
                            leading: const Icon(Icons.route, color: FtColors.teal),
                            title: const Text('Smart routing', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                            subtitle: Text('${data!['routingNudge']}', style: const TextStyle(fontSize: 12)),
                          ),
                        ],
                        const SectionHeading('Connections'),
                        _liveCard(),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _hero() {
    return MerchantHeader(
      title: me?['shopName']?.toString() ?? 'Your store',
      subtitle: '$_hello${me?['ownerName'] == null ? '' : ', ${me!['ownerName']}'}',
      trailing: IconButton(
        tooltip: 'Edit store details',
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const OnboardScreen())),
        icon: const Icon(Icons.edit_outlined, color: Colors.white, size: 20),
      ),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(8)),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SummaryMetric(value: inr.format(asNum(data!['todayRevenue'])), label: "Today's revenue"),
            SummaryMetric(value: '${data!['todayCustomers'] ?? 0}', label: 'Customers today'),
            SummaryMetric(value: inr.format(asNum(data!['khataOutstanding'])), label: 'Udhar pending'),
          ],
        ),
      ),
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

  Widget _action(IconData icon, String label, Color color, VoidCallback onTap) {
    return Expanded(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 3),
        child: Material(
          color: Colors.white,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8), side: const BorderSide(color: FtColors.border)),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 14),
              child: Column(children: [
                Icon(icon, color: color, size: 26),
                const SizedBox(height: 8),
                SizedBox(height: 36, child: Center(child: Text(label, textAlign: TextAlign.center, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)))),
              ]),
            ),
          ),
        ),
      ),
    );
  }

  Widget _liveCard() {
    final ondc = integrations?['ondc'] as Map<String, dynamic>? ?? {};
    final rzp = integrations?['razorpay'] as Map<String, dynamic>? ?? {};
    final mc = integrations?['mastercardGateway'] as Map<String, dynamic>? ?? {};
    return Card(
      color: Colors.white,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Network status', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 10),
            _pill('ONDC', ondc['live'] == true ? 'Live sandbox' : 'Local catalogue'),
            const SizedBox(height: 6),
            _pill('Mastercard', mc['gatewayReady'] == true ? 'MPGS connected' : 'Not configured'),
            const SizedBox(height: 6),
            _pill('Razorpay', rzp['ready'] == true ? 'Test keys live' : 'Not configured'),
            TextButton(
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const EvidenceScreen())),
              child: const Text('Open demo evidence'),
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
        Expanded(child: Text('$label  ·  $value', style: const TextStyle(color: FtColors.muted, fontSize: 12))),
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
            trailing: TextButton(onPressed: () => widget.onNavigate?.call(1), child: const Text('Pay')),
          ),
        ),
      ];
    }
    return rows.map((item) {
      final map = item as Map<String, dynamic>;
      final rail = map['rail']?.toString() ?? 'UPI';
      final card = rail == 'CARD';
      final kind = _transactionKind(rail, map['status']?.toString() ?? '');
      return Card(
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
          leading: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(color: card ? const Color(0xFFE3EAF5) : const Color(0xFFE6F4EA), borderRadius: BorderRadius.circular(8)),
            child: Icon(card ? Icons.credit_card : Icons.qr_code_2, color: card ? FtColors.navy : FtColors.teal, size: 20),
          ),
          title: Text(map['customerLabel']?.toString() ?? 'Customer', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
          subtitle: Text('$kind · ${map['status']}', style: const TextStyle(fontSize: 11, color: FtColors.muted)),
          trailing: Text(inr.format(asNum(map['amount'])), style: const TextStyle(fontSize: 13, color: FtColors.teal, fontWeight: FontWeight.w700)),
        ),
      );
    }).toList();
  }

  String _transactionKind(String rail, String status) {
    final lower = status.toLowerCase();
    if (lower.contains('ondc')) return 'ONDC order';
    if (lower.contains('khata') || lower.contains('udhar')) return 'Khata credit';
    if (rail == 'CARD') return 'Card payment';
    return 'UPI payment';
  }
}
