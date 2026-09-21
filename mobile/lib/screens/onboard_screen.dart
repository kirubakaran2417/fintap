import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/screens/shell.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
import 'package:flutter/material.dart';

class OnboardScreen extends StatefulWidget {
  const OnboardScreen({super.key});

  @override
  State<OnboardScreen> createState() => _OnboardScreenState();
}

class _OnboardScreenState extends State<OnboardScreen> {
  final shop = TextEditingController();
  final owner = TextEditingController();
  final gstin = TextEditingController();
  final address = TextEditingController();
  final city = TextEditingController();
  String category = 'Grocery';
  String language = 'hi';
  bool loading = false;
  String? error;

  @override
  void initState() {
    super.initState();
    api.me().then((me) {
      if (!mounted) return;
      owner.text = me['ownerName']?.toString() ?? '';
      shop.text = (me['shopName']?.toString() ?? '').contains("'s store") ? '' : (me['shopName']?.toString() ?? '');
    }).catchError((_) {});
  }

  @override
  void dispose() {
    shop.dispose();
    owner.dispose();
    gstin.dispose();
    address.dispose();
    city.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (shop.text.trim().isEmpty || owner.text.trim().isEmpty) {
      setState(() => error = 'Shop name and owner name are required');
      return;
    }
    setState(() {
      loading = true;
      error = null;
    });
    try {
      await api.onboard({
        'shopName': shop.text.trim(),
        'ownerName': owner.text.trim(),
        'category': category,
        'language': language,
        'gstin': gstin.text.trim(),
        'address': address.text.trim(),
        'city': city.text.trim(),
      });
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const MerchantShell()),
        (_) => false,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(title: const FinTapMark(compact: true), backgroundColor: Colors.white, foregroundColor: FtColors.navy),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SetupProgress(step: 2),
          const Text('Shop details', style: TextStyle(fontSize: 24, color: FtColors.navy, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          const Text('Your business profile', style: TextStyle(color: FtColors.muted, fontSize: 12)),
          const SizedBox(height: 24),
          TextField(controller: shop, decoration: const InputDecoration(labelText: 'Shop name', prefixIcon: Icon(Icons.storefront_outlined))),
          const SizedBox(height: 12),
          TextField(controller: owner, decoration: const InputDecoration(labelText: 'Owner name', prefixIcon: Icon(Icons.badge_outlined))),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: category,
            items: const [
              DropdownMenuItem(value: 'Grocery', child: Text('Grocery / Kirana')),
              DropdownMenuItem(value: 'Pharmacy', child: Text('Pharmacy')),
              DropdownMenuItem(value: 'F&B', child: Text('Food & beverage')),
              DropdownMenuItem(value: 'General', child: Text('General store')),
            ],
            onChanged: (value) => setState(() => category = value ?? 'Grocery'),
            decoration: const InputDecoration(labelText: 'Category'),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: language,
            items: const [
              DropdownMenuItem(value: 'en', child: Text('English')),
              DropdownMenuItem(value: 'hi', child: Text('Hindi')),
              DropdownMenuItem(value: 'ta', child: Text('Tamil')),
              DropdownMenuItem(value: 'te', child: Text('Telugu')),
            ],
            onChanged: (value) => setState(() => language = value ?? 'hi'),
            decoration: const InputDecoration(labelText: 'Insight language'),
          ),
          const SizedBox(height: 12),
          TextField(controller: gstin, decoration: const InputDecoration(labelText: 'GSTIN (optional)')),
          const SizedBox(height: 12),
          TextField(controller: address, decoration: const InputDecoration(labelText: 'Street address')),
          const SizedBox(height: 12),
          TextField(controller: city, decoration: const InputDecoration(labelText: 'City')),
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(error!, style: const TextStyle(color: Color(0xFFB42318))),
          ],
          const SizedBox(height: 24),
          FilledButton.icon(style: FilledButton.styleFrom(backgroundColor: FtColors.teal), onPressed: loading ? null : _save, icon: const Icon(Icons.check, size: 18), label: Text(loading ? 'Saving…' : 'Open FinTap')),
        ],
      ),
    );
  }
}
