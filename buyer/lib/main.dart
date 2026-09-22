import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

String apiBase() {
  if (kIsWeb) return 'http://localhost:8080';
  switch (defaultTargetPlatform) {
    case TargetPlatform.android:
      return 'http://10.0.2.2:8080';
    default:
      return 'http://localhost:8080';
  }
}

void main() {
  runApp(const FinTapBuyerApp());
}

class FinTapBuyerApp extends StatelessWidget {
  const FinTapBuyerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinTap Buyer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF5B2C6F), primary: const Color(0xFF5B2C6F)),
        useMaterial3: true,
      ),
      home: const BuyerHome(),
    );
  }
}

class BuyerHome extends StatefulWidget {
  const BuyerHome({super.key});

  @override
  State<BuyerHome> createState() => _BuyerHomeState();
}

class _BuyerHomeState extends State<BuyerHome> {
  final query = TextEditingController();
  List<dynamic> items = [];
  List<dynamic> orders = [];
  bool busy = false;
  String? error;
  String? networkNote;
  int tab = 0;

  @override
  void dispose() {
    query.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _search();
  }

  Future<void> _search() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final search = await _post('/api/buyer/search', {'query': query.text.trim()});
      final loadedOrders = await _getList('/api/buyer/orders');
      if (!mounted) return;
      setState(() {
        items = (search['items'] as List?) ?? [];
        orders = loadedOrders;
        final fallback = search['fallback']?.toString() ?? '';
        networkNote = fallback.isNotEmpty
            ? 'Preprod gateway unavailable — showing FinTap catalog over local ONDC protocol'
            : (search['officialHost'] == true
                ? 'Searched ONDC preprod gateway'
                : 'ONDC search completed');
      });
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _confirm(Map<String, dynamic> item) async {
    setState(() => busy = true);
    try {
      await _post('/api/buyer/confirm', {'itemId': item['id'], 'quantity': 1});
      await _search();
      if (!mounted) return;
      setState(() => tab = 1);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Request sent to ${item['providerName']}. Wait for the merchant to accept.')),
      );
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      if (mounted) setState(() => busy = false);
    }
  }

  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> body) async {
    final response = await http.post(
      Uri.parse('${apiBase()}$path'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    final decoded = jsonDecode(response.body.isEmpty ? '{}' : response.body);
    if (response.statusCode >= 400) {
      throw Exception(decoded is Map ? (decoded['error'] ?? 'Request failed') : 'Request failed');
    }
    return decoded as Map<String, dynamic>;
  }

  Future<List<dynamic>> _getList(String path) async {
    final response = await http.get(Uri.parse('${apiBase()}$path'));
    final decoded = jsonDecode(response.body.isEmpty ? '[]' : response.body);
    if (response.statusCode >= 400) throw Exception('Request failed');
    return decoded as List<dynamic>;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('FinTap Buyer'), backgroundColor: const Color(0xFF5B2C6F), foregroundColor: Colors.white),
      body: Column(
        children: [
          if (networkNote != null)
            Material(
              color: const Color(0xFFF4ECF7),
              child: ListTile(
                dense: true,
                leading: const Icon(Icons.info_outline, color: Color(0xFF5B2C6F)),
                title: Text(networkNote!, style: const TextStyle(fontSize: 12)),
              ),
            ),
          if (busy) const LinearProgressIndicator(),
          if (error != null)
            TextButton.icon(onPressed: _search, icon: const Icon(Icons.refresh), label: Text(error!, maxLines: 2)),
          Expanded(
            child: tab == 0 ? _catalog() : _orders(),
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (value) {
          setState(() => tab = value);
          if (value == 1) _search();
        },
        destinations: const [
          NavigationDestination(icon: Icon(Icons.storefront_outlined), label: 'Catalog'),
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Requests'),
        ],
      ),
    );
  }

  Widget _catalog() {
    return RefreshIndicator(
      onRefresh: _search,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: query,
            decoration: InputDecoration(
              hintText: 'Search grocery on ONDC',
              suffixIcon: IconButton(onPressed: busy ? null : _search, icon: const Icon(Icons.search)),
            ),
            onSubmitted: (_) => _search(),
          ),
          const SizedBox(height: 16),
          if (!busy && items.isEmpty) const Text('No published FinTap products yet. Publish SKUs in the merchant app, then search.'),
          ...items.map((raw) {
            final item = raw as Map<String, dynamic>;
            return Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                title: Text('${item['name']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('${item['providerName']} · ₹${item['price']}'),
                trailing: FilledButton(
                  onPressed: busy ? null : () => _confirm(item),
                  child: const Text('Request'),
                ),
              ),
            );
          }),
        ],
      ),
    );
  }

  Widget _orders() {
    return RefreshIndicator(
      onRefresh: _search,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (orders.isEmpty) const Text('No requests yet. Request a product from Catalog.'),
          ...orders.map((raw) {
            final order = raw as Map<String, dynamic>;
            return Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                title: Text('${order['name']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('${order['id']} · ₹${order['amount']}'),
                trailing: Text('${order['status']}', style: const TextStyle(fontWeight: FontWeight.w800)),
              ),
            );
          }),
        ],
      ),
    );
  }
}
