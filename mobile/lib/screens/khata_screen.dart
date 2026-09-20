import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class KhataScreen extends StatefulWidget {
  const KhataScreen({super.key});

  @override
  State<KhataScreen> createState() => _KhataScreenState();
}

class _KhataScreenState extends State<KhataScreen> {
  List<dynamic> rows = [];
  final name = TextEditingController();
  final amount = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final loaded = await api.khata();
    setState(() => rows = loaded);
  }

  Future<void> _add({required bool credit}) async {
    if (name.text.trim().isEmpty || (double.tryParse(amount.text) ?? 0) < 1) return;
    await api.addKhata({
      'customerName': name.text.trim(),
      'mobile': '',
      'amount': double.tryParse(amount.text) ?? 0,
      'credit': credit,
      'note': credit ? 'Repayment' : 'Udhaar',
    });
    name.clear();
    amount.clear();
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const FinTapMark(light: true, compact: true)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Digital khata', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          const Text('Give credit and collect repayments without a paper register.', style: TextStyle(color: FtColors.muted)),
          const SizedBox(height: 16),
          TextField(controller: name, decoration: const InputDecoration(labelText: 'Customer name')),
          const SizedBox(height: 8),
          TextField(controller: amount, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Amount')),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: FilledButton(onPressed: () => _add(credit: false), child: const Text('Give credit'))),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14))),
                  onPressed: () => _add(credit: true),
                  child: const Text('Collect'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ...rows.map((item) {
            final map = item as Map<String, dynamic>;
            final credit = map['credit'] == true;
            return Card(
              child: ListTile(
                title: Text(map['customerName'].toString()),
                subtitle: Text(map['note']?.toString() ?? ''),
                trailing: Text(
                  '${credit ? '+' : '-'}${inr.format(asNum(map['amount']))}',
                  style: TextStyle(color: credit ? FtColors.teal : const Color(0xFFB42318), fontWeight: FontWeight.w700),
                ),
              ),
            );
          }),
        ],
      ),
    );
  }
}
