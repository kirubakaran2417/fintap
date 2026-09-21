import 'package:digi_kadai/theme.dart';
import 'package:flutter/material.dart';

class SetupProgress extends StatelessWidget {
  const SetupProgress({super.key, required this.step});

  final int step;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Setup step $step of 2',
      child: Padding(
        padding: const EdgeInsets.only(bottom: 24),
        child: Row(children: [
          for (final current in [1, 2])
            Container(
              width: 44,
              height: 4,
              margin: const EdgeInsets.only(right: 8),
              decoration: BoxDecoration(color: current < step ? FtColors.teal : current == step ? FtColors.navy : FtColors.border, borderRadius: BorderRadius.circular(2)),
            ),
        ]),
      ),
    );
  }
}

class MerchantHeader extends StatelessWidget {
  const MerchantHeader({
    super.key,
    required this.title,
    required this.subtitle,
    this.color = FtColors.navy,
    this.trailing,
    this.child,
  });

  final String title;
  final String subtitle;
  final Color color;
  final Widget? trailing;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 22),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [color, Color.lerp(color, Colors.black, 0.22)!],
        ),
        borderRadius: const BorderRadius.vertical(bottom: Radius.circular(18)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 4),
                    Text(subtitle, style: const TextStyle(color: Colors.white70, fontSize: 12)),
                  ],
                ),
              ),
              if (trailing != null) ...[const SizedBox(width: 12), trailing!],
            ],
          ),
          if (child != null) ...[const SizedBox(height: 18), child!],
        ],
      ),
    );
  }
}

class SummaryMetric extends StatelessWidget {
  const SummaryMetric({super.key, required this.value, required this.label, this.light = true, this.color});

  final String value;
  final String label;
  final bool light;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
        child: Column(
          children: [
            FittedBox(
              fit: BoxFit.scaleDown,
              child: Text(value, style: TextStyle(color: color ?? (light ? Colors.white : FtColors.ink), fontSize: 19, fontWeight: FontWeight.w800)),
            ),
            const SizedBox(height: 4),
            Text(label, textAlign: TextAlign.center, style: TextStyle(color: light ? Colors.white70 : FtColors.muted, fontSize: 10)),
          ],
        ),
      ),
    );
  }
}

class SectionHeading extends StatelessWidget {
  const SectionHeading(this.title, {super.key, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 20, bottom: 10),
      child: Row(
        children: [
          Expanded(child: Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800))),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.message, this.onRetry});

  final IconData icon;
  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: FtColors.muted, size: 32),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center, style: const TextStyle(color: FtColors.muted)),
          if (onRetry != null) TextButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: const Text('Try again')),
        ],
      ),
    );
  }
}