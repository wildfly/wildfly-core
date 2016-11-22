/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.domain.management.security;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManager.PathEventContext;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

/**
 * An extension to {@link AbstractKeyManagerService} so that a KeyManager[] can be provided based on a JKS file based key store.
 *
 *
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class FileKeyManagerService extends AbstractKeyManagerService {

    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    private final InjectedValue<PathManager> pathManager = new InjectedValue<PathManager>();

    private volatile String provider;
    private volatile String path;
    private volatile String relativeTo;
    private volatile char[] keystorePassword;
    private volatile char[] keyPassword;
    private volatile String alias;
    private volatile FileKeystore keyStore;
    private String autoGenerateCertHostName;

    FileKeyManagerService(final String provider, final String path, final String relativeTo, final char[] keystorePassword,
                          final char[] keyPassword, final String alias, String autoGenerateCertHostName) {
        super(keystorePassword, keyPassword);

        this.provider = provider;
        this.path = path;
        this.relativeTo = relativeTo;
        this.keystorePassword = keystorePassword;
        this.keyPassword = keyPassword;
        this.alias = alias;
        this.autoGenerateCertHostName = autoGenerateCertHostName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRelativeTo() {
        return relativeTo;
    }

    public void setRelativeTo(final String relativeTo) {
        this.relativeTo = relativeTo;
    }

    public char[] getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(char[] keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public char[] getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(char[] keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    /*
     * Service Lifecycle Methods
     */

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        keyStore = null;
    }

    @Override
    protected boolean isLazy() {
        return keyStore == null;
    }

    @Override
    protected KeyStore loadKeyStore(boolean startup) {
        try {
            if (keyStore != null) {
                if (keyStore.isModified()) {
                    keyStore.load();
                }
                return keyStore.getKeyStore();
            }
            String file = path;
            if (relativeTo != null) {
                PathManager pm = pathManager.getValue();

                file = pm.resolveRelativePathEntry(file, relativeTo);
                pm.registerCallback(relativeTo, new Callback() {

                    @Override
                    public void pathModelEvent(PathEventContext eventContext, String name) {
                        if (!eventContext.isResourceServiceRestartAllowed()) {
                            eventContext.reloadRequired();
                        }
                    }

                    @Override
                    public void pathEvent(Event event, PathEntry pathEntry) {
                        // Service dependencies should trigger a stop and start.
                    }
                }, Event.REMOVED, Event.UPDATED);
            }
            File path = new File(file);
            if (!path.exists() && autoGenerateCertHostName != null) {
                if(startup) {
                    DomainManagementLogger.SECURITY_LOGGER.keystoreWillBeCreated(file, autoGenerateCertHostName);
                    return null;
                } else {
                    generateFileKeyStore(path);
                }
            }

            keyStore = FileKeystore.newKeyStore(provider, file, keystorePassword, keyPassword, alias);
            keyStore.load();

            return keyStore.getKeyStore();
        } catch (StartException e) {
            throw new IllegalStateException(e);
        }
    }

    private void generateFileKeyStore(File path) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();
            X509Certificate cert = generateCertificate(pair);


            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, keystorePassword);

            //Generate self signed certificate
            X509Certificate[] chain = new X509Certificate[1];
            chain[0] = cert;
            keyStore.setKeyEntry(alias, pair.getPrivate(), keyPassword, chain);
            try (FileOutputStream stream = new FileOutputStream(path)) {
                keyStore.store(stream, keystorePassword);
            }
            DomainManagementLogger.SECURITY_LOGGER.keystoreHasBeenCreated(path.toString(), getSha1Fingerprint(cert, "SHA-1"), getSha1Fingerprint(cert, "SHA-256"));

        } catch (Exception e) {
            throw DomainManagementLogger.SECURITY_LOGGER.failedToGenerateSelfSignedCertificate(e);
        }
    }

    X509Certificate generateCertificate(KeyPair pair) throws Exception {
        PrivateKey privkey = pair.getPrivate();
        X509CertificateBuilder builder = new X509CertificateBuilder();
        Date from = new Date();
        Date to = new Date(from.getTime() + (1000L * 60L * 60L * 24L * 365L * 10L));
        BigInteger sn = new BigInteger(64, new SecureRandom());

        builder.setNotValidAfter(ZonedDateTime.ofInstant(Instant.ofEpochMilli(to.getTime()), TimeZone.getDefault().toZoneId()));
        builder.setNotValidBefore(ZonedDateTime.ofInstant(Instant.ofEpochMilli(from.getTime()), TimeZone.getDefault().toZoneId()));
        builder.setSerialNumber(sn);
        X500Principal owner = new X500Principal("CN=" + autoGenerateCertHostName);
        builder.setSubjectDn(owner);
        builder.setIssuerDn(owner);
        builder.setPublicKey(pair.getPublic());
        builder.setVersion(3);
        builder.setSignatureAlgorithmName(SHA_256_WITH_RSA);
        builder.setSigningKey(privkey);
        return builder.build();
    }

    private static String getSha1Fingerprint(X509Certificate cert, String algo) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algo);
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return hexify(digest);

    }

    private static String hexify (byte[] bytes) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        StringBuffer buf = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; ++i) {
            if(i > 0) {
                buf.append(":");
            }
            buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
            buf.append(hexDigits[bytes[i] & 0x0f]);
        }
        return buf.toString();
    }

    public Injector<PathManager> getPathManagerInjector() {
        return pathManager;
    }

}
