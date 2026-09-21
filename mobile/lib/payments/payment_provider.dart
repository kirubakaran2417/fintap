import 'package:flutter/services.dart';

enum CardPaymentProvider {
  auto('AUTO', 'Automatic'),
  mastercard('MASTERCARD', 'Mastercard hosted checkout'),
  razorpay('RAZORPAY', 'Razorpay test checkout'),
  tapOnPhone('MASTERCARD', 'Mastercard Tap on Phone');

  const CardPaymentProvider(this.apiValue, this.label);
  final String apiValue;
  final String label;
}

abstract interface class TapOnPhoneAdapter {
  Future<bool> isAvailable();
  Future<Map<String, dynamic>> collect({required String orderId, required String sessionId});
}

/// Boundary for an acquirer-provided CPoC/MPoC Android SDK. The method channel
/// intentionally remains unavailable until that certified SDK is installed.
class MethodChannelTapOnPhoneAdapter implements TapOnPhoneAdapter {
  static const _channel = MethodChannel('com.fintap/tap_on_phone');

  @override
  Future<bool> isAvailable() async {
    try {
      return await _channel.invokeMethod<bool>('isAvailable') ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  @override
  Future<Map<String, dynamic>> collect({required String orderId, required String sessionId}) async {
    final result = await _channel.invokeMapMethod<String, dynamic>('collect', {
      'orderId': orderId,
      'sessionId': sessionId,
    });
    if (result == null || result['devicePayment'] == null) {
      throw PlatformException(code: 'NO_DEVICE_PAYMENT', message: 'Certified SDK returned no devicePayment payload');
    }
    return result;
  }
}
