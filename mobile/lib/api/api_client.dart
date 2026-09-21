import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

String apiBase() {
  if (kIsWeb) return 'http://localhost:8080';
  switch (defaultTargetPlatform) {
    case TargetPlatform.android:
      return 'http://10.0.2.2:8080';
    default:
      return 'http://localhost:8080';
  }
}

class ApiClient {
  String? token;
  String? lastMobile;

  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString('token');
    lastMobile = prefs.getString('lastMobile');
  }

  Future<void> persist(String value, {String? mobile}) async {
    token = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('token', value);
    if (mobile != null && mobile.isNotEmpty) {
      lastMobile = mobile;
      await prefs.setString('lastMobile', mobile);
    }
  }

  Future<void> clear() async {
    token = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('token');
  }

  Future<Map<String, dynamic>> login(String mobile, String pin) async {
    final body = await _post('/api/auth/login', {'mobile': mobile, 'pin': pin}, auth: false);
    await persist(body['token'] as String, mobile: mobile);
    return body;
  }

  Future<Map<String, dynamic>> register({
    required String mobile,
    required String pin,
    required String ownerName,
  }) async {
    final body = await _post('/api/auth/register', {
      'mobile': mobile,
      'pin': pin,
      'ownerName': ownerName,
    }, auth: false);
    await persist(body['token'] as String, mobile: mobile);
    return body;
  }

  Future<Map<String, dynamic>> me() => _get('/api/merchants/me');

  Future<Map<String, dynamic>> onboard(Map<String, dynamic> payload) =>
      _post('/api/merchants/onboard', payload);

  Future<Map<String, dynamic>> home() => _get('/api/home');

  Future<Map<String, dynamic>> routing(String amount) =>
      _get('/api/payments/routing?amount=$amount');

  Future<Map<String, dynamic>> acceptPayment(
    double amount,
    String rail, {
    String provider = 'AUTO',
    String? customerName,
    String? customerMobile,
  }) =>
      _post('/api/payments/accept', {
        'amount': amount,
        'rail': rail,
        'provider': provider,
        if (customerName != null && customerName.isNotEmpty) 'customerLabel': customerName,
        if (customerMobile != null && customerMobile.isNotEmpty) 'customerMobile': customerMobile,
      });

  Future<Map<String, dynamic>> paymentStatus(int id, {bool refresh = true}) =>
      _get('/api/payments/$id/status?refresh=$refresh');

  Future<Map<String, dynamic>> submitMastercardDevicePayment(
    int id,
    String sessionId,
    Map<String, dynamic> devicePayment,
  ) => _post('/api/payments/$id/mastercard/device', {
        'sessionId': sessionId,
        'devicePayment': devicePayment,
      });

  Future<Map<String, dynamic>> mastercardOrder(String orderId) =>
      _get('/api/payments/mastercard/orders/$orderId');

  Future<Map<String, dynamic>> integrationStatus() => _get('/api/integrations/status');

  Future<Map<String, dynamic>> demoEvidence() => _get('/api/integrations/evidence');

  Future<Map<String, dynamic>> pingOndc() => _post('/api/integrations/ondc/ping', {});

  Future<Map<String, dynamic>> lookupOndc() => _post('/api/integrations/ondc/lookup', {});

  Future<Map<String, dynamic>> goLive() => _post('/api/integrations/go-live', {});

  Future<Map<String, dynamic>> saveMastercardCredentials(String merchantId, String apiPassword) =>
      _post('/api/integrations/mastercard/credentials', {
        'merchantId': merchantId,
        'apiPassword': apiPassword,
      });

  Future<Map<String, dynamic>> saveRazorpayCredentials(String keyId, String keySecret) =>
      _post('/api/integrations/razorpay/credentials', {
        'keyId': keyId,
        'keySecret': keySecret,
      });

  Future<Map<String, dynamic>> generateOndcKeys() => _post('/api/integrations/ondc/keys', {});

  Future<List<dynamic>> catalog() => _getList('/api/catalog');

  Future<Map<String, dynamic>> generateSku(String barcode, String hint) =>
      _post('/api/catalog/generate', {'barcode': barcode, 'shelfHint': hint});

  Future<Map<String, dynamic>> publishSku(int id) => _post('/api/catalog/$id/publish', {});

  Future<List<dynamic>> ondcOrders() => _getList('/api/ondc/orders');

  Future<Map<String, dynamic>> simulateOndcOrder() =>
      _post('/api/ondc/orders/simulate', {});

  Future<Map<String, dynamic>> updateOrder(int id, String status) =>
      _post('/api/ondc/orders/$id/status', {'status': status});

  Future<List<dynamic>> khata() => _getList('/api/khata');

  Future<Map<String, dynamic>> addKhata(Map<String, dynamic> payload) =>
      _post('/api/khata', payload);

  Future<List<dynamic>> insights(String lang) => _getList('/api/insights?lang=$lang');

  Future<List<dynamic>> customers() => _getList('/api/customers');

  Future<Map<String, dynamic>> nudgeWhatsapp(Map<String, dynamic> payload) =>
      _post('/api/nudge/whatsapp', payload);

  Future<Map<String, dynamic>> _get(String path) async {
    final response = await http.get(_uri(path), headers: _headers());
    return _decodeMap(response);
  }

  Future<List<dynamic>> _getList(String path) async {
    final response = await http.get(_uri(path), headers: _headers());
    return _decodeList(response);
  }

  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> body, {bool auth = true}) async {
    final response = await http.post(
      _uri(path),
      headers: _headers(auth: auth),
      body: jsonEncode(body),
    );
    return _decodeMap(response);
  }

  Uri _uri(String path) => Uri.parse('${apiBase()}$path');

  Map<String, String> _headers({bool auth = true}) {
    final headers = {'Content-Type': 'application/json'};
    if (auth && token != null) {
      headers['Authorization'] = 'Bearer $token';
    }
    return headers;
  }

  dynamic _json(http.Response response) {
    if (response.body.isEmpty) {
      if (response.statusCode >= 400) throw ApiException('Request failed');
      return {};
    }
    return jsonDecode(response.body);
  }

  Map<String, dynamic> _decodeMap(http.Response response) {
    final decoded = _json(response);
    if (response.statusCode >= 400) {
      throw ApiException(decoded is Map ? (decoded['error']?.toString() ?? 'Request failed') : 'Request failed');
    }
    return decoded as Map<String, dynamic>;
  }

  List<dynamic> _decodeList(http.Response response) {
    final decoded = _json(response);
    if (response.statusCode >= 400) {
      throw ApiException(decoded is Map ? (decoded['error']?.toString() ?? 'Request failed') : 'Request failed');
    }
    return decoded as List<dynamic>;
  }
}

class ApiException implements Exception {
  ApiException(this.message);
  final String message;
  @override
  String toString() => message;
}
