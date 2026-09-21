import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/evidence_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class OndcScreen extends StatefulWidget {
  const OndcScreen({super.key});

  @override
  State<OndcScreen> createState() => _OndcScreenState();
}

class _OndcScreenState extends State<OndcScreen> {
  List<dynamic> orders = [];
  List<dynamic> catalog = [];
  Map<String, dynamic>? status;
  final barcode = TextEditingController(text: '8901725111924');
  final hint = TextEditingController(text: 'Detergent from shelf photo');
  bool busy = false;
  bool loading = true;
  String? error;

  @override
  void dispose() {
    barcode.dispose();
    hint.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final loadedOrders = await api.ondcOrders();
      final loadedCatalog = await api.catalog();
      final loadedStatus = await api.integrationStatus();
      if (!mounted) return;
      setState(() {
        orders = loadedOrders;
        catalog = loadedCatalog;
        status = loadedStatus['ondc'] as Map<String, dynamic>?;
        error = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _scan() async {
    setState(() => busy = true);
    try {
      await api.generateSku(barcode.text, hint.text);
      await _load();
    } catch (exception) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$exception')));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _toast(Future<Map<String, dynamic>> action, String okText) async {
    try {
      final result = await action;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(result['ok'] == true ? okText : '${result['error']}')));
    } catch (exception) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$exception')));
    }
  }

