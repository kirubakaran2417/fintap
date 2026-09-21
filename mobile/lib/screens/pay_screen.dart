import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

class PayScreen extends StatefulWidget {
  const PayScreen({super.key});

  @override
  State<PayScreen> createState() => _PayScreenState();
}

class _PayScreenState extends State<PayScreen> {
  String amount = '0';
  String rail = 'CARD';
  Map<String, dynamic>? advice;
  Map<String, dynamic>? lastPayment;
  bool busy = false;
  bool gatewayReady = false;

  @override
  void initState() {
    super.initState();
    api.integrationStatus().then((status) {
      final rzp = status['razorpay'] as Map<String, dynamic>? ?? {};
      final mc = status['mastercardGateway'] as Map<String, dynamic>? ?? {};
      setState(() => gatewayReady = rzp['ready'] == true || mc['gatewayReady'] == true);
    }).catchError((_) {});
  }

  void _tap(String digit) {
    setState(() {
      if (amount == '0') {
        amount = digit;
      } else if (amount.replaceAll('.', '').length < 7) {
        amount += digit;
      }
    });
    _route();
  }

  void _dot() {
    if (!amount.contains('.')) setState(() => amount += '.');
  }

  void _clear() => setState(() => amount = '0');

  Future<void> _route() async {
    try {
      final value = double.tryParse(amount) ?? 0;
      if (value <= 0) return;
      final result = await api.routing(amount);
      setState(() {
        advice = result;
        rail = result['recommendedRail'] as String? ?? rail;
      });
    } catch (_) {}
  }

  Future<void> _collect() async {
    final value = double.tryParse(amount) ?? 0;
    if (value < 1) return;
    setState(() => busy = true);
    try {
      final payment = await api.acceptPayment(value, rail);
      setState(() => lastPayment = payment);
      if (!mounted) return;
      final checkout = payment['checkoutUrl']?.toString();
      final hosted = checkout != null && checkout.isNotEmpty && (checkout.contains('checkout/pay') || checkout.contains('razorpay') || checkout.contains('/pay/'));
      await showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        builder: (context) => Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                rail == 'CARD' ? (hosted ? 'Open live checkout' : 'Card tap collected') : 'UPI collected',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              Text('${inr.format(value)} · ${payment['status']}'),
              Text('${payment['reference']}', style: const TextStyle(color: FtColors.muted)),
              const SizedBox(height: 8),
              Text(
                hosted
                    ? 'Finish this charge on the Razorpay test checkout (or Mastercard if those keys are set).'
                    : 'Amount is already recorded locally.',
                style: const TextStyle(color: FtColors.muted),
              ),
              if (hosted) ...[
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () => launchUrl(Uri.parse(checkout), mode: LaunchMode.externalApplication),
                  child: const Text('Open checkout'),
                ),
              ],
              const SizedBox(height: 8),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
            ],
          ),
        ),
      );
      setState(() => amount = '0');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const FinTapMark(light: true, compact: true)),
      body: Column(
        children: [
          const SizedBox(height: 18),
          Text('₹$amount', style: const TextStyle(fontSize: 48, fontWeight: FontWeight.w800, letterSpacing: -1.2)),
          Text(
            gatewayReady ? 'Razorpay test checkout is live' : 'Local SoftPOS · add Razorpay or MPGS keys',
            style: const TextStyle(color: FtColors.muted),
          ),
          const SizedBox(height: 14),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'CARD', label: Text('Card / tap'), icon: Icon(Icons.contactless)),
                ButtonSegment(value: 'UPI', label: Text('UPI QR'), icon: Icon(Icons.qr_code_2)),
              ],
              selected: {rail},
              onSelectionChanged: (value) => setState(() => rail = value.first),
            ),
          ),
          if (advice != null)
            Padding(
              padding: const EdgeInsets.all(14),
              child: Text(
                '${advice!['recommendedRail']} recommended · ${advice!['reason']}',
                textAlign: TextAlign.center,
                style: const TextStyle(color: FtColors.teal),
              ),
            ),
          Expanded(
            child: GridView.count(
              crossAxisCount: 3,
              childAspectRatio: 1.45,
              padding: const EdgeInsets.all(16),
              children: [
                for (final d in ['1', '2', '3', '4', '5', '6', '7', '8', '9'])
                  TextButton(
                    onPressed: () => _tap(d),
                    child: Text(d, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w700, color: FtColors.ink)),
                  ),
                TextButton(onPressed: _dot, child: const Text('.', style: TextStyle(fontSize: 26))),
                TextButton(onPressed: () => _tap('0'), child: const Text('0', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700))),
                TextButton(onPressed: _clear, child: const Text('C', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700))),
              ],
            ),
          ),
          if (lastPayment != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text('Last: ${lastPayment!['reference']} · ${lastPayment!['status']}', style: const TextStyle(color: FtColors.muted, fontSize: 12)),
            ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton(
              onPressed: busy ? null : _collect,
              child: Text(rail == 'CARD' ? (gatewayReady ? 'Create Razorpay order' : 'Collect card (local)') : 'Collect UPI'),
            ),
          ),
        ],
      ),
    );
  }
}
