import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
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
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const BuyerApp());
}

class BuyerApp extends StatelessWidget {
  const BuyerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinTap Buyer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0F9D8A)),
        textTheme: GoogleFonts.dmSansTextTheme(),
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
  List<dynamic> items = [];
  List<dynamic> orders = [];
  String? error;
  bool loading = true;
  bool busy = false;
  String? source;
  Timer? refresh;

  @override
  void initState() {
    super.initState();
    _load(searchNetwork: true);
    refresh = Timer.periodic(const Duration(seconds: 5), (_) {
      if (mounted && !busy) _load(searchNetwork: false, silent: true);
    });
  }

  @override
  void dispose() {
    refresh?.cancel();
    super.dispose();
  }

  Future<void> _load({bool searchNetwork = false, bool silent = false}) async {
    try {
      Map<String, dynamic>? searchBody;
      if (searchNetwork) {
        final search = await http.get(Uri.parse('${apiBase()}/api/buyer/search'));
        if (search.statusCode != 200) {
          throw Exception('Search failed (${search.statusCode})');
        }
        searchBody = jsonDecode(search.body) as Map<String, dynamic>;
      }
      final listed = await http.get(Uri.parse('${apiBase()}/api/buyer/orders'));
      final orderBody = listed.statusCode == 200 ? jsonDecode(listed.body) : [];
      if (!mounted) return;
      setState(() {
        if (searchBody != null) {
          items = (searchBody['items'] as List?) ?? [];
          source = '${searchBody['source']} · gateway ${searchBody['gatewayUrl']}';
          final itemsEmpty = items.isEmpty;
          error = itemsEmpty ? searchBody['error']?.toString() : null;
        }
        orders = orderBody is List ? orderBody : [];
        loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        if (!silent) error = '$e';
        loading = false;
      });
    }
  }

  Future<void> _confirm(Map<String, dynamic> item) async {
    setState(() => busy = true);
    try {
      final response = await http.post(
        Uri.parse('${apiBase()}/api/buyer/confirm'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'itemId': item['id'], 'quantity': 1}),
      );
      if (response.statusCode >= 400) {
        throw Exception(response.body);
      }
      await _load(searchNetwork: false);
    } catch (e) {
      if (!mounted) return;
      setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF3F6FB),
      appBar: AppBar(
        title: const Text('FinTap Buyer'),
        actions: [
          IconButton(onPressed: loading ? null : () => _load(searchNetwork: true), icon: const Icon(Icons.refresh)),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (error != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(error!, style: const TextStyle(color: Colors.red)),
                  ),
                if (source != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(source!, style: const TextStyle(color: Color(0xFF5B6B7C))),
                  ),
                Text('Seller catalog', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 8),
                if (items.isEmpty) const Text('No catalog returned from ONDC yet. Search uses the preprod gateway plus a Beckn /search to the seller BPP.'),
                ...items.map((raw) {
                  final item = Map<String, dynamic>.from(raw as Map);
                  return Card(
                    child: ListTile(
                      title: Text('${item['name']}'),
                      subtitle: Text('${item['shop']} · ₹${item['price']}\n${item['bppUri'] ?? ''}'),
                      isThreeLine: true,
                      trailing: FilledButton(
                        onPressed: busy ? null : () => _confirm(item),
                        child: const Text('Request'),
                      ),
                    ),
                  );
                }),
                const SizedBox(height: 24),
                Text('Your orders', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 8),
                if (orders.isEmpty) const Text('Place a request to talk to the seller app.'),
                ...orders.map((raw) {
                  final order = Map<String, dynamic>.from(raw as Map);
                  return Card(
                    child: ListTile(
                      title: Text('${order['orderRef']} · ${order['status']}'),
                      subtitle: Text('${order['itemsSummary']} · ₹${order['amount']}'),
                    ),
                  );
                }),
              ],
            ),
    );
  }
}
