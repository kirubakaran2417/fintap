import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/services/whatsapp_service.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class TransactionsScreen extends StatefulWidget {
  const TransactionsScreen({super.key, this.initialRail});

  final String? initialRail;

  @override
  State<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends State<TransactionsScreen> {
  List<dynamic> allTransactions = [];
  bool loading = true;
  String? error;
  String selectedFilter = 'ALL';
  String searchQuery = '';
  final searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    if (widget.initialRail != null && widget.initialRail!.isNotEmpty) {
      selectedFilter = widget.initialRail!.toUpperCase();
    }
    _load();
  }

  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => loading = true);
    try {
      final list = await api.payments();
      if (!mounted) return;
      setState(() {
        allTransactions = list;
        error = null;
      });
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  List<dynamic> get filteredTransactions {
    return allTransactions.where((item) {
      final map = item as Map<String, dynamic>;
      final rail = (map['rail']?.toString() ?? 'UPI').toUpperCase();

      // Rail Filter
      if (selectedFilter != 'ALL') {
        if (selectedFilter == 'KHATA' && rail != 'KHATA') return false;
        if (selectedFilter == 'CARD' && rail != 'CARD') return false;
        if (selectedFilter == 'UPI' && rail != 'UPI') return false;
      }

      // Search Query
      if (searchQuery.isNotEmpty) {
        final query = searchQuery.toLowerCase();
        final customer = (map['customerLabel']?.toString() ?? '').toLowerCase();
        final ref = (map['reference']?.toString() ?? '').toLowerCase();
        final mobile = (map['customerMobile']?.toString() ?? '').toLowerCase();
        final note = (map['failureReason']?.toString() ?? '').toLowerCase();
        if (!customer.contains(query) &&
            !ref.contains(query) &&
            !mobile.contains(query) &&
            !note.contains(query)) {
          return false;
        }
      }

      return true;
    }).toList();
  }

  void _showDetails(Map<String, dynamic> tx) {
    final rail = tx['rail']?.toString() ?? 'UPI';
    final isKhata = rail == 'KHATA';
    final note = tx['failureReason']?.toString() ?? tx['note']?.toString() ?? '';
    final isRepayment = isKhata && (note.toLowerCase().contains('repay') || tx['reference']?.toString().contains('REPAY') == true);
    final amt = asNum(tx['amount']);
    final customer = tx['customerLabel']?.toString() ?? 'Customer';
    final mobile = tx['customerMobile']?.toString() ?? '';
    final ref = tx['reference']?.toString() ?? '';
    final dateStr = formatDateTime(tx['createdAt']);
    final status = tx['status']?.toString() ?? 'SUCCESS';

    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 24,
                  backgroundColor: isKhata
                      ? (isRepayment ? const Color(0xFFE6F4EA) : const Color(0xFFFFF8E1))
                      : (rail == 'CARD' ? const Color(0xFFE3EAF5) : const Color(0xFFE6F4EA)),
                  child: Icon(
                    isKhata
                        ? (isRepayment ? Icons.call_received : Icons.call_made)
                        : (rail == 'CARD' ? Icons.credit_card : Icons.qr_code_2),
                    color: isKhata
                        ? (isRepayment ? FtColors.teal : const Color(0xFFD97706))
                        : (rail == 'CARD' ? FtColors.navy : FtColors.teal),
                    size: 24,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        customer,
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
                      ),
                      Text(
                        isKhata
                            ? (isRepayment ? 'Khata Repayment' : 'Khata Credit (Udhaar)')
                            : (rail == 'CARD' ? 'Card Payment' : 'UPI Payment'),
                        style: const TextStyle(fontSize: 12, color: FtColors.muted),
                      ),
                    ],
                  ),
                ),
                Text(
                  '${(isKhata && !isRepayment) ? '-' : '+'}${inr.format(amt)}',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: (isKhata && !isRepayment) ? const Color(0xFFD97706) : FtColors.teal,
                  ),
                ),
              ],
            ),
            const Divider(height: 28),
            _detailRow('Transaction Ref', ref, copyable: true),
            _detailRow('Date & Time', dateStr.isNotEmpty ? dateStr : 'Recently'),
            _detailRow('Payment Rail', isKhata ? 'Digital Khata Ledger' : (rail == 'CARD' ? 'Visa / Mastercard SoftPOS' : 'UPI QR Instant')),
            _detailRow('Status', status, statusColor: status == 'SUCCESS' ? FtColors.teal : FtColors.danger),
            if (mobile.isNotEmpty) _detailRow('Customer Phone', mobile, copyable: true),
            if (note.isNotEmpty) _detailRow('Notes / Description', note),
            const SizedBox(height: 16),
            if (isKhata && !isRepayment && mobile.isNotEmpty) ...[
              FilledButton.icon(
                icon: const Icon(Icons.chat, size: 18),
                style: FilledButton.styleFrom(backgroundColor: const Color(0xFF25D366)),
                label: const Text('Send WhatsApp Udhaar Reminder'),
                onPressed: () async {
                  Navigator.pop(sheetContext);
                  try {
                    final res = await api.nudgeWhatsapp({
                      'mobile': mobile,
                      'type': 'KHATA',
                      'name': customer,
                      'amount': inr.format(amt).replaceAll('₹', '').trim(),
                      'sendLive': true,
                    });
                    if (mounted) {
                      WhatsAppService.showLiveDeliveryModal(context, res);
                    }
                  } catch (e) {
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Nudge failed: $e')));
                    }
                  }
                },
              ),
              const SizedBox(height: 8),
            ],
            OutlinedButton(
              onPressed: () => Navigator.pop(sheetContext),
              child: const Text('Close'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _detailRow(String label, String value, {bool copyable = false, Color? statusColor}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(label, style: const TextStyle(fontSize: 12, color: FtColors.muted)),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
                color: statusColor ?? FtColors.ink,
              ),
            ),
          ),
          if (copyable && value.isNotEmpty)
            GestureDetector(
              onTap: () {
                Clipboard.setData(ClipboardData(text: value));
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Copied $label to clipboard'), duration: const Duration(seconds: 1)),
                );
              },
              child: const Icon(Icons.copy, size: 14, color: FtColors.muted),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final list = filteredTransactions;

    num totalCollections = 0;
    num totalKhataGiven = 0;
    num totalKhataRepaid = 0;
    int upiCount = 0;
    int cardCount = 0;
    int khataCount = 0;

    for (final item in allTransactions) {
      final map = item as Map<String, dynamic>;
      final rail = (map['rail']?.toString() ?? 'UPI').toUpperCase();
      final amt = asNum(map['amount']);
      final note = (map['failureReason']?.toString() ?? '').toLowerCase();
      final isRepay = note.contains('repay') || map['reference']?.toString().contains('REPAY') == true;

      if (rail == 'KHATA') {
        khataCount++;
        if (isRepay) {
          totalKhataRepaid += amt;
        } else {
          totalKhataGiven += amt;
        }
      } else {
        totalCollections += amt;
        if (rail == 'CARD') {
          cardCount++;
        } else {
          upiCount++;
        }
      }
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Transactions'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 32),
          children: [
            // Top Summary Card
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [FtColors.navy, Color(0xFF1E293B)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Total Collections', style: TextStyle(color: Colors.white70, fontSize: 11)),
                          const SizedBox(height: 2),
                          Text(inr.format(totalCollections + totalKhataRepaid), style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(color: Colors.white12, borderRadius: BorderRadius.circular(12)),
                        child: Text('${allTransactions.length} entries', style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const Divider(color: Colors.white12, height: 1),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Khata Given', style: TextStyle(color: Colors.white60, fontSize: 10.5)),
                            Text('- ${inr.format(totalKhataGiven)}', style: const TextStyle(color: Color(0xFFFBBF24), fontSize: 13, fontWeight: FontWeight.w700)),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Khata Recovered', style: TextStyle(color: Colors.white60, fontSize: 10.5)),
                            Text('+ ${inr.format(totalKhataRepaid)}', style: const TextStyle(color: Color(0xFF34D399), fontSize: 13, fontWeight: FontWeight.w700)),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Card + UPI', style: TextStyle(color: Colors.white60, fontSize: 10.5)),
                            Text(inr.format(totalCollections), style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w700)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),

            // Search Bar
            TextField(
              controller: searchController,
              decoration: InputDecoration(
                hintText: 'Search customer, phone, or ref...',
                hintStyle: const TextStyle(fontSize: 13, color: FtColors.muted),
                prefixIcon: const Icon(Icons.search, size: 20),
                suffixIcon: searchQuery.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          searchController.clear();
                          setState(() => searchQuery = '');
                        },
                      )
                    : null,
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: FtColors.border)),
                filled: true,
                fillColor: Colors.white,
              ),
              onChanged: (val) => setState(() => searchQuery = val.trim()),
            ),
            const SizedBox(height: 10),

            // Rail Filter Chips
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _filterChip('ALL', 'All (${allTransactions.length})'),
                  const SizedBox(width: 8),
                  _filterChip('UPI', 'UPI QR ($upiCount)'),
                  const SizedBox(width: 8),
                  _filterChip('CARD', 'Card Tap ($cardCount)'),
                  const SizedBox(width: 8),
                  _filterChip('KHATA', 'Khata Book ($khataCount)'),
                ],
              ),
            ),
            const SizedBox(height: 14),

            // List or Empty
            if (loading)
              const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator()))
            else if (error != null)
              EmptyState(icon: Icons.cloud_off_outlined, message: error!, onRetry: _load)
            else if (list.isEmpty)
              EmptyState(
                icon: Icons.receipt_long_outlined,
                message: searchQuery.isNotEmpty
                    ? 'No transactions matching "$searchQuery"'
                    : 'No transactions found for $selectedFilter filter',
                onRetry: () {
                  searchController.clear();
                  setState(() {
                    searchQuery = '';
                    selectedFilter = 'ALL';
                  });
                },
              )
            else
              ...list.map((item) {
                final map = item as Map<String, dynamic>;
                final rail = (map['rail']?.toString() ?? 'UPI').toUpperCase();
                final isKhata = rail == 'KHATA';
                final isCard = rail == 'CARD';
                final note = map['failureReason']?.toString() ?? map['note']?.toString() ?? '';
                final isRepayment = isKhata && (note.toLowerCase().contains('repay') || map['reference']?.toString().contains('REPAY') == true);
                final amt = asNum(map['amount']);
                final customer = map['customerLabel']?.toString() ?? 'Customer';
                final dateStr = formatDate(map['createdAt']);

                final IconData icon;
                final Color iconColor;
                final Color iconBg;
                if (isKhata) {
                  icon = isRepayment ? Icons.call_received : Icons.call_made;
                  iconColor = isRepayment ? FtColors.teal : const Color(0xFFD97706);
                  iconBg = isRepayment ? const Color(0xFFE6F4EA) : const Color(0xFFFFF8E1);
                } else if (isCard) {
                  icon = Icons.credit_card;
                  iconColor = FtColors.navy;
                  iconBg = const Color(0xFFE3EAF5);
                } else {
                  icon = Icons.qr_code_2;
                  iconColor = FtColors.teal;
                  iconBg = const Color(0xFFE6F4EA);
                }

                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(12),
                    onTap: () => _showDetails(map),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      child: Row(
                        children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(color: iconBg, borderRadius: BorderRadius.circular(10)),
                            child: Icon(icon, color: iconColor, size: 20),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        customer,
                                        style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: isKhata
                                            ? (isRepayment ? const Color(0xFFE8F5E9) : const Color(0xFFFFF3E0))
                                            : const Color(0xFFF1F5F9),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Text(
                                        isKhata ? 'KHATA' : rail,
                                        style: TextStyle(
                                          fontSize: 9.5,
                                          fontWeight: FontWeight.w800,
                                          color: isKhata ? (isRepayment ? FtColors.teal : const Color(0xFFD97706)) : FtColors.navy,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  isKhata
                                      ? '${isRepayment ? 'Repayment' : 'Udhaar'}${dateStr.isNotEmpty ? ' · $dateStr' : ''}'
                                      : '${rail == 'CARD' ? 'Card SoftPOS' : 'UPI QR'}${dateStr.isNotEmpty ? ' · $dateStr' : ''}',
                                  style: const TextStyle(fontSize: 11, color: FtColors.muted),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 10),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                '${(isKhata && !isRepayment) ? '-' : '+'}${inr.format(amt)}',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w800,
                                  color: (isKhata && !isRepayment) ? const Color(0xFFD97706) : FtColors.teal,
                                ),
                              ),
                              const SizedBox(height: 2),
                              const Text('SUCCESS', style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w700, color: Colors.green)),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  Widget _filterChip(String filterKey, String label) {
    final active = selectedFilter == filterKey;
    return ChoiceChip(
      label: Text(label, style: TextStyle(fontSize: 11.5, fontWeight: active ? FontWeight.w700 : FontWeight.w500)),
      selected: active,
      selectedColor: FtColors.navy,
      labelStyle: TextStyle(color: active ? Colors.white : FtColors.ink),
      onSelected: (val) {
        if (val) setState(() => selectedFilter = filterKey);
      },
    );
  }
}
