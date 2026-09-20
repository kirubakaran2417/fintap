import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
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
      setState(() {
        orders = loadedOrders;
        catalog = loadedCatalog;
        status = loadedStatus['ondc'] as Map<String, dynamic>?;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> _scan() async {
    setState(() => busy = true);
    try {
      await api.generateSku(barcode.text, hint.text);
      await _load();
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _toast(Future<Map<String, dynamic>> action, String okText) async {
    final result = await action;
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(result['ok'] == true ? okText : '${result['error']}')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final live = status?['live'] == true;
    return Scaffold(
      appBar: AppBar(title: const FinTapMark(light: true, compact: true)),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              color: FtColors.navy,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(live ? 'ONDC sandbox connected' : 'ONDC running locally', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 16)),
                    const SizedBox(height: 6),
                    Text(
                      live
                          ? 'Signed /search is enabled for ${status?['subscriberId']}'
                          : 'Set ONDC_ENABLED and subscriber keys, then ping the mock BPP.',
                      style: TextStyle(color: Colors.white.withValues(alpha:0.8)),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      children: [
                        FilledButton.tonal(
                          onPressed: () => _toast(api.pingOndc(), 'Mock BPP /search reached'),
                          child: const Text('Ping sandbox'),
                        ),
                        OutlinedButton(
                          style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: const BorderSide(color: Colors.white54)),
                          onPressed: () => _toast(api.lookupOndc(), 'Registry lookup sent'),
                          child: const Text('Registry lookup'),
                        ),
                        OutlinedButton(
                          style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: const BorderSide(color: Colors.white54)),
                          onPressed: () async {
                            final keys = await api.generateOndcKeys();
                            await Clipboard.setData(ClipboardData(text: '${keys['signingPublicKey']}'));
                            if (!context.mounted) return;
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('New public key copied. Save the private key in ONDC_SIGNING_PRIVATE_KEY.')),
                            );
                          },
                          child: const Text('Generate keys'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 18),
            const Text('Buyer-app orders', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
            const SizedBox(height: 8),
            if (orders.isEmpty) const Text('No ONDC orders yet. Confirm callbacks land as NEW orders.', style: TextStyle(color: FtColors.muted)),
            ...orders.map((item) {
              final map = item as Map<String, dynamic>;
              return Card(
                child: ListTile(
                  title: Text('${map['orderRef']} · ${map['buyerApp']}'),
                  subtitle: Text('${map['itemsSummary']}\n${map['status']}'),
                  isThreeLine: true,
                  trailing: Text(inr.format(asNum(map['amount']))),
                  onTap: () async {
                    await api.updateOrder(asInt(map['id']), 'ACCEPTED');
                    await _load();
                  },
                ),
              );
            }),
            const SizedBox(height: 18),
            const Text('Catalogue for ONDC', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
            const SizedBox(height: 8),
            TextField(controller: barcode, decoration: const InputDecoration(labelText: 'Barcode')),
            const SizedBox(height: 8),
            TextField(controller: hint, decoration: const InputDecoration(labelText: 'Shelf / product hint')),
            const SizedBox(height: 8),
            FilledButton(onPressed: busy ? null : _scan, child: Text(busy ? 'Generating…' : 'Generate listing')),
            const SizedBox(height: 8),
            ...catalog.map((item) {
              final map = item as Map<String, dynamic>;
              final published = map['publishedToOndc'] == true;
              return Card(
                child: ListTile(
                  title: Text(map['name'].toString()),
                  subtitle: Text('₹${map['sellingPrice']} · stock ${map['stock']}'),
                  trailing: published
                      ? const Chip(label: Text('Published'))
                      : TextButton(
                          onPressed: () async {
                            await api.publishSku(asInt(map['id']));
                            await _load();
                          },
                          child: const Text('Publish'),
                        ),
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}