  Future<void> _update(Future<Map<String, dynamic>> Function() action) async {
    setState(() => busy = true);
    try {
      await action();
      await _load();
    } catch (exception) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$exception')));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final live = status?['live'] == true;
    return DefaultTabController(
      length: 3,
      child: Scaffold(
      appBar: AppBar(
        title: const Text('ONDC online store'),
        backgroundColor: FtColors.purple,
        actions: [
          IconButton(
            tooltip: 'Demo evidence',
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const EvidenceScreen())),
            icon: const Icon(Icons.fact_check_outlined),
          ),
        ],
      ),
      body: Column(
        children: [
          MerchantHeader(
            title: live ? 'Store connected' : 'Your online store',
            subtitle: live ? 'ONDC sandbox' : 'Local catalogue',
            color: FtColors.purple,
            trailing: const Icon(Icons.storefront_outlined, color: Colors.white, size: 32),
            child: Row(children: [
              SummaryMetric(value: '${orders.length}', label: 'Orders'),
              SummaryMetric(value: '${catalog.length}', label: 'Products'),
              SummaryMetric(value: '${catalog.where((item) => item['publishedToOndc'] == true).length}', label: 'Published'),
            ]),
          ),
          const Material(color: Colors.white, child: TabBar(
            labelColor: FtColors.purple,
            indicatorColor: FtColors.purple,
            tabs: [Tab(text: 'Orders'), Tab(text: 'Catalogue'), Tab(text: 'Connections')],
          )),
          if (loading) const LinearProgressIndicator(),
          if (error != null) Padding(padding: const EdgeInsets.all(8), child: TextButton.icon(onPressed: _load, icon: const Icon(Icons.refresh), label: Text(error!, maxLines: 2, overflow: TextOverflow.ellipsis))),
          Expanded(child: TabBarView(children: [
            RefreshIndicator(onRefresh: _load, child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(14),
              children: [
                if (!loading && orders.isEmpty) const EmptyState(icon: Icons.shopping_bag_outlined, message: 'No ONDC orders yet'),
                ...orders.map((item) => _orderCard(item as Map<String, dynamic>)),
              ],
            )),
            RefreshIndicator(onRefresh: _load, child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(14),
              children: [
                ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  leading: const Icon(Icons.add_circle_outline, color: FtColors.navy),
                  title: const Text('Add product', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                  children: [
                    TextField(controller: barcode, decoration: const InputDecoration(labelText: 'Barcode', prefixIcon: Icon(Icons.qr_code_scanner))),
                    const SizedBox(height: 10),
                    TextField(controller: hint, decoration: const InputDecoration(labelText: 'Product description')),
                    const SizedBox(height: 10),
                    FilledButton.icon(onPressed: busy ? null : _scan, icon: const Icon(Icons.auto_awesome, size: 18), label: Text(busy ? 'Generating...' : 'Generate listing')),
                    const SizedBox(height: 16),
                  ],
                ),
                const SizedBox(height: 12),
                if (!loading && catalog.isEmpty) const EmptyState(icon: Icons.inventory_2_outlined, message: 'No products yet'),
                ...catalog.map((item) {
                  final map = item as Map<String, dynamic>;
                  final published = map['publishedToOndc'] == true;
                  return Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      leading: const Icon(Icons.inventory_2_outlined, color: FtColors.purple),
                      title: Text(map['name'].toString(), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                      subtitle: Text('${inr.format(asNum(map['sellingPrice']))} · Stock: ${map['stock']}', style: const TextStyle(fontSize: 11, color: FtColors.muted)),
                      trailing: published
                          ? const Tooltip(message: 'Published to ONDC', child: Icon(Icons.check_circle, color: FtColors.teal))
                          : IconButton(tooltip: 'Publish to ONDC', onPressed: busy ? null : () => _update(() => api.publishSku(asInt(map['id']))), icon: const Icon(Icons.publish, color: FtColors.navy)),
                    ),
                  );
                }),
              ],
            )),
            ListView(
          padding: const EdgeInsets.all(14),
          children: [
            Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(live ? 'ONDC sandbox connected' : 'ONDC running locally', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                    const SizedBox(height: 6),
                    Text(
                      live
                          ? 'Signed /search is enabled for ${status?['subscriberId']}'
                          : 'Set ONDC_ENABLED and subscriber keys, then ping the mock BPP.',
                      style: const TextStyle(color: FtColors.muted, fontSize: 12),
                    ),
                    const SizedBox(height: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        FilledButton.tonal(
                          onPressed: () => _toast(api.pingOndc(), 'Mock BPP /search reached'),
                          child: const Text('Ping sandbox'),
                        ),
                        OutlinedButton(
                          onPressed: () => _toast(api.lookupOndc(), 'Registry lookup sent'),
                          child: const Text('Registry lookup'),
                        ),
                        OutlinedButton(
                          onPressed: () async {
                            try {
                            final keys = await api.generateOndcKeys();
                            await Clipboard.setData(ClipboardData(text: '${keys['signingPublicKey']}'));
                            if (!context.mounted) return;
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('New public key copied. Save the private key in ONDC_SIGNING_PRIVATE_KEY.')),
                            );
                            } catch (exception) {
                              if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$exception')));
                            }
                          },
                          child: const Text('Generate keys'),
                        ),
                      ],
                    ),
                  ],
                ),
            ),
          ],
            ),
          ])),
        ],
      ),
    ),
    );
  }

  Widget _orderCard(Map<String, dynamic> order) {
    final orderStatus = order['status']?.toString() ?? 'NEW';
    final isNew = orderStatus == 'NEW';
    final color = isNew || orderStatus == 'CANCELLED' ? FtColors.danger : FtColors.teal;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text('${order['orderRef']} · ${order['buyerApp']}', style: const TextStyle(fontSize: 11, color: FtColors.muted))),
            const SizedBox(width: 8),
            Container(padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4), decoration: BoxDecoration(color: color.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(4)), child: Text(orderStatus, style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.w700))),
          ]),
          const SizedBox(height: 12),
          Text('${order['itemsSummary']}', style: const TextStyle(fontSize: 13)),
          const Divider(height: 24),
          Text(inr.format(asNum(order['amount'])), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          if (isNew) ...[
            const SizedBox(height: 12),
            Row(children: [
              IconButton.outlined(tooltip: 'Reject order', onPressed: busy ? null : () => _update(() => api.updateOrder(asInt(order['id']), 'CANCELLED')), icon: const Icon(Icons.close, color: FtColors.danger)),
              const SizedBox(width: 10),
              Expanded(child: FilledButton.icon(style: FilledButton.styleFrom(backgroundColor: FtColors.teal), onPressed: busy ? null : () => _update(() => api.updateOrder(asInt(order['id']), 'ACCEPTED')), icon: const Icon(Icons.check, size: 18), label: const Text('Accept order'))),
            ]),
          ],
        ]),
      ),
    );
  }
}
