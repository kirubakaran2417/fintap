package com.fintap.digikadai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integrations")
public class IntegrationProperties {

    private String publicBaseUrl = "http://localhost:8080";
    private boolean allowRuntimeCredentials;
    private final Ondc ondc = new Ondc();
    private final Mastercard mastercard = new Mastercard();
    private final Razorpay razorpay = new Razorpay();

    public Ondc getOndc() {
        return ondc;
    }

    public Mastercard getMastercard() {
        return mastercard;
    }

    public Razorpay getRazorpay() {
        return razorpay;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isAllowRuntimeCredentials() {
        return allowRuntimeCredentials;
    }

    public void setAllowRuntimeCredentials(boolean allowRuntimeCredentials) {
        this.allowRuntimeCredentials = allowRuntimeCredentials;
    }

    public static class Ondc {
        private boolean enabled;
        private String subscriberId = "";
        private String uniqueKeyId = "ukid-1";
        private String subscriberUrl = "";
        private String signingPrivateKey = "";
        private String signingPublicKey = "";
        private String encryptionPrivateKey = "";
        private String encryptionPublicKey = "";
        private String registryEncryptionPublicKey = "";
        private String siteVerificationToken = "";
        private boolean verifyIncoming = true;
        private boolean allowHttpCallbacks;
        private String registryUrl = "";
        private String gatewayUrl = "";
        private String mockBppUrl = "";
        private String domain = "ONDC:RET10";
        private String city = "std:080";
        private String coreVersion = "1.2.0";
        private String bppId = "";
        private String bppUri = "";
        private String bapId = "";
        private String bapUri = "";
        private String country = "IND";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        public String getUniqueKeyId() {
            return uniqueKeyId;
        }

        public void setUniqueKeyId(String uniqueKeyId) {
            this.uniqueKeyId = uniqueKeyId;
        }

        public String getSubscriberUrl() {
            return subscriberUrl;
        }

        public void setSubscriberUrl(String subscriberUrl) {
            this.subscriberUrl = subscriberUrl;
        }

        public String getSigningPrivateKey() {
            return signingPrivateKey;
        }

        public void setSigningPrivateKey(String signingPrivateKey) {
            this.signingPrivateKey = signingPrivateKey;
        }

        public String getSigningPublicKey() {
            return signingPublicKey;
        }

        public void setSigningPublicKey(String signingPublicKey) {
            this.signingPublicKey = signingPublicKey;
        }

        public String getEncryptionPrivateKey() {
            return encryptionPrivateKey;
        }

        public void setEncryptionPrivateKey(String encryptionPrivateKey) {
            this.encryptionPrivateKey = encryptionPrivateKey;
        }

        public String getEncryptionPublicKey() {
            return encryptionPublicKey;
        }

        public void setEncryptionPublicKey(String encryptionPublicKey) {
            this.encryptionPublicKey = encryptionPublicKey;
        }

        public String getRegistryEncryptionPublicKey() {
            return registryEncryptionPublicKey;
        }

        public void setRegistryEncryptionPublicKey(String registryEncryptionPublicKey) {
            this.registryEncryptionPublicKey = registryEncryptionPublicKey;
        }

        public String getSiteVerificationToken() {
            return siteVerificationToken;
        }

        public void setSiteVerificationToken(String siteVerificationToken) {
            this.siteVerificationToken = siteVerificationToken;
        }

        public boolean isVerifyIncoming() {
            return verifyIncoming;
        }

        public void setVerifyIncoming(boolean verifyIncoming) {
            this.verifyIncoming = verifyIncoming;
        }

        public boolean isAllowHttpCallbacks() {
            return allowHttpCallbacks;
        }

        public void setAllowHttpCallbacks(boolean allowHttpCallbacks) {
            this.allowHttpCallbacks = allowHttpCallbacks;
        }

        public String getRegistryUrl() {
            return registryUrl;
        }

        public void setRegistryUrl(String registryUrl) {
            this.registryUrl = registryUrl;
        }

        public String getGatewayUrl() {
            return gatewayUrl;
        }

        public void setGatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl;
        }

        public String getMockBppUrl() {
            return mockBppUrl;
        }

        public void setMockBppUrl(String mockBppUrl) {
            this.mockBppUrl = mockBppUrl;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCoreVersion() {
            return coreVersion;
        }

        public void setCoreVersion(String coreVersion) {
            this.coreVersion = coreVersion;
        }

        public String getBppId() {
            return bppId == null || bppId.isBlank() ? subscriberId : bppId;
        }

        public void setBppId(String bppId) {
            this.bppId = bppId;
        }

        public String getBppUri() {
            return bppUri == null || bppUri.isBlank() ? subscriberUrl : bppUri;
        }

        public void setBppUri(String bppUri) {
            this.bppUri = bppUri;
        }

        public String getBapId() {
            return bapId == null || bapId.isBlank() ? "fintap.buyer.local" : bapId;
        }

        public void setBapId(String bapId) {
            this.bapId = bapId;
        }

        public String getBapUri() {
            return bapUri == null || bapUri.isBlank() ? subscriberUrl : bapUri;
        }

        public void setBapUri(String bapUri) {
            this.bapUri = bapUri;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public boolean keysReady() {
            return signingPrivateKey != null && !signingPrivateKey.isBlank()
                    && subscriberId != null && !subscriberId.isBlank();
        }
    }

    public static class Mastercard {
        private boolean gatewayEnabled;
        private String gatewayBaseUrl = "https://test-gateway.mastercard.com";
        private String merchantId = "";
        private String apiPassword = "";
        private String apiVersion = "100";
        private String currency = "INR";
        private String checkoutScriptUrl = "";
        private boolean developersEnabled;
        private String developersBaseUrl = "https://sandbox.api.mastercard.com";
        private String consumerKey = "";
        private String keystorePath = "";
        private String keystorePassword = "";
        private String keyAlias = "";

        public boolean isGatewayEnabled() {
            return gatewayEnabled;
        }

        public void setGatewayEnabled(boolean gatewayEnabled) {
            this.gatewayEnabled = gatewayEnabled;
        }

        public String getGatewayBaseUrl() {
            return gatewayBaseUrl;
        }

        public void setGatewayBaseUrl(String gatewayBaseUrl) {
            this.gatewayBaseUrl = gatewayBaseUrl;
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public String getApiPassword() {
            return apiPassword;
        }

        public void setApiPassword(String apiPassword) {
            this.apiPassword = apiPassword;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getCheckoutScriptUrl() {
            if (checkoutScriptUrl == null || checkoutScriptUrl.isBlank()) {
                String base = gatewayBaseUrl == null ? "" : gatewayBaseUrl.replaceAll("/+$", "");
                return base + "/static/checkout/checkout.min.js";
            }
            return checkoutScriptUrl;
        }

        public void setCheckoutScriptUrl(String checkoutScriptUrl) {
            this.checkoutScriptUrl = checkoutScriptUrl;
        }

        public boolean isDevelopersEnabled() {
            return developersEnabled;
        }

        public void setDevelopersEnabled(boolean developersEnabled) {
            this.developersEnabled = developersEnabled;
        }

        public String getDevelopersBaseUrl() {
            return developersBaseUrl;
        }

        public void setDevelopersBaseUrl(String developersBaseUrl) {
            this.developersBaseUrl = developersBaseUrl;
        }

        public String getConsumerKey() {
            return consumerKey;
        }

        public void setConsumerKey(String consumerKey) {
            this.consumerKey = consumerKey;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public void setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public void setKeyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
        }

        public boolean gatewayReady() {
            return gatewayEnabled && merchantId != null && !merchantId.isBlank()
                    && apiPassword != null && !apiPassword.isBlank();
        }

        public boolean developersReady() {
            return developersEnabled && consumerKey != null && !consumerKey.isBlank()
                    && keystorePath != null && !keystorePath.isBlank();
        }
    }

    public static class Razorpay {
        private boolean enabled;
        private String keyId = "";
        private String keySecret = "";
        private String baseUrl = "https://api.razorpay.com";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean ready() {
            return enabled && keyId != null && keyId.startsWith("rzp_")
                    && keySecret != null && !keySecret.isBlank();
        }
    }
}
