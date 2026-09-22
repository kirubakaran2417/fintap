# 🏆 FinTap (DigiKadai) — Final Submission

> **Universal Merchant Operating System for MSMEs & Kirana Stores via ONDC Open Commerce, Meta WhatsApp Business Cloud API, Dunzo Fulfillment & Enterprise Payment Infrastructure.**

[![Build Backend](https://img.shields.io/badge/Backend-Java%2017%20%7C%20Spring%20Boot%203.x-00e5b7?style=flat-square)](backend/)
[![Mobile App](https://img.shields.io/badge/Mobile-Flutter%203.x%20%7C%20Dart%203.x-3b82f6?style=flat-square)](mobile/)
[![ONDC Protocol](https://img.shields.io/badge/ONDC-Beckn%20v1.0.0%20BPP-8b5cf6?style=flat-square)](https://ondc.org)
[![Status](https://img.shields.io/badge/Build%20Status-PASSING%20(0%20errors)-brightgreen?style=flat-square)](#-build--verification-status)

---

## 📌 Executive Summary

**FinTap (DigiKadai)** is a bank white-label digital operating system engineered specifically for India's 60+ million Kirana stores and micro-entrepreneurs (MSMEs). By combining **ONDC Open Commerce**, **Automated Dunzo Hyper-Local Fulfillment**, **Direct Meta WhatsApp Cloud API Nudging**, and **Mastercard MPGS Payment Routing**, FinTap converts offline paper-ledger retail shops into fully digitized omni-channel sellers in under 60 seconds with **0% commission fees**.

---

## 🌟 Core Innovation Pillars

| Pillar | Technical Solution | Value Delivered |
| :--- | :--- | :--- |
| 🛍️ **ONDC Open Commerce** | Native Beckn v1.0.0 Seller BPP Node in Java Spring Boot (`/search`, `/select`, `/confirm` callbacks). | Zero-fee listing across national buyer apps (Paytm, PhonePe, Mystore). |
| 💬 **Automated WhatsApp Nudge** | Server-to-server Meta WhatsApp Business Cloud API integration (`POST /v18.0/{id}/messages`). | Direct background debt collection nudges & order status alerts returning `wamid...` delivery IDs. |
| 🚚 **Dunzo Hyper-Local Dispatch** | Automated 3-minute pickup dispatcher state machine (`ASSIGNED_DUNZO` $\rightarrow$ `DELIVERED`). | Automated rider assignment and last-mile order delivery tracking. |
| 💳 **Mastercard MPGS & SoftPOS** | Mastercard Hosted Checkout (API v100) with 3DS 2.0 auth & audio payment prompts. | Hardware-free card acceptance and instant payment reconciliation. |
| 💰 **e-Mudra Micro Loans** | Digital Udhaar/Jama ledger & cashflow transaction underwriting scorer. | Pre-approved ₹1,00,000 collateral-free credit access for Kirana owners. |

---

## 📐 System Architecture Blueprint

```
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                         📱 FLUTTER MOBILE / WEB UI                          │
 │         Material Design 3 • Dart 3.x Engine • HSL Custom Design System        │
 └──────────────────────────────────────┬──────────────────────────────────────┘
                                        │ REST / JSON (JWT Auth)
 ┌──────────────────────────────────────▼──────────────────────────────────────┐
 │                       ⚙️ SPRING BOOT 3 CORE BACKEND                          │
 │   CommerceController  │  OndcNetworkController  │  NudgeController          │
 │   CommerceService     │  OndcNetworkService     │  NudgeService & Dunzo     │
 ├─────────────────────────────────────────────────────────────────────────────┤
 │                   Data Layer: Spring Data JPA (H2 / PostgreSQL)             │
 └──────┬──────────────────────┬──────────────────────┬────────────────────────┘
        │                      │                      │
 ┌──────▼────────┐      ┌──────▼────────┐      ┌──────▼────────┐
 │ 🌐 ONDC BECKN │      │ 💬 META CLOUD │      │ 💳 MASTERCARD │
 │   GATEWAY     │      │ WHATSAPP API  │      │ MPGS GATEWAY  │
 └───────────────┘      └───────────────┘      └───────────────┘
```

---

## 🎬 Formal Project Presentation

An interactive, high-resolution presentation deck is available for project evaluation:

- 📊 **Interactive Deck**: [`presentation_deck.html`](file:///e:/GIRI/Workspaces/FinTap/presentation_deck.html)
- 📌 **Key Highlights**: Contains complete system architecture diagrams, ONDC Beckn protocol sequence flows, Meta API payload previews, and live execution scripts.

---

## 🚀 Quick Start Guide

Follow these steps to run the complete FinTap platform locally on your machine.

### Prerequisites

| Component | Minimum Version Required |
| :--- | :--- |
| **Java JDK** | JDK 17 or JDK 21 |
| **Apache Maven** | Maven 3.8+ |
| **Flutter SDK** | Flutter 3.x (Stable Channel) |
| **Web Browser** | Google Chrome / Microsoft Edge |

---

### Step 1: Start the Spring Boot Backend

```powershell
cd backend
mvn spring-boot:run
```

- **Backend Base URL**: `http://localhost:8080`
- **H2 Console**: `http://localhost:8080/h2` (`jdbc:h2:file:./data/fintap`, user: `sa`, password: *(empty)*)

---

### Step 2: Start the Flutter Mobile / Web App

Open a second terminal window:

```powershell
cd mobile
flutter pub get
flutter run -d chrome
```

---

### 🔑 Demo Login Credentials

Pre-seeded merchant account for instant testing:
- **Mobile Number**: `9876543210`
- **PIN**: `1234`
- **Shop Name**: *Lakshmi Kirana*

---

## 📑 Project Documentation Index

| File | Description |
| :--- | :--- |
| 📊 [`presentation_deck.html`](file:///e:/GIRI/Workspaces/FinTap/presentation_deck.html) | Interactive 11-Slide Formal Project Presentation Deck. |
| 📌 [`PROJECT_OVERVIEW.md`](file:///e:/GIRI/Workspaces/FinTap/PROJECT_OVERVIEW.md) | Executive summary, business model, market impact, and value proposition. |
| 🔄 [`SYSTEM_PROJECT_FLOW.md`](file:///e:/GIRI/Workspaces/FinTap/SYSTEM_PROJECT_FLOW.md) | Comprehensive end-to-end data flow and sequence diagrams. |
| 🏗️ [`CODE_CLASS_ARCHITECTURE.md`](file:///e:/GIRI/Workspaces/FinTap/CODE_CLASS_ARCHITECTURE.md) | Deep dive into package layout, Spring Boot services, and Flutter screens. |
| 🔌 [`INTEGRATIONS.md`](file:///e:/GIRI/Workspaces/FinTap/INTEGRATIONS.md) | Detailed documentation on ONDC Beckn, Meta WhatsApp, Dunzo & MPGS APIs. |

---

## ✅ Build & Verification Status

- **Spring Boot Backend**: `mvn compile` $\rightarrow$ **BUILD SUCCESS**
- **Flutter Frontend**: `flutter analyze` $\rightarrow$ **0 Errors, 0 Warnings**
- **Database Migrations**: Dynamic H2 `VARCHAR(64)` DDL status migration applied cleanly.

---

### 🛡️ License & Submission Notice

This repository represents the official final submission for the **FinTap (DigiKadai)** project. All rights reserved.
