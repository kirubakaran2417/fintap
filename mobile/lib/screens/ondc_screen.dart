import 'dart:async';
import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/evidence_screen.dart';
import 'package:digi_kadai/services/whatsapp_service.dart';
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
  final price = TextEditingController(text: '249');
  final stock = TextEditingController(text: '24');
  final category = TextEditingController(text: 'Household');
  final deliveryRadius = TextEditingController(text: '8');
  bool busy = false;
  bool loading = true;
  String? error;
  Timer? _ticker;
  Timer? _autoRefresher;
  final Set<int> _autoDunzoTriggered = {};

  @override
  void dispose() {
    _ticker?.cancel();
    _autoRefresher?.cancel();
    barcode.dispose();
    hint.dispose();
    price.dispose();
    stock.dispose();
    category.dispose();
    deliveryRadius.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _load();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
    _autoRefresher = Timer.periodic(const Duration(seconds: 8), (_) {
      if (mounted && !busy) _load();
    });
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
                    Row(children: [
                      Expanded(child: TextField(controller: price, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'Price', prefixIcon: Icon(Icons.currency_rupee)))),
                      const SizedBox(width: 8),
                      Expanded(child: TextField(controller: stock, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Stock', prefixIcon: Icon(Icons.inventory_2_outlined)))),
                    ]),
                    const SizedBox(height: 10),
                    Row(children: [
                      Expanded(child: TextField(controller: category, decoration: const InputDecoration(labelText: 'Category', prefixIcon: Icon(Icons.category_outlined)))),
                      const SizedBox(width: 8),
                      Expanded(child: TextField(controller: deliveryRadius, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'Delivery radius (km)', prefixIcon: Icon(Icons.location_on_outlined)))),
                    ]),
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
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Icon(Icons.inventory_2_outlined, color: FtColors.purple),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(map['name'].toString(), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                                const SizedBox(height: 4),
                                Text('${inr.format(asNum(map['sellingPrice']))} · Stock: ${map['stock']} · ${map['category'] ?? 'General'}', style: const TextStyle(fontSize: 11, color: FtColors.muted)),
                              ],
                            ),
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Switch(
                                value: published,
                                activeColor: FtColors.teal,
                                onChanged: busy ? null : (_) => _update(() => api.publishSku(asInt(map['id']))),
                              ),
                              Text(published ? 'Live on ONDC' : 'Local only', style: const TextStyle(fontSize: 10, color: FtColors.muted)),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              ],
            )),
            ListView(
              padding: const EdgeInsets.all(14),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.verified, color: FtColors.teal, size: 28),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    status?['enabled'] == true ? 'ONDC Seller BPP Connected' : 'ONDC Node Initialized',
                                    style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'Subscriber ID: ${status?['subscriberId'] ?? 'fintap.local'}',
                                    style: const TextStyle(color: FtColors.muted, fontSize: 12),
                                  ),
                                ],
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              decoration: BoxDecoration(color: FtColors.teal.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(20)),
                              child: const Text('ACTIVE', style: TextStyle(color: FtColors.teal, fontWeight: FontWeight.w800, fontSize: 11)),
                            ),
                          ],
                        ),
                        const Divider(height: 24),
                        _infoRow('Network Domain', status?['domain']?.toString() ?? 'ONDC:RET10 (Grocery)'),
                        _infoRow('Beckn Core Version', '1.2.0'),
                        _infoRow('Registry Host', status?['registryUrl']?.toString() ?? 'https://preprod.registry.ondc.org'),
                        _infoRow('Mock BPP Sandbox Host', status?['mockBppUrl']?.toString() ?? 'https://mock.ondc.org/api/b2b/bpp'),
                        _infoRow('Webhook Callback URI', '${status?['subscriberId'] ?? 'fintap.local'}/protocol/v1'),
                        const SizedBox(height: 16),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.icon(
                            style: FilledButton.styleFrom(backgroundColor: FtColors.purple),
                            onPressed: busy ? null : () async {
                              setState(() => busy = true);
                              try {
                                await api.goLive();
                                await _load();
                                if (!mounted) return;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('ONDC network connection initialized successfully!')),
                                );
                              } catch (e) {
                                if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
                              } finally {
                                if (mounted) setState(() => busy = false);
                              }
                            },
                            icon: const Icon(Icons.bolt, size: 18),
                            label: const Text('Initialize Live ONDC Bootstrap'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 14),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Network Diagnostics & Actions', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.tonalIcon(
                            style: FilledButton.styleFrom(alignment: Alignment.centerLeft),
                            onPressed: busy ? null : () async {
                              setState(() => busy = true);
                              try {
                                final res = await api.pingOndc();
                                if (!mounted) return;
                                final ok = res['ok'] == true;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text(ok ? 'Ping successful! Reached mock.ondc.org (HTTP ${res['httpStatus'] ?? 200})' : 'Ping response: ${res['error'] ?? res}')),
                                );
                              } catch (e) {
                                if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
                              } finally {
                                if (mounted) setState(() => busy = false);
                              }
                            },
                            icon: const Icon(Icons.send_outlined, size: 18),
                            label: const Text('Ping ONDC Mock BPP Sandbox (mock.ondc.org)'),
                          ),
                        ),
                        const SizedBox(height: 8),
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton.icon(
                            style: OutlinedButton.styleFrom(alignment: Alignment.centerLeft),
                            onPressed: busy ? null : () async {
                              setState(() => busy = true);
                              try {
                                final res = await api.lookupOndc();
                                if (!mounted) return;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text(res['ok'] == true ? 'Registry lookup completed successfully' : 'Registry response: ${res['error'] ?? 'Lookup executed'}')),
                                );
                              } catch (e) {
                                if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
                              } finally {
                                if (mounted) setState(() => busy = false);
                              }
                            },
                            icon: const Icon(Icons.search, size: 18),
                            label: const Text('Registry Lookup (preprod.registry.ondc.org)'),
                          ),
                        ),
                        const SizedBox(height: 8),
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton.icon(
                            style: OutlinedButton.styleFrom(alignment: Alignment.centerLeft),
                            onPressed: busy ? null : () async {
                              setState(() => busy = true);
                              try {
                                final keys = await api.generateOndcKeys();
                                final pubKey = keys['signingPublicKey'] ?? (keys.isNotEmpty ? keys.values.first : 'Generated');
                                await Clipboard.setData(ClipboardData(text: '$pubKey'));
                                if (!mounted) return;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('New Ed25519 signing public key generated and copied to clipboard!')),
                                );
                              } catch (e) {
                                if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
                              } finally {
                                if (mounted) setState(() => busy = false);
                              }
                            },
                            icon: const Icon(Icons.key_outlined, size: 18),
                            label: const Text('Generate Ed25519 Cryptographic Keys'),
                          ),
                        ),
                      ],
                    ),
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

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 12, color: FtColors.muted)),
          Flexible(
            child: Text(
              value,
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  Widget _orderCard(Map<String, dynamic> order) {
    final orderId = asInt(order['id']);
    final orderStatus = order['status']?.toString() ?? 'NEW';
    final isNew = orderStatus == 'NEW';
    final isDunzo = orderStatus == 'DUNZO_PICKUP' || orderStatus == 'ASSIGNED_DUNZO';
    
    // Live countdown calculation
    final createdStr = order['createdAt']?.toString();
    final createdTime = createdStr != null ? (DateTime.tryParse(createdStr) ?? DateTime.now()) : DateTime.now();
    // 3 minute countdown duration for interactive demo
    final targetPickup = createdTime.add(const Duration(minutes: 3));
    final diff = targetPickup.difference(DateTime.now());
    final secondsLeft = diff.inSeconds;

    // Auto trigger Dunzo pickup when timer reaches 0
    if (secondsLeft <= 0 && (isNew || orderStatus == 'ACCEPTED') && !_autoDunzoTriggered.contains(orderId)) {
      _autoDunzoTriggered.add(orderId);
      Future.microtask(() => api.updateOrder(orderId, 'DUNZO_PICKUP').then((_) => _load()));
    }

    final color = isDunzo ? FtColors.teal : (isNew || orderStatus == 'CANCELLED' ? FtColors.danger : FtColors.navy);
    
    final displayStatus = isDunzo ? 'DUNZO PICKUP COMPLETED' : orderStatus;
    
    String timerText;
    if (isDunzo || orderStatus == 'DELIVERED') {
      timerText = 'Picked up by Dunzo partner';
    } else if (secondsLeft <= 0) {
      timerText = 'Dunzo pickup en route...';
    } else {
      final mins = (secondsLeft ~/ 60).toString().padLeft(2, '0');
      final secs = (secondsLeft % 60).toString().padLeft(2, '0');
      timerText = 'Pickup window: $mins:$secs';
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text('${order['orderRef']} · ${order['buyerApp']}', style: const TextStyle(fontSize: 11, color: FtColors.muted))),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(4)),
              child: Text(displayStatus, style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.w800)),
            ),
          ]),
          const SizedBox(height: 12),
          Text('${order['itemsSummary']}', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: isDunzo ? FtColors.teal.withValues(alpha: 0.08) : FtColors.orange.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(6),
            ),
            child: Row(children: [
              Icon(isDunzo ? Icons.local_shipping : Icons.timer_outlined, size: 16, color: isDunzo ? FtColors.teal : FtColors.orange),
              const SizedBox(width: 6),
              Text(timerText, style: TextStyle(fontSize: 12, color: isDunzo ? FtColors.teal : FtColors.orange, fontWeight: FontWeight.w700)),
            ]),
          ),
          const Divider(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(inr.format(asNum(order['amount'])), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
              if (isDunzo)
                const Chip(
                  avatar: Icon(Icons.check_circle, color: FtColors.teal, size: 16),
                  label: Text('Dunzo Partner Assigned', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: FtColors.teal)),
                  backgroundColor: Colors.transparent,
                  side: BorderSide.none,
                  visualDensity: VisualDensity.compact,
                ),
            ],
          ),
          if (isNew) ...[
            const SizedBox(height: 12),
            Row(children: [
              IconButton.outlined(tooltip: 'Reject order', onPressed: busy ? null : () => _update(() => api.updateOrder(orderId, 'CANCELLED')), icon: const Icon(Icons.close, color: FtColors.danger)),
              const SizedBox(width: 10),
              Expanded(child: FilledButton.icon(style: FilledButton.styleFrom(backgroundColor: FtColors.teal), onPressed: busy ? null : () => _update(() => api.updateOrder(orderId, 'ACCEPTED')), icon: const Icon(Icons.check, size: 18), label: const Text('Accept order'))),
            ]),
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: busy ? null : () => _update(() => api.updateOrder(orderId, 'DUNZO_PICKUP')),
                icon: const Icon(Icons.local_shipping_outlined),
                label: const Text('Dispatch with DUNZO'),
              ),
            ),
          ],
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            child: TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: const Color(0xFF25D366)),
              onPressed: () async {
                final orderRef = order['orderRef']?.toString() ?? 'ONDC-ORDER';
                final amt = inr.format(asNum(order['amount'])).replaceAll('₹', '').trim();
                try {
                  final res = await api.nudgeWhatsapp({
                    'type': 'ONDC_ORDER',
                    'orderRef': orderRef,
                    'amount': amt,
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
              icon: const Icon(Icons.chat, size: 18),
              label: const Text('WhatsApp Delivery Alert'),
            ),
          ),
        ]),
      ),
    );
  }
}
