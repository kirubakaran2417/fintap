import 'package:digi_kadai/main.dart';
import 'package:digi_kadai/theme.dart';
import 'package:digi_kadai/widgets/format.dart';
import 'package:digi_kadai/widgets/logo.dart';
import 'package:flutter/material.dart';

class InsightsScreen extends StatefulWidget {
  const InsightsScreen({super.key});

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> {
  String lang = 'hi';
  List<dynamic> insights = [];
  List<dynamic> customers = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final loadedInsights = await api.insights(lang);
    final loadedCustomers = await api.customers();
    setState(() {
      insights = loadedInsights;
      customers = loadedCustomers;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const FinTapMark(light: true, compact: true)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Daily intelligence', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          const Text('Hear what to do today — not another dashboard.', style: TextStyle(color: FtColors.muted)),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            children: [
              for (final item in {'en': 'EN', 'hi': 'हिन्दी', 'ta': 'தமிழ்', 'te': 'తెలుగు'}.entries)
                ChoiceChip(
                  label: Text(item.value),
                  selected: lang == item.key,
                  selectedColor: FtColors.teal.withValues(alpha:0.2),
                  onSelected: (_) {
                    lang = item.key;
                    _load();
                  },
                ),
            ],
          ),
          const SizedBox(height: 12),
          ...insights.map((item) {
            final map = item as Map<String, dynamic>;
            return Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(map['title'].toString(), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                    const SizedBox(height: 6),
                    Text(map['body'].toString(), style: const TextStyle(height: 1.4)),
                    const SizedBox(height: 8),
                    Chip(label: Text(map['type'].toString()), visualDensity: VisualDensity.compact),
                  ],
                ),
              ),
            );
          }),
          const SizedBox(height: 16),
          const Text('Card customer profiles', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
          ...customers.map((item) {
            final map = item as Map<String, dynamic>;
            return Card(
              child: ListTile(
                title: Text(map['displayName'].toString()),
                subtitle: Text('${map['visitCount']} visits · churn ${((asNum(map['churnRisk'])) * 100).round()}%'),
                trailing: Text(inr.format(asNum(map['lifetimeSpend']))),
              ),
            );
          }),
        ],
      ),
    );
  }
}
