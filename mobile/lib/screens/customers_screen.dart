import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/services/whatsapp_service.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
import 'package:flutter/material.dart';

class CustomersScreen extends StatefulWidget {
  const CustomersScreen({super.key});

  @override
  State<CustomersScreen> createState() => _CustomersScreenState();
}

class _CustomersScreenState extends State<CustomersScreen> {
  List<dynamic> customers = [];
  String segment = 'All';
  String query = '';
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final loaded = await api.customers();
      if (!mounted) return;
      setState(() { customers = loaded; error = null; });
    } catch (exception) {
      if (mounted) setState(() => error = '$exception');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String _segment(Map<String, dynamic> customer) {
    final churnRisk = asNum(customer['churnRisk']);
    final visitCount = asInt(customer['visitCount']);
    final spend = asNum(customer['lifetimeSpend']);
    if (spend >= 15000 || visitCount >= 10) return 'VIP';
    if (churnRisk >= 0.5) return 'Drifting';
    if (visitCount < 3) return 'New';
    return 'Regular';
  }

  Color _color(String value) => switch (value) {
    'VIP' => const Color(0xFF0F5E9C),
    'Drifting' => FtColors.danger,
    'New' => FtColors.teal,
    _ => FtColors.navy,
  };

  void _showProfile(Map<String, dynamic> customer) {
    final visits = asInt(customer['visitCount']);
    final spend = asNum(customer['lifetimeSpend']);
    final segment = _segment(customer);
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const CircleAvatar(radius: 30, backgroundColor: FtColors.navy, child: Icon(Icons.person_outline, color: Colors.white, size: 32)),
          const SizedBox(height: 12),
          Text('${customer['displayName']}', textAlign: TextAlign.center, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          Text(segment, style: TextStyle(color: _color(segment), fontWeight: FontWeight.w700)),
          const SizedBox(height: 24),
          Row(children: [
            SummaryMetric(value: '$visits', label: 'Total visits', light: false),
            SummaryMetric(value: inr.format(spend), label: 'Total spent', light: false),
            SummaryMetric(value: inr.format(visits == 0 ? 0 : spend / visits), label: 'Average basket', light: false),
          ]),
          const Divider(height: 32),
          ListTile(contentPadding: EdgeInsets.zero, leading: const Icon(Icons.trending_down, color: FtColors.orange), title: const Text('Churn risk'), trailing: Text('${(asNum(customer['churnRisk']) * 100).round()}%')),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              style: FilledButton.styleFrom(backgroundColor: const Color(0xFF25D366)),
              onPressed: () async {
                Navigator.pop(context);
                final displayName = customer['displayName']?.toString() ?? 'Customer';
                final mobile = customer['mobile']?.toString() ?? '';
                try {
                  final res = await api.nudgeWhatsapp({
                    'mobile': mobile,
                    'type': 'REENGAGE',
                    'name': displayName,
                    'sendLive': true,
                  });
                  if (context.mounted) {
                    WhatsAppService.showLiveDeliveryModal(context, res);
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Nudge failed: $e')));
                  }
                }
              },
              icon: const Icon(Icons.chat, color: Colors.white),
              label: const Text('Send WhatsApp Discount Nudge'),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(width: double.infinity, child: OutlinedButton(onPressed: () => Navigator.pop(context), child: const Text('Close'))),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final total = customers.fold<num>(0, (sum, item) => sum + asNum(item['lifetimeSpend']));
    final atRisk = customers.where((item) => asNum(item['churnRisk']) >= 0.5).length;
    final filtered = customers.where((item) => (segment == 'All' || _segment(item as Map<String, dynamic>) == segment) && '${item['displayName']}'.toLowerCase().contains(query)).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Customers'), actions: [IconButton(tooltip: 'Refresh customers', onPressed: _load, icon: const Icon(Icons.refresh))]),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            MerchantHeader(
              title: 'Card & ONDC customers',
              subtitle: 'In-store cards and ONDC buyer apps',
              trailing: const Icon(Icons.people_outline, color: Colors.white, size: 32),
              child: Row(children: [
                SummaryMetric(value: '${customers.length}', label: 'Recognised'),
                SummaryMetric(value: '$atRisk', label: 'At risk'),
                SummaryMetric(value: inr.format(total), label: 'Lifetime revenue'),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                TextField(decoration: const InputDecoration(hintText: 'Search customers', prefixIcon: Icon(Icons.search)), onChanged: (value) => setState(() => query = value.trim().toLowerCase())),
                const SizedBox(height: 10),
                Wrap(spacing: 6, runSpacing: 6, children: [
                  for (final value in ['All', 'VIP', 'Regular', 'Drifting', 'New'])
                    ChoiceChip(label: Text(value, style: const TextStyle(fontSize: 11)), selected: segment == value, onSelected: (_) => setState(() => segment = value)),
                ]),
                if (atRisk > 0) Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    tileColor: const Color(0xFFFFF3E0),
                    leading: const Icon(Icons.error_outline, color: FtColors.orange),
                    title: Text('$atRisk customers drifting', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => setState(() => segment = 'Drifting'),
                  ),
                ),
                const SizedBox(height: 8),
                if (loading) const Center(child: CircularProgressIndicator())
                else if (error != null) EmptyState(icon: Icons.cloud_off_outlined, message: error!, onRetry: _load)
                else if (filtered.isEmpty) const EmptyState(icon: Icons.people_outline, message: 'No customers found')
                else ...filtered.map((item) {
                  final customer = item as Map<String, dynamic>;
                  final name = customer['displayName']?.toString() ?? 'Customer';
                  final customerSegment = _segment(customer);
                  final color = _color(customerSegment);
                  return Card(
                    margin: const EdgeInsets.only(bottom: 10),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(8),
                      onTap: () => _showProfile(customer),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(children: [
                          Row(children: [
                            CircleAvatar(radius: 19, backgroundColor: color, foregroundColor: Colors.white, child: Text(name.isEmpty ? '?' : name.characters.first.toUpperCase())),
                            const SizedBox(width: 10),
                            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text(name, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                              const SizedBox(height: 3),
                              Text(
                                customer['source'] == 'ONDC' ? 'ONDC buyer · ${customer['visitCount']} orders' : '${customer['visitCount']} visits',
                                style: const TextStyle(fontSize: 11, color: FtColors.muted),
                              ),
                            ])),
                            const SizedBox(width: 8),
                            Text(inr.format(asNum(customer['lifetimeSpend'])), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800)),
                          ]),
                          const Divider(height: 20),
                          Row(children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(999)),
                              child: Text(customerSegment, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: color)),
                            ),
                            const Spacer(),
                            if (asInt(customer['visitCount']) < 4)
                              const Text('Nudge', style: TextStyle(fontSize: 10, color: FtColors.orange, fontWeight: FontWeight.w700))
                            else
                              const Icon(Icons.chevron_right, size: 18, color: FtColors.muted),
                          ]),
                        ]),
                      ),
                    ),
                  );
                }),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}