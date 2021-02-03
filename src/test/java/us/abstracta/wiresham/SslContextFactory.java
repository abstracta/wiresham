package us.abstracta.wiresham;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public abstract class SslContextFactory {

  private SslContextFactory() {
  }

  public static SSLContext buildSslContext() throws GeneralSecurityException, IOException {
    KeyManager[] keyManagers = buildKeyManagers(
        TestResource.getResourceFile("/keystore.jks"), "changeit");
    TrustManager[] trustManagers = buildTrustManagers(TestResource.getResourceFile("/cacerts.jks"),
        "changeit");
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagers, trustManagers, SecureRandom.getInstanceStrong());
    return sslContext;
  }

  private static KeyManager[] buildKeyManagers(File keyStore, String password)
      throws GeneralSecurityException, IOException {
    KeyStore store = buildKeyStore(keyStore, password);
    KeyManagerFactory factory = KeyManagerFactory
        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(store, password.toCharArray());
    return factory.getKeyManagers();
  }

  private static KeyStore buildKeyStore(File keyStore, String password)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    store.load(new FileInputStream(keyStore), password.toCharArray());
    return store;
  }

  private static TrustManager[] buildTrustManagers(File trustStore, String password)
      throws GeneralSecurityException, IOException {
    TrustManagerFactory factory = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(buildKeyStore(trustStore, password));
    return factory.getTrustManagers();
  }

}
