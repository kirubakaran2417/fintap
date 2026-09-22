import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class FtColors {
  static const ink = Color(0xFF1A1A2E);
  static const navy = Color(0xFF1C4587);
  static const primaryDark = Color(0xFF0F2D5E);
  static const teal = Color(0xFF0F9D58);
  static const gold = Color(0xFFC9A227);
  static const purple = Color(0xFF4A0E8F);
  static const danger = Color(0xFFDB4437);
  static const border = Color(0xFFE0E4EA);
  static const bg = Color(0xFFF5F7FA);
  static const card = Colors.white;
  static const muted = Color(0xFF5F6368);
  static const chrome = Color(0xFFE3EAF5);
}

ThemeData buyerTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: FtColors.navy,
      primary: FtColors.navy,
      secondary: FtColors.teal,
      surface: FtColors.card,
    ),
    scaffoldBackgroundColor: Colors.transparent,
  );
  return base.copyWith(
    textTheme: GoogleFonts.plusJakartaSansTextTheme(base.textTheme).apply(
      bodyColor: FtColors.ink,
      displayColor: FtColors.ink,
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: FtColors.navy,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: false,
      titleTextStyle: GoogleFonts.plusJakartaSans(
        color: Colors.white,
        fontWeight: FontWeight.w700,
        fontSize: 16,
      ),
    ),
    cardTheme: CardThemeData(
      color: FtColors.card,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: FtColors.border),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: FtColors.border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: FtColors.navy, width: 1.6),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: FtColors.navy,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: GoogleFonts.plusJakartaSans(fontWeight: FontWeight.w700, fontSize: 13),
      ),
    ),
  );
}

class BuyerBackdrop extends StatelessWidget {
  const BuyerBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: FtColors.chrome,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: Stack(
            children: [
              const Positioned.fill(child: _BuyerPattern()),
              child,
            ],
          ),
        ),
      ),
    );
  }
}

class _BuyerPattern extends StatelessWidget {
  const _BuyerPattern();

  @override
  Widget build(BuildContext context) {
    return const DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFE8EEF8), FtColors.bg, Color(0xFFD7E4F4)],
        ),
      ),
      child: CustomPaint(painter: _BlobPainter(), child: SizedBox.expand()),
    );
  }
}

class _BlobPainter extends CustomPainter {
  const _BlobPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final navy = Paint()..color = FtColors.navy.withValues(alpha: 0.08);
    final teal = Paint()..color = FtColors.teal.withValues(alpha: 0.10);
    final gold = Paint()..color = FtColors.gold.withValues(alpha: 0.12);
    canvas.drawCircle(Offset(size.width * 0.92, -40), 140, navy);
    canvas.drawCircle(Offset(-30, size.height * 0.22), 110, teal);
    canvas.drawCircle(Offset(size.width * 0.18, size.height * 0.92), 160, navy);
    canvas.drawCircle(Offset(size.width * 0.78, size.height * 0.62), 90, gold);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
