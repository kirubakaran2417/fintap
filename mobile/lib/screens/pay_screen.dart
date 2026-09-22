import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/payments/payment_provider.dart';
import 'package:digi_kadai/screens/transactions_screen.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
  final TextEditingController customerName = TextEditingController();
  final TextEditingController customerMobile = TextEditingController();

  bool get _hasCustomer {
    final nameOk = customerName.text.trim().length >= 2;
    final mobileOk = customerMobile.text.replaceAll(RegExp(r'\D'), '').length == 10;
    return nameOk && mobileOk;
  }

  @override
  void initState() {
    super.initState();
    customerName.addListener(() => setState(() {}));
    customerMobile.addListener(() => setState(() {}));
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

  @override
  void dispose() {
    customerName.dispose();
    customerMobile.dispose();
    super.dispose();
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
    if (value < 1 || !_hasCustomer) return;
    setState(() => busy = true);
    try {
      final payment = await api.acceptPayment(
        value,
        rail,
        provider: cardProvider.apiValue,
        customerName: customerName.text.trim(),
        customerMobile: customerMobile.text.replaceAll(RegExp(r'\D'), ''),
      );
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

      if (!mounted) return;

      if (rail == 'CARD') {
        await showModalBottomSheet<void>(
          context: context,
          showDragHandle: true,
          isScrollControlled: true,
          useSafeArea: true,
          builder: (modalContext) => StatefulBuilder(
            builder: (ctx, setModalState) {
              final currStatus = payment['status']?.toString() ?? 'UNKNOWN';
              final isSuccess = {'SUCCESS', 'CAPTURED', 'PAID', 'APPROVED'}.contains(currStatus.toUpperCase());
              final note = payment['note']?.toString() ?? 'Visa & Mastercard Contactless NFC Tap';

              if (isSuccess) {
                return Padding(
                  padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const CircleAvatar(
                        radius: 36,
                        backgroundColor: FtColors.teal,
                        child: Icon(Icons.check, color: Colors.white, size: 40),
                      ),
                      const SizedBox(height: 16),
                      const Text('Contactless Tap Approved', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
                      const SizedBox(height: 8),
                      Text(inr.format(value), style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w800, color: FtColors.ink)),
                      Text(note, style: const TextStyle(color: FtColors.muted, fontSize: 13)),
                      const SizedBox(height: 20),
                      ListTile(title: const Text('Customer'), subtitle: Text(customerName.text.trim()), contentPadding: EdgeInsets.zero),
                      ListTile(title: const Text('Transaction Ref'), subtitle: SelectableText('${payment['reference']}'), contentPadding: EdgeInsets.zero),
                      const ListTile(title: Text('Status'), trailing: Text('SUCCESS', style: TextStyle(fontWeight: FontWeight.w700, color: Colors.green)), contentPadding: EdgeInsets.zero),
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: () => Navigator.pop(modalContext),
                        child: const Text('Done'),
                      ),
                    ],
                  ),
                );
              }

              return Padding(
                padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(color: const Color(0xFF1E3A8A).withAlpha(30), borderRadius: BorderRadius.circular(6)),
                          child: const Text('VISA', style: TextStyle(fontWeight: FontWeight.w900, color: Color(0xFF1D4ED8), fontStyle: FontStyle.italic)),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(color: const Color(0xFFDC2626).withAlpha(30), borderRadius: BorderRadius.circular(6)),
                          child: const Text('Mastercard', style: TextStyle(fontWeight: FontWeight.w800, color: Color(0xFFDC2626))),
                        ),
                        const SizedBox(width: 10),
                        const Icon(Icons.contactless, color: FtColors.teal, size: 24),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text(inr.format(value), textAlign: TextAlign.center, style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w800)),
                    const Text('Hold Visa/Mastercard card or smartphone near rear NFC reader', textAlign: TextAlign.center, style: TextStyle(color: FtColors.muted, fontSize: 13)),
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: FtColors.bg,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: FtColors.border),
                      ),
                      child: const Column(
                        children: [
                          Icon(Icons.contactless, size: 52, color: FtColors.teal),
                          SizedBox(height: 10),
                          Text('Ready for Contactless Tap', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                          SizedBox(height: 4),
                          Text('EMVCo Contactless Level 2 · SoftPOS Active', style: TextStyle(fontSize: 12, color: FtColors.muted)),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
                            onPressed: () async {
                              HapticFeedback.heavyImpact();
                              final res = await api.submitNfcTap(asInt(payment['id']), brand: 'VISA', panLast4: '4242');
                              setModalState(() => payment.addAll(res));
                              if (mounted) setState(() => lastPayment = payment);
                            },
                            icon: const Icon(Icons.credit_card, color: Color(0xFF1D4ED8), size: 18),
                            label: const Text('Tap Visa (•••• 4242)', style: TextStyle(fontSize: 11)),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton.icon(
                            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
                            onPressed: () async {
                              HapticFeedback.heavyImpact();
                              final res = await api.submitNfcTap(asInt(payment['id']), brand: 'MASTERCARD', panLast4: '5412');
                              setModalState(() => payment.addAll(res));
                              if (mounted) setState(() => lastPayment = payment);
                            },
                            icon: const Icon(Icons.credit_card, color: Color(0xFFDC2626), size: 18),
                            label: const Text('Tap MC (•••• 5412)', style: TextStyle(fontSize: 11)),
                          ),
                        ),
                      ],
                    ),
                    if (hosted) ...[
                      const SizedBox(height: 10),
                      FilledButton.icon(
                        style: FilledButton.styleFrom(backgroundColor: FtColors.navy, padding: const EdgeInsets.symmetric(vertical: 12)),
                        onPressed: () => launchUrl(Uri.parse(checkout), mode: LaunchMode.externalApplication),
                        icon: const Icon(Icons.open_in_browser, size: 18),
                        label: const Text('Open Full-Screen NFC Terminal'),
                      ),
                    ],
                  ],
                ),
              );
            },
          ),
        );
      } else {
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
                  received ? 'Payment received' : (failed ? 'Payment failed' : 'Payment recorded'),
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 8),
                Text(inr.format(value), textAlign: TextAlign.center, style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800)),
                const Text('UPI payment', textAlign: TextAlign.center, style: TextStyle(color: FtColors.muted)),
                const SizedBox(height: 20),
                ListTile(title: const Text('Customer'), subtitle: Text(customerName.text.trim()), contentPadding: EdgeInsets.zero),
                ListTile(title: const Text('Mobile'), subtitle: Text(customerMobile.text.replaceAll(RegExp(r'\D'), '')), contentPadding: EdgeInsets.zero),
                ListTile(title: const Text('Transaction ID'), subtitle: SelectableText('${payment['reference']}'), contentPadding: EdgeInsets.zero),
                ListTile(title: const Text('Status'), trailing: Text(paymentStatus, style: const TextStyle(fontWeight: FontWeight.w700)), contentPadding: EdgeInsets.zero),
                const SizedBox(height: 12),
                OutlinedButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
              ],
            ),
          )),
        );
      }
      if (mounted) setState(() { amount = '0'; advice = null; customerName.clear(); customerMobile.clear(); });
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
          const SizedBox(height: 8),
          TextField(
            controller: customerName,
            textCapitalization: TextCapitalization.words,
            textInputAction: TextInputAction.next,
            enabled: !busy,
            decoration: const InputDecoration(
              labelText: 'Customer name',
              prefixIcon: Icon(Icons.person_outline),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: customerMobile,
            keyboardType: TextInputType.phone,
            textInputAction: TextInputAction.done,
            enabled: !busy,
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(10),
            ],
            decoration: const InputDecoration(
              labelText: 'Mobile number',
              prefixIcon: Icon(Icons.phone_android),
              counterText: '',
            ),
          ),
          const SizedBox(height: 20),
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
              initialValue: cardProvider,
              decoration: const InputDecoration(labelText: 'Card provider'),
              items: [
                DropdownMenuItem(
                  value: CardPaymentProvider.mastercard,
                  child: Text(mastercardReady ? 'Mastercard hosted checkout' : '💳 Visa & Mastercard NFC Tap on Phone'),
                ),
                if (razorpayReady)
                  const DropdownMenuItem(value: CardPaymentProvider.razorpay, child: Text('Razorpay test checkout')),
                if (tapOnPhoneReady)
                  const DropdownMenuItem(value: CardPaymentProvider.tapOnPhone, child: Text('Mastercard Tap on Phone')),
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
            onPressed: busy || !_hasCustomer || (double.tryParse(amount) ?? 0) < 1 ? null : _collect,
            icon: busy ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.arrow_forward, size: 18),
            label: Text(busy ? 'Processing...' : 'Collect payment'),
          ),
          if (lastPayment != null)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Text('Last: ${lastPayment!['reference']} · ${lastPayment!['status']}', style: const TextStyle(color: FtColors.muted, fontSize: 12)),
            ),
          const SizedBox(height: 12),
          TextButton.icon(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const TransactionsScreen()),
            ),
            icon: const Icon(Icons.receipt_long_outlined, size: 16),
            label: const Text('View all transactions & Khata ledger'),
          ),
        ],
      ),
    );
  }
}
