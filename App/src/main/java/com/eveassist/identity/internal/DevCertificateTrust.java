package com.eveassist.identity.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Trusts one more certificate, when {@code eveassist.dev.trusted-certificate} names it. Only the dev profile sets it.
 * Spring Security makes several HTTP clients of its own for sign-in, so this changes the JVM's default. It has to run
 * before the context starts, because the sign-in configuration is read from Keycloak during start-up.
 */
public class DevCertificateTrust implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        var path = event.getEnvironment().getProperty("eveassist.dev.trusted-certificate");
        if (path == null) {
            return;
        }
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            var certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            var context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new AlsoTrusting(certificate, jvmDefault())}, null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot trust the certificate in " + path, e);
        }
    }

    private static X509TrustManager jvmDefault() throws GeneralSecurityException {
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("The JVM has no default X509 trust manager");
    }

    private record AlsoTrusting(X509Certificate certificate, X509TrustManager fallback) implements X509TrustManager {

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain[0].equals(certificate)) {
                chain[0].checkValidity();
            } else {
                fallback.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            fallback.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return fallback.getAcceptedIssuers();
        }
    }
}
