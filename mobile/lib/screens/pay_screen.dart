import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/payments/payment_provider.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
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
  bool mastercardReady = false;
  bool razorpayReady = false;
  bool tapOnPhoneReady = false;
  CardPaymentProvider cardProvider = CardPaymentProvider.auto;
  final TapOnPhoneAdapter tapOnPhone = MethodChannelTapOnPhoneAdapter();

  @override
  void initState() {
    super.initState();
    api.integrationStatus().then((status) async {
      final rzp = status['razorpay'] as Map<String, dynamic>? ?? {};
      final mc = status['mastercardGateway'] as Map<String, dynamic>? ?? {};
      final tapReady = await tapOnPhone.isAvailable();
      if (!mounted) return;
      setState(() {
        mastercardReady = mc['gatewayReady'] == true;
        razorpayReady = rzp['ready'] == true;
        tapOnPhoneReady = tapReady;
        gatewayReady = razorpayReady || mastercardReady;
        cardProvider = mastercardReady
            ? CardPaymentProvider.mastercard
            : (razorpayReady ? CardPaymentProvider.razorpay : CardPaymentProvider.auto);
      });
    }).catchError((_) {});
  }

  void _tap(String digit) {
    if (busy || (amount.contains('.') && amount.split('.').last.length >= 2)) return;
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
    if (!busy && !amount.contains('.')) setState(() => amount += '.');
  }

  void _backspace() {
    if (busy) return;
    setState(() {
      amount = amount.length > 1 ? amount.substring(0, amount.length - 1) : '0';
      advice = null;
    });
    _route();
  }

  Future<void> _route() async {
    try {
      final value = double.tryParse(amount) ?? 0;
      if (value <= 0) return;
      final requestedAmount = amount;
      final result = await api.routing(requestedAmount);
      if (!mounted || busy || amount != requestedAmount) return;
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
      final payment = await api.acceptPayment(value, rail, provider: cardProvider.apiValue);
      if (!mounted) return;
      setState(() => lastPayment = payment);
      if (rail == 'CARD' && cardProvider == CardPaymentProvider.tapOnPhone && tapOnPhoneReady) {
        final sdkResult = await tapOnPhone.collect(
          orderId: payment['gatewayOrderId']?.toString() ?? '',
          sessionId: payment['gatewaySessionId']?.toString() ?? '',
        );
        final updated = await api.submitMastercardDevicePayment(
          asInt(payment['id']),
          payment['gatewaySessionId']?.toString() ?? '',
          Map<String, dynamic>.from(sdkResult['devicePayment'] as Map),
        );
        payment.addAll(updated);
        if (mounted) setState(() => lastPayment = payment);
      }
      final checkout = payment['checkoutUrl']?.toString();
      final hosted = checkout != null && checkout.isNotEmpty && (checkout.contains('checkout/pay') || checkout.contains('razorpay') || checkout.contains('/pay/'));
      final paymentStatus = payment['status']?.toString() ?? 'UNKNOWN';
      final received = {'SUCCESS', 'CAPTURED', 'PAID', 'APPROVED'}.contains(paymentStatus.toUpperCase());
      final failed = {'FAILED', 'CANCELLED'}.contains(paymentStatus.toUpperCase());
      await showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (context) => SingleChildScrollView(child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(child: CircleAvatar(
                radius: 32,
                backgroundColor: received ? FtColors.teal : (failed ? FtColors.danger : FtColors.navy),
                child: Icon(received ? Icons.check : (failed ? Icons.error_outline : Icons.receipt_long_outlined), color: Colors.white, size: 32),
              )),
              const SizedBox(height: 16),
              Text(
                received ? 'Payment received' : (failed ? 'Payment failed' : (hosted ? 'Checkout ready' : 'Payment recorded')),
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              Text(inr.format(value), textAlign: TextAlign.center, style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800)),
              Text(rail == 'CARD' ? 'Card payment' : 'UPI payment', textAlign: TextAlign.center, style: const TextStyle(color: FtColors.muted)),
              const SizedBox(height: 20),
              ListTile(title: const Text('Transaction ID'), subtitle: SelectableText('${payment['reference']}'), contentPadding: EdgeInsets.zero),
              ListTile(title: const Text('Status'), trailing: Text(paymentStatus, style: const TextStyle(fontWeight: FontWeight.w700)), contentPadding: EdgeInsets.zero),
              if (failed && payment['failureReason'] != null)
                Text('${payment['failureReason']}', textAlign: TextAlign.center, style: const TextStyle(color: FtColors.danger)),
              if (hosted) ...[
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () => launchUrl(Uri.parse(checkout), mode: LaunchMode.externalApplication),
                  child: const Text('Open checkout'),
                ),
              ],
              if (paymentStatus.toUpperCase() == 'PENDING') ...[
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: () async {
                    final updated = await api.paymentStatus(asInt(payment['id']));
                    if (!mounted) return;
                    setState(() => lastPayment = updated);
                    if (context.mounted) Navigator.pop(context);
                    ScaffoldMessenger.of(this.context).showSnackBar(
                      SnackBar(content: Text('Payment status: ${updated['status']}')),
                    );
                  },
                  icon: const Icon(Icons.refresh),
                  label: const Text('Refresh payment status'),
                ),
              ],
              const SizedBox(height: 8),
              OutlinedButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
            ],
          ),
        )),
      );
      if (mounted) setState(() { amount = '0'; advice = null; });
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
      backgroundColor: Colors.white,
      appBar: AppBar(title: const Text('Accept payment'), backgroundColor: Colors.white, foregroundColor: FtColors.ink, titleTextStyle: const TextStyle(color: FtColors.ink, fontSize: 16, fontWeight: FontWeight.w700)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
        children: [
          const SizedBox(height: 12),
          const Text('Amount to collect', textAlign: TextAlign.center, style: TextStyle(color: FtColors.muted, fontSize: 12)),
          const SizedBox(height: 10),
          SizedBox(height: 64, child: FittedBox(fit: BoxFit.scaleDown, child: Text('₹$amount', style: const TextStyle(fontSize: 44, fontWeight: FontWeight.w800)))),
          const SizedBox(height: 8),
          Text(
            gatewayReady ? 'Gateway connected' : 'Local payment mode',
            textAlign: TextAlign.center,
            style: const TextStyle(color: FtColors.muted, fontSize: 11),
          ),
          const SizedBox(height: 24),
          GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: 3,
            childAspectRatio: 1.8,
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            children: [
              for (final digit in ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0'])
                TextButton(
                  style: TextButton.styleFrom(backgroundColor: FtColors.bg, foregroundColor: FtColors.ink, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
                  onPressed: busy ? null : () => digit == '.' ? _dot() : _tap(digit),
                  child: Text(digit, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w600)),
                ),
              IconButton(
                tooltip: 'Delete last digit',
                style: IconButton.styleFrom(backgroundColor: FtColors.bg, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
                onPressed: busy ? null : _backspace,
                icon: const Icon(Icons.backspace_outlined),
              ),
            ],
          ),
          const SizedBox(height: 20),
          SegmentedButton<String>(
              showSelectedIcon: false,
              style: SegmentedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16), selectedBackgroundColor: const Color(0xFFE3EAF5), selectedForegroundColor: FtColors.navy, side: const BorderSide(color: FtColors.border)),
              segments: const [
                ButtonSegment(value: 'CARD', label: Text('Card tap'), icon: Icon(Icons.contactless)),
                ButtonSegment(value: 'UPI', label: Text('UPI QR'), icon: Icon(Icons.qr_code_2)),
              ],
              selected: {rail},
              onSelectionChanged: busy ? null : (value) => setState(() => rail = value.first),
          ),
          if (rail == 'CARD') ...[
            const SizedBox(height: 12),
            DropdownButtonFormField<CardPaymentProvider>(
              isExpanded: true,
              value: cardProvider,
              decoration: const InputDecoration(labelText: 'Card provider'),
              items: [
                if (mastercardReady)
                  const DropdownMenuItem(value: CardPaymentProvider.mastercard, child: Text('Mastercard hosted checkout')),
                if (razorpayReady)
                  const DropdownMenuItem(value: CardPaymentProvider.razorpay, child: Text('Razorpay test checkout')),
                if (tapOnPhoneReady)
                  const DropdownMenuItem(value: CardPaymentProvider.tapOnPhone, child: Text('Mastercard Tap on Phone')),
                if (!gatewayReady)
                  const DropdownMenuItem(value: CardPaymentProvider.auto, child: Text('Local simulated card')),
              ],
              onChanged: busy ? null : (value) => setState(() => cardProvider = value ?? CardPaymentProvider.auto),
            ),
          ],
          if (advice != null)
            Padding(
              padding: const EdgeInsets.all(14),
              child: Text(
                '${advice!['recommendedRail']} recommended · ${advice!['reason']}',
                textAlign: TextAlign.center,
                style: const TextStyle(color: FtColors.teal, fontSize: 12),
              ),
            ),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: busy || (double.tryParse(amount) ?? 0) < 1 ? null : _collect,
            icon: busy ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.arrow_forward, size: 18),
            label: Text(busy ? 'Processing...' : 'Collect payment'),
          ),
          if (lastPayment != null)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Text('Last: ${lastPayment!['reference']} · ${lastPayment!['status']}', style: const TextStyle(color: FtColors.muted, fontSize: 12)),
            ),
        ],
      ),
    );
  }
}
