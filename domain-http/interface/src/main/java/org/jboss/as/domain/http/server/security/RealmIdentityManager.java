/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.http.server.security;

import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;
import static org.jboss.as.domain.management.RealmConfigurationConstants.DIGEST_PLAIN_TEXT;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.GSSContextCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.util.HexConverter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.ietf.jgss.GSSException;
import org.jboss.as.controller.security.InetAddressPrincipal;
import org.jboss.as.core.security.SimplePrincipal;
import org.jboss.as.core.security.SubjectUserInfo;
import org.jboss.as.domain.http.server.logging.HttpServerLogger;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.AuthorizingCallbackHandler;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.SubjectIdentity;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.interfaces.DigestPassword;

/**
 * {@link IdentityManager} implementation to wrap the current security realms.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @deprecated Elytron integration will make this obsolete.
 */
@Deprecated
public class RealmIdentityManager implements IdentityManager {

    private static final ThreadLocal<ThreadLocalStore> requestSpecific = new ThreadLocal<ThreadLocalStore>();

    static void setRequestSpecific(final AuthMechanism mechanism, final InetAddress clientAddress) {
        ThreadLocalStore store = new ThreadLocalStore();
        store.requestMechanism = mechanism;
        store.inetAddress = clientAddress;

        requestSpecific.set(store);
    }

    static void clearRequestSpecific() {
        ThreadLocalStore store = requestSpecific.get();
        if (store != null && store.subjectIdentity != null) {
            store.subjectIdentity.logout();
        }

        requestSpecific.set(null);
    }

    private AuthMechanism getRequestMeschanism() {
        ThreadLocalStore store = requestSpecific.get();

        return store == null ? null : store.requestMechanism;
    }

    private InetAddress getInetAddress() {
        ThreadLocalStore store = requestSpecific.get();

        return store == null ? null : store.inetAddress;
    }

    void setCurrentSubjectIdentity(final SubjectIdentity subjectIdentity) {
        requestSpecific.get().subjectIdentity = subjectIdentity;
    }

    private final SecurityRealm securityRealm;

    public RealmIdentityManager(final SecurityRealm securityRealm) {
        this.securityRealm = securityRealm;
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    private boolean plainTextDigest() {
        Map<String, String> mechConfig = securityRealm.getMechanismConfig(AuthMechanism.DIGEST);
        boolean plainTextDigest = true;
        if (mechConfig.containsKey(DIGEST_PLAIN_TEXT)) {
            plainTextDigest = Boolean.parseBoolean(mechConfig.get(DIGEST_PLAIN_TEXT));
        }

        return plainTextDigest;
    }

    /*
     * This verify method is used to verify both BASIC authentication and DIGEST authentication requests.
     */

    @Override
    public Account verify(String id, Credential credential) {
        if (id == null || id.length() == 0) {
            HttpServerLogger.ROOT_LOGGER.debug("Missing or empty username received, aborting account verification.");
            return null;
        }

        if (credential instanceof PasswordCredential) {
            return verify(id, (PasswordCredential) credential);
        } else if (credential instanceof DigestCredential) {
            return verify(id, (DigestCredential) credential);
        }

        throw HttpServerLogger.ROOT_LOGGER.invalidCredentialType(credential.getClass().getName());
    }

    private Account verify(String id, PasswordCredential credential) {
        assertMechanism(AuthMechanism.PLAIN);
        if (credential == null) {
            return null;
        }

        AuthorizingCallbackHandler ach = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.PLAIN);
        Callback[] callbacks = new Callback[3];
        callbacks[0] = new RealmCallback("Realm", securityRealm.getName());
        callbacks[1] = new NameCallback("Username", id);
        callbacks[2] = new EvidenceVerifyCallback(new PasswordGuessEvidence(credential.getPassword()));

        try {
            ach.handle(callbacks);
        } catch (Exception e) {
            ROOT_LOGGER.debug("Failure handling Callback(s) for BASIC authentication.", e);
            return null;
        }

        if (((EvidenceVerifyCallback) callbacks[2]).isVerified() == false) {
            return null;
        }

        Principal user = new SimplePrincipal(id);
        Collection<Principal> userCol = Collections.singleton(user);
        SubjectUserInfo supplemental;
        try {
            supplemental = ach.createSubjectUserInfo(userCol);
        } catch (IOException e) {
            return null;
        }
        addInetPrincipal(supplemental.getSubject().getPrincipals());

        return new RealmIdentityAccount(supplemental.getSubject(), user);
    }

