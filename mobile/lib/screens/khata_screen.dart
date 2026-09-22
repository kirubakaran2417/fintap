import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/services/whatsapp_service.dart';
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
  final mobile = TextEditingController();
  final amount = TextEditingController();
  final notes = TextEditingController(text: '');

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    name.dispose();
    mobile.dispose();
    amount.dispose();
    notes.dispose();
    super.dispose();
  }

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

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
    mobile.clear();
    amount.clear();
    notes.clear();
    DateTime selectedDate = DateTime.now();
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
                  const SizedBox(height: 14),
                  InkWell(
                    onTap: saving
                        ? null
                        : () async {
                            final now = DateTime.now();
                            final picked = await showDatePicker(
                              context: context,
                              initialDate: selectedDate,
                              firstDate: DateTime(2020),
                              lastDate: now.add(const Duration(days: 30)),
                              helpText: 'Select Khata Entry Date',
                            );
                            if (picked != null) {
                              updateSheet(() => selectedDate = picked);
                            }
                          },
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(
                        border: Border.all(color: FtColors.border),
                        borderRadius: BorderRadius.circular(10),
                        color: FtColors.bg,
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.calendar_month_outlined, color: FtColors.gold, size: 20),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text('Entry date', style: TextStyle(fontSize: 10.5, color: FtColors.muted)),
                                Text(
                                  formatDate(selectedDate),
                                  style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: FtColors.ink),
                                ),
                              ],
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFEF3C7),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text('Change date', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFFB45309))),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      ChoiceChip(
                        label: const Text('Today', style: TextStyle(fontSize: 11)),
                        selected: _isSameDay(selectedDate, DateTime.now()),
                        onSelected: saving
                            ? null
                            : (val) {
                                if (val) updateSheet(() => selectedDate = DateTime.now());
                              },
                      ),
                      const SizedBox(width: 8),
                      ChoiceChip(
                        label: const Text('Yesterday', style: TextStyle(fontSize: 11)),
                        selected: _isSameDay(selectedDate, DateTime.now().subtract(const Duration(days: 1))),
                        onSelected: saving
                            ? null
                            : (val) {
                                if (val) updateSheet(() => selectedDate = DateTime.now().subtract(const Duration(days: 1)));
                              },
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  TextFormField(
                    controller: name,
                    enabled: !saving,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Customer name', prefixIcon: Icon(Icons.person_outline)),
                    validator: (value) => value == null || value.trim().isEmpty ? 'Enter a customer name' : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: mobile,
                    enabled: !saving,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(labelText: 'Customer mobile (optional)', prefixIcon: Icon(Icons.phone_outlined)),
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
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: notes,
                    enabled: !saving,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(labelText: 'Notes', prefixIcon: Icon(Icons.note_alt_outlined)),
                    maxLines: 2,
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
                          'mobile': mobile.text.trim(),
                          'amount': double.parse(amount.text),
                          'credit': credit,
                          'note': (notes.text.trim().isNotEmpty ? notes.text.trim() : (credit ? 'Repayment' : 'Udhaar')),
                          'entryDate': selectedDate.toIso8601String(),
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
    num given = 0;
    num recovered = 0;
    final customers = <String>{};
    for (final item in rows) {
      final map = item as Map<String, dynamic>;
      final amt = asNum(map['amount']);
      if (map['credit'] == true) {
        recovered += amt;
      } else {
        given += amt;
      }
      final cname = map['customerName']?.toString();
      if (cname != null && cname.isNotEmpty) customers.add(cname);
    }
    final customerCount = customers.length;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Digital khata'),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
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
                  final dateStr = formatDate(map['createdAt']);
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
                      subtitle: Row(
                        children: [
                          if (dateStr.isNotEmpty) ...[
                            Text(dateStr, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: FtColors.navy)),
                            const Text(' · ', style: TextStyle(fontSize: 11, color: FtColors.muted)),
                          ],
                          Expanded(
                            child: Text(
                              map['note']?.toString() ?? (credit ? 'Repayment' : 'Udhaar'),
                              style: const TextStyle(fontSize: 11, color: FtColors.muted),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text('${credit ? '+' : '-'}${inr.format(asNum(map['amount']))}', style: TextStyle(fontSize: 13, color: credit ? FtColors.teal : FtColors.danger, fontWeight: FontWeight.w800)),
                          if (!credit) ...[
                            const SizedBox(width: 4),
                            IconButton(
                              visualDensity: VisualDensity.compact,
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(),
                              tooltip: 'Nudge on WhatsApp',
                              icon: const Icon(Icons.chat, color: Color(0xFF25D366), size: 20),
                              onPressed: () async {
                                final mobile = map['mobile']?.toString() ?? '';
                                final amt = inr.format(asNum(map['amount'])).replaceAll('₹', '').trim();
                                try {
                                  final res = await api.nudgeWhatsapp({
                                    'mobile': mobile,
                                    'type': 'KHATA',
                                    'name': customerName,
                                    'amount': amt,
                                    'sendLive': true,
                                  });
                                  if (context.mounted) {
                                    WhatsAppService.showLiveDeliveryModal(context, res);
                                  }
                                } catch (e) {
                                  if (context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Nudge failed: $e')));
                                  }
                                }
                              },
                            ),
                          ],
                        ],
                      ),
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
