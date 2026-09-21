import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
import 'package:flutter/material.dart';

class KhataScreen extends StatefulWidget {
  const KhataScreen({super.key});

  @override
  State<KhataScreen> createState() => _KhataScreenState();
}

class _KhataScreenState extends State<KhataScreen> {
  List<dynamic> rows = [];
  bool loading = true;
  String? error;
  final name = TextEditingController();
  final amount = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    name.dispose();
    amount.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final loaded = await api.khata();
      if (!mounted) return;
      setState(() { rows = loaded; error = null; });
    } catch (exception) {
      if (mounted) setState(() => error = '$exception');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _showEntry() async {
    name.clear();
    amount.clear();
    final form = GlobalKey<FormState>();
    bool credit = false;
    bool saving = false;
    String? saveError;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      isDismissible: false,
      enableDrag: false,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, updateSheet) => PopScope(
          canPop: !saving,
          child: SingleChildScrollView(
            padding: EdgeInsets.fromLTRB(20, 4, 20, MediaQuery.viewInsetsOf(context).bottom + 24),
            child: Form(
              key: form,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text('Add khata entry', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 18),
                  SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: false, label: Text('Give credit'), icon: Icon(Icons.call_made)),
                      ButtonSegment(value: true, label: Text('Repayment'), icon: Icon(Icons.call_received)),
                    ],
                    selected: {credit},
                    onSelectionChanged: saving ? null : (value) => updateSheet(() => credit = value.first),
                  ),
                  const SizedBox(height: 18),
                  TextFormField(
                    controller: name,
                    enabled: !saving,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Customer name', prefixIcon: Icon(Icons.person_outline)),
                    validator: (value) => value == null || value.trim().isEmpty ? 'Enter a customer name' : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: amount,
                    enabled: !saving,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Amount', prefixIcon: Icon(Icons.currency_rupee)),
                    validator: (value) {
                      final parsed = double.tryParse(value ?? '');
                      return parsed == null || !parsed.isFinite || parsed < 1 ? 'Enter an amount of at least 1' : null;
                    },
                  ),
                  if (saveError != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(saveError!, style: const TextStyle(color: FtColors.danger))),
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    icon: const Icon(Icons.check, size: 18),
                    label: Text(saving ? 'Saving...' : 'Save entry'),
                    onPressed: saving ? null : () async {
                      if (!form.currentState!.validate()) return;
                      updateSheet(() { saving = true; saveError = null; });
                      try {
                        await api.addKhata({
                          'customerName': name.text.trim(),
                          'mobile': '',
                          'amount': double.parse(amount.text),
                          'credit': credit,
                          'note': credit ? 'Repayment' : 'Udhaar',
                        });
                        if (!sheetContext.mounted) return;
                        updateSheet(() => saving = false);
                        Navigator.pop(sheetContext);
                        await _load();
                      } catch (exception) {
                        if (sheetContext.mounted) updateSheet(() { saving = false; saveError = '$exception'; });
                      }
                    },
                  ),
                  TextButton(onPressed: saving ? null : () => Navigator.pop(sheetContext), child: const Text('Cancel')),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final given = rows.where((row) => row['credit'] != true).fold<num>(0, (total, row) => total + asNum(row['amount']));
    final recovered = rows.where((row) => row['credit'] == true).fold<num>(0, (total, row) => total + asNum(row['amount']));
    final customerCount = rows.map((row) => row['customerName'].toString().trim().toLowerCase()).toSet().length;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Digital khata'),
        backgroundColor: FtColors.gold,
        actions: [IconButton(tooltip: 'Refresh ledger', onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add khata entry',
        backgroundColor: FtColors.gold,
        foregroundColor: FtColors.ink,
        onPressed: _showEntry,
        child: const Icon(Icons.add),
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 90),
          children: [
            MerchantHeader(
              title: 'Your credit ledger',
              subtitle: 'Udhar and repayments',
              color: FtColors.gold,
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                SummaryMetric(value: inr.format(given - recovered), label: 'Net outstanding'),
                SummaryMetric(value: '$customerCount', label: 'Customers'),
                SummaryMetric(value: inr.format(recovered), label: 'Total recovered'),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionHeading('Ledger entries'),
                if (loading) const Center(child: CircularProgressIndicator())
                else if (error != null) EmptyState(icon: Icons.cloud_off_outlined, message: error!, onRetry: _load)
                else if (rows.isEmpty) const EmptyState(icon: Icons.menu_book_outlined, message: 'No khata entries yet')
                else ...rows.map((item) {
                  final map = item as Map<String, dynamic>;
                  final credit = map['credit'] == true;
                  final customerName = map['customerName']?.toString() ?? 'Customer';
                  return Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      leading: CircleAvatar(
                        radius: 18,
                        backgroundColor: credit ? const Color(0xFFE6F4EA) : const Color(0xFFE3EAF5),
                        foregroundColor: credit ? FtColors.teal : FtColors.navy,
                        child: Text(customerName.isEmpty ? '?' : customerName.characters.first.toUpperCase()),
                      ),
                      title: Text(customerName, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                      subtitle: Text(map['note']?.toString() ?? (credit ? 'Repayment' : 'Udhaar'), style: const TextStyle(fontSize: 11, color: FtColors.muted)),
                      trailing: Text('${credit ? '+' : '-'}${inr.format(asNum(map['amount']))}', style: TextStyle(fontSize: 13, color: credit ? FtColors.teal : FtColors.danger, fontWeight: FontWeight.w800)),
                    ),
                  );
                }),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}