    private Account verify(String id, DigestCredential credential) {
        assertMechanism(AuthMechanism.DIGEST);

        AuthorizingCallbackHandler ach = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.DIGEST);
        Callback[] callbacks = new Callback[3];
        callbacks[0] = new RealmCallback("Realm", credential.getRealm());
        callbacks[1] = new NameCallback("Username", id);
        boolean plainText = plainTextDigest();
        if (plainText) {
            callbacks[2] = new PasswordCallback("Password", false);
        } else {
            callbacks[2] = new CredentialCallback(org.wildfly.security.credential.PasswordCredential.class, ALGORITHM_DIGEST_MD5);
        }

        try {
            ach.handle(callbacks);
        } catch (Exception e) {
            ROOT_LOGGER.debug("Failure handling Callback(s) for BASIC authentication.", e);
            return null;
        }

        byte[] ha1;
        if (plainText) {
            MessageDigest digest = null;
            try {
                digest = credential.getAlgorithm().getMessageDigest();

                digest.update(id.getBytes(UTF_8));
                digest.update((byte) ':');
                digest.update(credential.getRealm().getBytes(UTF_8));
                digest.update((byte) ':');
                digest.update(new String(((PasswordCallback) callbacks[2]).getPassword()).getBytes(UTF_8));

                ha1 = HexConverter.convertToHexBytes(digest.digest());
            } catch (NoSuchAlgorithmException e) {
                ROOT_LOGGER.debug("Unexpected authentication failure", e);
                return null;
            } finally {
                digest.reset();
            }
        } else {
            org.wildfly.security.credential.PasswordCredential passwordCredential = (org.wildfly.security.credential.PasswordCredential) (((CredentialCallback)callbacks[2]).getCredential());
            DigestPassword digestPassword = passwordCredential.getPassword(DigestPassword.class);
            ha1 = ByteIterator.ofBytes(digestPassword.getDigest()).hexEncode().drainToString().getBytes(StandardCharsets.US_ASCII);
        }

        try {
            if (credential.verifyHA1(ha1)) {
                Principal user = new SimplePrincipal(id);
                Collection<Principal> userCol = Collections.singleton(user);
                SubjectUserInfo supplemental = ach.createSubjectUserInfo(userCol);
                addInetPrincipal(supplemental.getSubject().getPrincipals());

                return new RealmIdentityAccount(supplemental.getSubject(), user);
            }
        } catch (IOException e) {
            ROOT_LOGGER.debug("Unexpected authentication failure", e);
        }

        return null;
    }

    /*
     * The final single method is used for Client Cert style authentication only.
     */

    @Override
    public Account verify(Credential credential) {
        assertMechanism(AuthMechanism.CLIENT_CERT, AuthMechanism.KERBEROS);

        final AuthorizingCallbackHandler ach;
        final Principal user;
        if (credential instanceof X509CertificateCredential) {
            X509CertificateCredential certCred = (X509CertificateCredential) credential;

            ach = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.CLIENT_CERT);
            user = certCred.getCertificate().getSubjectDN();
        } else if (credential instanceof GSSContextCredential) {
            GSSContextCredential gssCred = (GSSContextCredential) credential;
            try {
            user = new KerberosPrincipal(gssCred.getGssContext().getSrcName().toString());
            } catch (GSSException e) {
                // By this point this should not be able to happen.
                ROOT_LOGGER.debug("Unexpected authentication failure", e);
                return null;
            }
            ach = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.KERBEROS);
        } else {
            return null;
        }

        try {
            ach.handle(new Callback[] { new AuthorizeCallback(user.getName(), user.getName()) });
        } catch (IOException e) {
            ROOT_LOGGER.debug("Unexpected authentication failure", e);
            return null;
        } catch (UnsupportedCallbackException e) {
            ROOT_LOGGER.debug("Unexpected authentication failure", e);
            return null;
        }

        Collection<Principal> userCol = Collections.singleton(user);
        SubjectUserInfo supplemental;
        try {
            supplemental = ach.createSubjectUserInfo(userCol);
        } catch (IOException e) {
            return null;
        }
        addInetPrincipal(supplemental.getSubject().getPrincipals());

        return new RealmIdentityAccount(supplemental.getSubject(), user);
    }

    private void addInetPrincipal(final Collection<Principal> principals) {
        InetAddress address = getInetAddress();
        if (address != null) {
            principals.add(new InetAddressPrincipal(address));
        }
    }

    private void assertMechanism(final AuthMechanism... mechanisms) {
        AuthMechanism requested = getRequestMeschanism();
        for (AuthMechanism current : mechanisms) {
            if (requested == current) {
                return;
            }
        }
        // This is impossible, only here for testing if someone messed up a change
        throw new IllegalStateException("Unexpected authentication mechanism executing.");
    }

    private static final class ThreadLocalStore {
        AuthMechanism requestMechanism;
        InetAddress inetAddress;
        SubjectIdentity subjectIdentity;
    }

}
