import 'package:intl/intl.dart';

final inr = NumberFormat.currency(locale: 'en_IN', symbol: '₹');
final dateFormat = DateFormat('dd MMM yyyy');
final dateTimeFormat = DateFormat('dd MMM yyyy, hh:mm a');

num asNum(dynamic value) => value is num ? value : num.tryParse('$value') ?? 0;

int asInt(dynamic value) => asNum(value).round();

String formatDate(dynamic date) {
  if (date == null) return '';
  if (date is DateTime) return dateFormat.format(date);
  final parsed = DateTime.tryParse(date.toString());
  if (parsed != null) {
    return dateFormat.format(parsed.toLocal());
  }
  return date.toString();
}

String formatDateTime(dynamic date) {
  if (date == null) return '';
  if (date is DateTime) return dateTimeFormat.format(date);
  final parsed = DateTime.tryParse(date.toString());
  if (parsed != null) {
    return dateTimeFormat.format(parsed.toLocal());
  }
  return date.toString();
}
