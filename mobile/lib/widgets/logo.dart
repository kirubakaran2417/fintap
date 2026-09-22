import 'package:digi_kadai/theme.dart';
import 'package:flutter/material.dart';

class FinTapLogo extends StatelessWidget {
  const FinTapLogo({super.key, this.size = 72, this.light = false});

  final double size;
  final bool light;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: _LogoPainter(light: light)),
    );
  }
}

class FinTapMark extends StatelessWidget {
  const FinTapMark({super.key, this.light = false, this.compact = false});

  final bool light;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final color = light ? Colors.white : FtColors.ink;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        FinTapLogo(size: compact ? 28 : 36, light: light),
        const SizedBox(width: 8),
        Text(
          'FinTap',
          style: TextStyle(
            color: color,
            fontWeight: FontWeight.w800,
            fontSize: compact ? 18 : 22,
            letterSpacing: 0,
          ),
        ),
      ],
    );
  }
}

class _LogoPainter extends CustomPainter {
  _LogoPainter({required this.light});

  final bool light;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final bg = Paint()
      ..shader = const LinearGradient(
        colors: [FtColors.navy, FtColors.primaryDark],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ).createShader(rect);
    canvas.drawRRect(RRect.fromRectAndRadius(rect, Radius.circular(size.width * 0.28)), bg);

    final arc = Paint()
      ..color = FtColors.gold
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.07
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(
      Rect.fromCircle(center: Offset(size.width * 0.50, size.height * 0.58), radius: size.width * 0.28),
      3.5,
      2.3,
      false,
      arc,
    );

    final tap = Paint()..color = Colors.white;
    canvas.drawCircle(Offset(size.width * 0.50, size.height * 0.40), size.width * 0.08, tap);
    final text = TextPainter(
      text: TextSpan(
        text: 'FT',
        style: TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w800,
          fontSize: size.width * 0.22,
          letterSpacing: 0,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    text.paint(canvas, Offset((size.width - text.width) / 2, size.height * 0.62));
  }

  @override
  bool shouldRepaint(covariant _LogoPainter oldDelegate) => oldDelegate.light != light;
}

class MiBankLogo extends StatelessWidget {
  const MiBankLogo({super.key, this.compact = true, this.onTap});

  final bool compact;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final content = Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.28),
          width: 0.8,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(2),
            decoration: const BoxDecoration(
              color: FtColors.gold,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.account_balance_rounded,
              size: 10,
              color: FtColors.navy,
            ),
          ),
          const SizedBox(width: 4),
          const Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text: 'MI',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w900,
                    fontSize: 10,
                    letterSpacing: 0.3,
                  ),
                ),
                TextSpan(
                  text: ' BANK',
                  style: TextStyle(
                    color: FtColors.gold,
                    fontWeight: FontWeight.w800,
                    fontSize: 9.5,
                    letterSpacing: 0.5,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );

    if (onTap != null) {
      return InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: content,
      );
    }
    return content;
  }
}

