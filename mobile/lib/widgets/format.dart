import 'package:intl/intl.dart';

final inr = NumberFormat.currency(locale: 'en_IN', symbol: '₹');

num asNum(dynamic value) => value is num ? value : num.tryParse('$value') ?? 0;

int asInt(dynamic value) => asNum(value).round();
