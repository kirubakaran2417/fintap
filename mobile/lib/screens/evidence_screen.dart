import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class EvidenceScreen extends StatefulWidget {
  const EvidenceScreen({super.key});

  @override
  State<EvidenceScreen> createState() => _EvidenceScreenState();
}

class _EvidenceScreenState extends State<EvidenceScreen> {
  Map<String, dynamic>? data;
  Map<String, dynamic>? status;
  String? error;
  bool busy = false;
  final merchantId = TextEditingController();
  final apiPassword = TextEditingController();
  final rzpKey = TextEditingController();
  final rzpSecret = TextEditingController();

  @override
  void dispose() {
    merchantId.dispose();
    apiPassword.dispose();
    rzpKey.dispose();
    rzpSecret.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final evidence = await api.demoEvidence();
      final integrations = await api.integrationStatus();
      setState(() {
        data = evidence;
        status = integrations;
        error = null;
      });
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  Future<void> _run(Future<Map<String, dynamic>> action, String okText) async {
    setState(() => busy = true);
    try {
      final result = await action;
      await _load();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(result['ok'] == true ? okText : '${result['error'] ?? result}')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _copy(String label, String value) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Copied $label')));
  }

  @override
  Widget build(BuildContext context) {
    final ondcLive = (status?['ondc'] as Map?)?['live'] == true;
    final rzpReady = (status?['razorpay'] as Map?)?['ready'] == true;
    final runtimeCredentialsAllowed = (status?['live'] as Map?)?['runtimeCredentialsAllowed'] == true;
    return Scaffold(
      appBar: AppBar(
        title: const FinTapMark(light: true, compact: true),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: data == null
          ? Center(child: error == null ? const CircularProgressIndicator() : Text(error!))
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                const Text('Demo evidence', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
                const SizedBox(height: 6),
                Text(
                  data!['disclaimer']?.toString() ?? '',
                  style: const TextStyle(color: FtColors.muted, height: 1.35),
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    _flag('ONDC', ondcLive ? 'Configured' : 'Local'),
                    const SizedBox(width: 8),
                    _flag('Razorpay', rzpReady ? 'Test live' : 'Off'),
                  ],
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: busy ? null : () => _run(api.goLive(), 'Live sandbox ping sent'),
                  child: const Text('Go live now'),
                ),
                const SizedBox(height: 16),
                _section(
                  'ONDC last ping',
                  data!['ondcPing'] as Map<String, dynamic>? ?? {},
                  actions: [
                    FilledButton(
                      onPressed: busy ? null : () => _run(api.pingOndc(), 'Ping recorded'),
                      child: const Text('Ping mock.ondc.org'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _section(
                  'ONDC registry lookup',
                  data!['ondcLookup'] as Map<String, dynamic>? ?? {},
                  actions: [
                    OutlinedButton(
                      onPressed: busy ? null : () => _run(api.lookupOndc(), 'Lookup recorded'),
                      child: const Text('Lookup registry'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _section(
                  'ONDC last asynchronous callback',
                  data!['ondcCallback'] as Map<String, dynamic>? ?? {},
                  actions: const [
                    Text(
                      'Failed callbacks show the action, transaction and error here. Transient failures are retried up to three times.',
                      style: TextStyle(color: FtColors.muted, fontSize: 13),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _section(
                  'Razorpay last order',
                  data!['razorpay'] as Map<String, dynamic>? ?? {},
                  actions: [
                    if (runtimeCredentialsAllowed) ...[
                    TextField(
                      controller: rzpKey,
                      decoration: const InputDecoration(labelText: 'Razorpay Key ID (rzp_test_...)'),
                    ),
                    TextField(
                      controller: rzpSecret,
                      obscureText: true,
                      decoration: const InputDecoration(labelText: 'Razorpay Key Secret'),
                    ),
                    FilledButton(
                      onPressed: busy
                          ? null
                          : () => _run(
                                api.saveRazorpayCredentials(rzpKey.text.trim(), rzpSecret.text.trim()),
                                'Razorpay keys saved',
                              ),
                      child: const Text('Connect Razorpay'),
                    ),
                    ] else
                      const Text(
                        'Configure Razorpay with backend environment variables. Runtime secret entry is disabled.',
                        style: TextStyle(color: FtColors.muted, fontSize: 13),
                      ),
                    const Text(
                      'Copy both values from Razorpay Dashboard → API Keys. Test keys start with rzp_test_. Then collect a card on Pay.',
                      style: TextStyle(color: FtColors.muted, fontSize: 13),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _section(
                  'Mastercard last session',
                  data!['mastercard'] as Map<String, dynamic>? ?? {},
                  actions: [
                    if (runtimeCredentialsAllowed) ...[
                    TextField(
                      controller: merchantId,
                      decoration: const InputDecoration(labelText: 'MPGS merchant ID'),
                    ),
                    TextField(
                      controller: apiPassword,
                      obscureText: true,
                      decoration: const InputDecoration(labelText: 'MPGS API password'),
                    ),
                    FilledButton(
                      onPressed: busy
                          ? null
                          : () => _run(
                                api.saveMastercardCredentials(merchantId.text.trim(), apiPassword.text.trim()),
                                'Mastercard credentials saved',
                              ),
                      child: const Text('Connect Mastercard sandbox'),
                    ),
                    ] else
                      const Text(
                        'Configure MC_MERCHANT_ID and MC_API_PASSWORD in the backend environment.',
                        style: TextStyle(color: FtColors.muted, fontSize: 13),
                      ),
                    const Text(
                      'Get these from Mastercard Merchant Manager or your acquiring bank. There is no public shared password. After connect, collect a card payment on Pay.',
                      style: TextStyle(color: FtColors.muted, fontSize: 13),
                    ),
                  ],
                ),
              ],
            ),
    );
  }

  Widget _flag(String label, String value) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(color: FtColors.muted, fontSize: 12)),
              const SizedBox(height: 4),
              Text(value, style: const TextStyle(fontWeight: FontWeight.w800)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _section(String title, Map<String, dynamic> payload, {required List<Widget> actions}) {
    final live = payload['officialHost'] == true;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16))),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: live ? const Color(0xFFE7F7F3) : const Color(0xFFF3F6F8),
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: Text(
                    live ? 'Official host' : 'Not live yet',
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: live ? FtColors.teal : FtColors.muted),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ...payload.entries.map((entry) => _row(entry.key, '${entry.value}')),
            const SizedBox(height: 12),
            const SizedBox(height: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (final action in actions) ...[
                  action,
                  const SizedBox(height: 8),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(String key, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        onTap: () => _copy(key, value),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(key, style: const TextStyle(color: FtColors.muted, fontSize: 11)),
            Text(value, style: const TextStyle(fontSize: 13, height: 1.35)),
          ],
        ),
      ),
    );
  }
}
