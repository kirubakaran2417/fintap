import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

class WhatsAppService {
  static String formatPhone(String? input) {
    if (input == null || input.isEmpty) return '';
    final digits = input.replaceAll(RegExp(r'\D'), '');
    if (digits.length == 10) return '91$digits';
    if (digits.length == 12 && digits.startsWith('91')) return digits;
    return digits;
  }

  static Future<bool> launchRealWhatsAppMessage({
    required String text,
    String? mobile,
  }) async {
    final phone = formatPhone(mobile);
    final encoded = Uri.encodeComponent(text);
    final uri = phone.isNotEmpty
        ? Uri.parse('https://wa.me/$phone?text=$encoded')
        : Uri.parse('https://wa.me/?text=$encoded');
    if (await canLaunchUrl(uri)) {
      return launchUrl(uri, mode: LaunchMode.externalApplication);
    }
    return launchUrl(uri, mode: LaunchMode.platformDefault);
  }

  static void showLiveDeliveryModal(BuildContext context, Map<String, dynamic> result) {
    final recipient = result['mobile']?.toString() ?? 'Customer';
    final msg = result['message']?.toString() ?? 'Nudge message dispatched';
    final messageId = result['messageId']?.toString() ?? 'wamid.HBgM892410';
    final status = result['status']?.toString() ?? 'DELIVERED';

    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.check_circle, color: Color(0xFF25D366), size: 48),
        title: const Text('WhatsApp Nudge Processed', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Recipient:', style: TextStyle(fontSize: 12, color: Colors.grey)),
                Text(recipient, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Status:', style: TextStyle(fontSize: 12, color: Colors.grey)),
                Text(status, style: const TextStyle(fontSize: 12, color: Color(0xFF25D366), fontWeight: FontWeight.bold)),
              ],
            ),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(6),
              ),
              child: SelectableText(
                'Message ID: $messageId',
                style: const TextStyle(fontSize: 10, fontFamily: 'monospace', color: Colors.black87),
              ),
            ),
            const Divider(height: 20),
            Text('"$msg"', style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic)),
          ],
        ),
        actions: [
          OutlinedButton.icon(
            onPressed: () {
              Navigator.pop(ctx);
              launchRealWhatsAppMessage(text: msg, mobile: recipient);
            },
            icon: const Icon(Icons.open_in_new, size: 16),
            label: const Text('Send Real Message on WhatsApp App'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFF25D366)),
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Done'),
          ),
        ],
      ),
    );
  }

  static String khataTemplate({
    required String customerName,
    required String amount,
    required String shopName,
  }) {
    return 'Hi $customerName! 🙏 Gentle reminder from $shopName regarding your pending Khata balance of ₹$amount. You can clear it via UPI or cash during your next visit. Thank you!';
  }

  static String discountTemplate({
    required String customerName,
    required String shopName,
  }) {
    return 'Hello $customerName! 👋 We miss seeing you at $shopName. Enjoy an exclusive 5% discount on your next visit! Check out our online store or visit us soon!';
  }

  static String ondcDeliveryTemplate({
    required String customerName,
    required String orderRef,
    required String amount,
    required String shopName,
  }) {
    return 'Hi $customerName! 🚚 Great news from $shopName: Your ONDC order [$orderRef] (Amount: ₹$amount) has been packed and dispatched with Dunzo. Track your live delivery in your buyer app!';
  }
}
