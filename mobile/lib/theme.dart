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
  static const orange = Color(0xFFFF6D00);
  static const border = Color(0xFFE0E4EA);
  static const bg = Color(0xFFF5F7FA);
  static const card = Colors.white;
  static const muted = Color(0xFF5F6368);
}

ThemeData finTapTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: FtColors.navy,
      primary: FtColors.navy,
      secondary: FtColors.teal,
      surface: FtColors.card,
    ),
    scaffoldBackgroundColor: FtColors.bg,
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
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      height: 64,
      indicatorColor: FtColors.navy.withValues(alpha: 0.10),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => GoogleFonts.plusJakartaSans(
          fontSize: 11,
          fontWeight: states.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
        ),
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
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: FtColors.navy,
        minimumSize: const Size(48, 44),
        side: const BorderSide(color: FtColors.border),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    ),
    dividerTheme: const DividerThemeData(color: FtColors.border, thickness: 1),
    tabBarTheme: const TabBarThemeData(
      labelColor: FtColors.navy,
      unselectedLabelColor: FtColors.muted,
      indicatorColor: FtColors.navy,
      dividerColor: FtColors.border,
    ),
  );
}
