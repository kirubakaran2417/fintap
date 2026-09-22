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

final buyerSession = BuyerSession();

class BuyerSession {
  String? token;
  String? name;
  String? mobile;

  Map<String, String> get headers {
    final map = <String, String>{'Content-Type': 'application/json'};
    if (token != null && token!.isNotEmpty) {
      map['Authorization'] = 'Bearer $token';
    }
    return map;
  }

  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString('buyerToken');
    name = prefs.getString('buyerName');
    mobile = prefs.getString('buyerMobile');
  }

  Future<void> save({required String token, required String name, required String mobile}) async {
    this.token = token;
    this.name = name;
    this.mobile = mobile;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('buyerToken', token);
    await prefs.setString('buyerName', name);
    await prefs.setString('buyerMobile', mobile);
  }

  Future<void> clear() async {
    token = null;
    name = null;
    mobile = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('buyerToken');
    await prefs.remove('buyerName');
    await prefs.remove('buyerMobile');
  }

  Future<Map<String, dynamic>> register({
    required String name,
    required String mobile,
    required String pin,
    required String address,
  }) async {
    final response = await http.post(
      Uri.parse('${apiBase()}/api/buyer/register'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'name': name, 'mobile': mobile, 'pin': pin, 'address': address}),
    );
    final body = _decode(response);
    if (response.statusCode >= 400) {
      throw Exception(body['error'] ?? body['message'] ?? response.body);
    }
    await save(
      token: '${body['token']}',
      name: '${body['name']}',
      mobile: '${body['mobile']}',
    );
    return body;
  }

  Future<Map<String, dynamic>> login({required String mobile, required String pin}) async {
    final response = await http.post(
      Uri.parse('${apiBase()}/api/buyer/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'mobile': mobile, 'pin': pin}),
    );
    final body = _decode(response);
    if (response.statusCode >= 400) {
      throw Exception(body['error'] ?? body['message'] ?? response.body);
    }
    await save(
      token: '${body['token']}',
      name: '${body['name']}',
      mobile: '${body['mobile']}',
    );
    return body;
  }

  Map<String, dynamic> _decode(http.Response response) {
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, dynamic>) return decoded;
    } catch (_) {}
    return {'message': response.body};
  }
}
