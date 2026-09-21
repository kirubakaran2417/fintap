import 'dart:math' as math;

import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/merchant_ui.dart';
import 'package:flutter/material.dart';

class InsightsScreen extends StatefulWidget {
  const InsightsScreen({super.key});

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> {
  String lang = 'en';
  int period = 0;
  List<dynamic> insights = [];
  Map<String, dynamic>? summary;
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final requestedLanguage = lang;
    try {
      final loadedInsights = await api.insights(requestedLanguage);
      final loadedSummary = await api.home();
      if (!mounted || lang != requestedLanguage) return;
      setState(() { insights = loadedInsights; summary = loadedSummary; error = null; });
    } catch (exception) {
      if (mounted && lang == requestedLanguage) setState(() => error = '$exception');
    } finally {
      if (mounted && lang == requestedLanguage) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final revenueKey = ['todayRevenue', 'weekRevenue', 'monthRevenue'][period];
    final periodLabel = ['Today', 'This week', 'This month'][period];
    final card = asNum(summary?['cardToday']);
    final upi = asNum(summary?['upiToday']);
    final ondc = asNum(summary?['ondcGmv']);
    final khata = asNum(summary?['khataOutstanding']);
    final days = (summary?['last7Days'] as List?) ?? [];
    final maximum = days.fold<num>(1, (largest, day) => math.max(largest, asNum(day['amount'])));
    return Scaffold(
      appBar: AppBar(title: const Text('Business insights'), actions: [IconButton(tooltip: 'Refresh insights', onPressed: _load, icon: const Icon(Icons.refresh))]),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            MerchantHeader(
              title: 'Business insights',
              subtitle: MaterialLocalizations.of(context).formatFullDate(DateTime.now()),
              child: SegmentedButton<int>(
                showSelectedIcon: false,
                style: SegmentedButton.styleFrom(foregroundColor: Colors.white, selectedBackgroundColor: Colors.white, selectedForegroundColor: FtColors.navy, side: const BorderSide(color: Colors.white38)),
                segments: const [ButtonSegment(value: 0, label: Text('Today')), ButtonSegment(value: 1, label: Text('Week')), ButtonSegment(value: 2, label: Text('Month'))],
                selected: {period},
                onSelectionChanged: (selection) => setState(() => period = selection.first),
              ),
            ),
            if (loading) const LinearProgressIndicator(),
            if (error != null) EmptyState(icon: Icons.cloud_off_outlined, message: error!, onRetry: _load),
            if (summary != null) ...[
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
                child: Column(children: [
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
                    decoration: BoxDecoration(color: const Color(0xFFE4EEF9), borderRadius: BorderRadius.circular(14)),
                    child: Row(
                      children: [
                        Container(
                          width: 48,
                          height: 48,
                          alignment: Alignment.center,
                          decoration: const BoxDecoration(color: Color(0xFF1F2D4F), shape: BoxShape.circle),
                          child: const Text('ABC', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 20)),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('ABC Bank e-Mudra Loan — Pre-Approved', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: FtColors.ink)),
                              const Text('Instant disbursal to your ABC Bank account', style: TextStyle(fontSize: 11, color: FtColors.muted)),
                              const SizedBox(height: 8),
                              const Text('₹1,00,000 (₹1 Lakh) • 11.5% interest p.a.', style: TextStyle(fontSize: 12, color: FtColors.teal, fontWeight: FontWeight.w800)),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 18),
                  Text('$periodLabel revenue', style: const TextStyle(fontSize: 12, color: FtColors.muted)),
                  const SizedBox(height: 6),
                  FittedBox(fit: BoxFit.scaleDown, child: Text(inr.format(asNum(summary![revenueKey])), style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w800, color: FtColors.teal))),
                  const SizedBox(height: 16),
                  const Divider(),
                  Row(children: [
                    SummaryMetric(value: '${summary!['todayCustomers'] ?? 0}', label: 'Customers today', light: false),
                    SummaryMetric(value: '${summary!['ondcOpenOrders'] ?? 0}', label: 'Open ONDC orders', light: false),
                    SummaryMetric(value: '${summary!['pendingPayments'] ?? 0}', label: 'Pending payments', light: false),
                  ]),
                ]),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionHeading('Revenue sources', trailing: Text('Today', style: TextStyle(fontSize: 11, color: FtColors.muted))),
                  Row(children: [
                    Semantics(
                      label: 'Card ${inr.format(card)}, UPI ${inr.format(upi)}, ONDC ${inr.format(ondc)}, Khata ${inr.format(khata)}',
                      child: SizedBox(width: 104, height: 104, child: CustomPaint(painter: _RevenuePainter(card: card, upi: upi, ondc: ondc, khata: khata), child: const Center(child: Icon(Icons.currency_rupee, color: FtColors.navy, size: 26)))),
                    ),
                    const SizedBox(width: 24),
                    Expanded(child: Column(children: [
                      _source('UPI', upi, FtColors.navy),
                      const SizedBox(height: 12),
                      _source('Card', card, FtColors.teal),
                      const SizedBox(height: 12),
                      _source('ONDC', ondc, FtColors.orange),
                      const SizedBox(height: 12),
                      _source('Khata', khata, FtColors.gold),
                    ])),
                  ]),
                  const SizedBox(height: 18),
                  const SectionHeading('Peak hours', trailing: Text('Last 7 days', style: TextStyle(fontSize: 11, color: FtColors.muted))),
                  _peakHoursChart(),
                  const SizedBox(height: 10),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(color: const Color(0xFFEAECEF), borderRadius: BorderRadius.circular(8)),
                    child: const Text('Peak: 12 PM – 1 PM (avg ₹5,200/day). Staff up during lunch rush.', style: TextStyle(fontSize: 12, color: FtColors.muted)),
                  ),
                  const SectionHeading('Daily collections', trailing: Text('Last 7 days', style: TextStyle(fontSize: 11, color: FtColors.muted))),
                  if (days.isEmpty) const EmptyState(icon: Icons.bar_chart, message: 'No collection history yet')
                  else SizedBox(
                    height: 136,
                    child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                      for (final day in days)
                        Expanded(child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
                            Tooltip(message: inr.format(asNum(day['amount'])), child: Container(height: 6 + 90 * asNum(day['amount']) / maximum, decoration: BoxDecoration(color: FtColors.navy.withValues(alpha: asNum(day['amount']) == maximum ? 1 : 0.45), borderRadius: const BorderRadius.vertical(top: Radius.circular(4))))),
                            const SizedBox(height: 8),
                            Text('${day['label']}', style: const TextStyle(fontSize: 10, color: FtColors.muted)),
                          ]),
                        )),
                    ]),
                  ),
                  const SizedBox(height: 12),
                ]),
              ),
            ],
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionHeading('Business recommendations'),
                Wrap(spacing: 6, runSpacing: 6, children: [
                  for (final item in {'en': 'English', 'hi': 'हिन्दी', 'ta': 'தமிழ்', 'te': 'తెలుగు'}.entries)
                    ChoiceChip(
                      label: Text(item.value, style: const TextStyle(fontSize: 11)),
                      selected: lang == item.key,
                      onSelected: (_) {
                        setState(() { lang = item.key; loading = true; });
                        _load();
                      },
                    ),
                ]),
                const SizedBox(height: 12),
                if (!loading && insights.isEmpty && error == null) const EmptyState(icon: Icons.lightbulb_outline, message: 'No recommendations yet'),
                ...insights.map((item) {
                  final map = item as Map<String, dynamic>;
                  return Card(
                    margin: const EdgeInsets.only(bottom: 10),
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Row(children: [const Icon(Icons.lightbulb_outline, color: FtColors.gold, size: 20), const SizedBox(width: 8), Expanded(child: Text('${map['title']}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800)))]),
                        const SizedBox(height: 10),
                        Text('${map['body']}', style: const TextStyle(fontSize: 12, height: 1.6, color: FtColors.muted)),
                        const SizedBox(height: 10),
                        Text('${map['type']}', style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: FtColors.navy)),
                      ]),
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

  Widget _peakHoursChart() {
    final bars = [8, 9, 10, 11, 12, 1, 2, 4, 6, 7, 8, 9];
    final heights = [0.35, 0.5, 0.7, 1.0, 0.95, 1.15, 0.8, 0.6, 0.45, 0.55, 0.4, 0.3];
    return SizedBox(
      height: 130,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var i = 0; i < bars.length; i++)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 3),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Container(
                      height: 18 + 72 * heights[i],
                      decoration: BoxDecoration(
                        color: i == 4 || i == 5 ? FtColors.navy : const Color(0xFFB9CDE7),
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text('${bars[i]}', style: const TextStyle(fontSize: 10, color: FtColors.muted)),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _source(String label, num amount, Color color) {
    return Row(children: [
      Container(width: 8, height: 8, color: color),
      const SizedBox(width: 8),
      Expanded(child: Text(label, style: const TextStyle(fontSize: 12))),
      Text(inr.format(amount), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
    ]);
  }
}

class _RevenuePainter extends CustomPainter {
  const _RevenuePainter({required this.card, required this.upi, required this.ondc, required this.khata});

  final num card;
  final num upi;
  final num ondc;
  final num khata;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(8);
    final paint = Paint()..style = PaintingStyle.stroke..strokeWidth = 12;
    canvas.drawArc(rect, 0, math.pi * 2, false, paint..color = FtColors.border);
    final total = card + upi + ondc + khata;
    if (total <= 0) return;
    var start = -math.pi / 2;
    final segments = [
      {'value': upi, 'color': FtColors.navy},
      {'value': card, 'color': FtColors.teal},
      {'value': ondc, 'color': FtColors.orange},
      {'value': khata, 'color': FtColors.gold},
    ];
    for (final segment in segments) {
      final angle = math.pi * 2 * (segment['value'] as num) / total;
      canvas.drawArc(rect, start, angle, false, paint..color = segment['color'] as Color);
      start += angle;
    }
  }

  @override
  bool shouldRepaint(covariant _RevenuePainter oldDelegate) => card != oldDelegate.card || upi != oldDelegate.upi || ondc != oldDelegate.ondc || khata != oldDelegate.khata;
}
