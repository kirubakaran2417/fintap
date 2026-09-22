import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:fintap_buyer/logo.dart';
import 'package:fintap_buyer/register_screen.dart';
import 'package:fintap_buyer/session.dart';
import 'package:fintap_buyer/theme.dart';
import 'package:http/http.dart' as http;

class CatalogScreen extends StatefulWidget {
  const CatalogScreen({super.key});

  @override
  State<CatalogScreen> createState() => _CatalogScreenState();
}

class _CatalogScreenState extends State<CatalogScreen> {
  List<dynamic> items = [];
  List<dynamic> orders = [];
  String? error;
  bool loading = true;
  bool busy = false;
  bool syncing = false;
  Timer? refresh;

  @override
  void initState() {
    super.initState();
    _load();
    refresh = Timer.periodic(const Duration(seconds: 6), (_) {
      if (mounted && !busy) _load(silent: true);
    });
  }

  @override
  void dispose() {
    refresh?.cancel();
    super.dispose();
  }

  Future<void> _load({bool silent = false}) async {
    if (syncing) return;
    syncing = true;
    try {
      final catalog = await http.get(Uri.parse('${apiBase()}/api/buyer/catalog'));
      if (catalog.statusCode != 200) {
        throw Exception('Catalog sync failed (${catalog.statusCode})');
      }
      final searchBody = jsonDecode(catalog.body) as Map<String, dynamic>;
      final listed = await http.get(Uri.parse('${apiBase()}/api/buyer/orders'), headers: buyerSession.headers);
      final orderBody = listed.statusCode == 200 ? jsonDecode(listed.body) : [];
      if (!mounted) return;
      setState(() {
        items = (searchBody['items'] as List?) ?? [];
        error = items.isEmpty ? 'No products available from the seller right now.' : null;
        orders = orderBody is List ? orderBody : [];
        loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        if (!silent) error = '$e';
        loading = false;
      });
    } finally {
      syncing = false;
    }
  }

  Future<void> _confirm(Map<String, dynamic> item) async {
    setState(() => busy = true);
    try {
      final response = await http.post(
        Uri.parse('${apiBase()}/api/buyer/confirm'),
        headers: buyerSession.headers,
        body: jsonEncode({'itemId': item['id'], 'quantity': 1}),
      );
      if (response.statusCode >= 400) {
        final decoded = jsonDecode(response.body);
        if (decoded is Map && (decoded['error'] != null || decoded['message'] != null)) {
          throw Exception(decoded['error'] ?? decoded['message']);
        }
        throw Exception(response.body);
      }
      await _load();
    } catch (e) {
      if (!mounted) return;
      setState(() => error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _logout() async {
    await buyerSession.clear();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const RegisterScreen()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final buyer = buyerSession.name ?? 'Buyer';
    return Scaffold(
      appBar: AppBar(
        title: const FinTapMark(light: true, compact: true),
        actions: [
          IconButton(tooltip: 'Refresh', onPressed: loading ? null : () => _load(), icon: const Icon(Icons.refresh)),
          IconButton(tooltip: 'Sign out', onPressed: _logout, icon: const Icon(Icons.logout)),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.fromLTRB(20, 16, 20, 22),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [FtColors.navy, Color.lerp(FtColors.navy, Colors.black, 0.22)!],
                      ),
                      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(18)),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Hi, $buyer', style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                        const SizedBox(height: 4),
                        const Text('Service catalog from FinTap sellers', style: TextStyle(color: Colors.white70, fontSize: 12)),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (error != null)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: Text(error!, style: const TextStyle(color: FtColors.danger)),
                          ),
                        const Text('Available products', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800)),
                        const SizedBox(height: 8),
                        if (items.isEmpty) const Text('No products available from the seller right now.', style: TextStyle(color: FtColors.muted)),
                        ...items.map((raw) {
                          final item = Map<String, dynamic>.from(raw as Map);
                          return Card(
                            child: ListTile(
                              title: Text('${item['name'] ?? 'Product'}', style: const TextStyle(fontWeight: FontWeight.w700)),
                              subtitle: Text('${_shopLabel(item['shop'])} · ₹${item['price'] ?? '0'}'),
                              trailing: FilledButton(
                                style: FilledButton.styleFrom(minimumSize: const Size(88, 40)),
                                onPressed: busy ? null : () => _confirm(item),
                                child: const Text('Request'),
                              ),
                            ),
                          );
                        }),
                        const SizedBox(height: 20),
                        const Text('Your orders', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800)),
                        const SizedBox(height: 8),
                        if (orders.isEmpty) const Text('Place a request to talk to the seller app.', style: TextStyle(color: FtColors.muted)),
                        ...orders.map((raw) {
                          final order = Map<String, dynamic>.from(raw as Map);
                          return Card(
                            child: ListTile(
                              title: Text('${order['orderRef']} · ${order['status']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                              subtitle: Text('${order['itemsSummary']} · ₹${order['amount']}'),
                            ),
                          );
                        }),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  String _shopLabel(Object? shop) {
    final value = '$shop'.trim();
    if (value.isEmpty || value == 'null' || value.contains('://') || value.toLowerCase().contains('http')) {
      return 'Seller';
    }
    return value;
  }
}
