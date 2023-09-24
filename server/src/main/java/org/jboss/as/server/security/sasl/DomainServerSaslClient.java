/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security.sasl;

import static org.jboss.as.server.logging.ServerLogger.AS_ROOT_LOGGER;
import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.jboss.as.server.security.DomainServerCredential;
import org.wildfly.common.bytes.ByteStringBuilder;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.sasl.util.SaslWrapper;
import org.wildfly.security.sasl.util.StringPrep;

/**
 * The client mechanism for JBOSS-DOMAIN-SERVER.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class DomainServerSaslClient implements SaslClient, SaslWrapper {

    private final CallbackHandler cbh;
    private boolean complete = false;

    DomainServerSaslClient(final CallbackHandler cbh) {
        this.cbh = cbh;
    }

    public String getMechanismName() {
        return JBOSS_DOMAIN_SERVER;
    }

    public boolean hasInitialResponse() {
        return true;
    }

    public byte[] evaluateChallenge(final byte[] challenge) throws SaslException {
        if (complete) {
            throw AS_ROOT_LOGGER.mechMessageAfterComplete().toSaslException();
        }
        complete = true;
        if (challenge.length > 0) {
            throw AS_ROOT_LOGGER.mechInvalidMessageReceived().toSaslException();
        }
        final NameCallback nameCallback = new NameCallback("Login name");
        final CredentialCallback credentialCallback = new CredentialCallback(DomainServerCredential.class);
        try {
            cbh.handle(new Callback[] { nameCallback, credentialCallback });
        } catch (SaslException e) {
            throw e;
        } catch (IOException | UnsupportedCallbackException e) {
            throw AS_ROOT_LOGGER.mechCallbackHandlerFailedForUnknownReason(e).toSaslException();
        }
        final String name = nameCallback.getName();
        if (name == null) {
            throw AS_ROOT_LOGGER.mechNoLoginNameGiven().toSaslException();
        }
        DomainServerCredential credential = credentialCallback.getCredential(DomainServerCredential.class);
        final String token = credential != null ? credential.getToken() : null;
        if (token == null) {
            throw AS_ROOT_LOGGER.mechNoTokenGiven().toSaslException();
        }
        try {
            final ByteStringBuilder b = new ByteStringBuilder();
            StringPrep.encode(name, b, StringPrep.PROFILE_SASL_STORED);
            b.append((byte) 0);
            StringPrep.encode(token, b, StringPrep.PROFILE_SASL_STORED);
            AS_ROOT_LOGGER.tracef("SASL Negotiation Completed");
            return b.toArray();
        } catch (IllegalArgumentException ex) {
            throw AS_ROOT_LOGGER.mechMalformedFields(ex).toSaslException();
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        if (complete) {
            throw AS_ROOT_LOGGER.mechNoSecurityLayer();
        } else {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
    }

    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        if (complete) {
            throw AS_ROOT_LOGGER.mechNoSecurityLayer();
        } else {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
    }

    public Object getNegotiatedProperty(final String propName) {
        if (complete) {
            return null;
        } else {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
    }

    public void dispose() throws SaslException {
    }
}
