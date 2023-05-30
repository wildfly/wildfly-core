/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.security.sasl;

import static org.jboss.as.server.logging.ServerLogger.AS_ROOT_LOGGER;
import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.jboss.as.server.security.DomainServerCredential;
import org.jboss.as.server.security.DomainServerEvidence;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.security.auth.callback.CachedIdentityAuthorizeCallback;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.cache.CachedIdentity;
import org.wildfly.security.cache.IdentityCache;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.sasl.util.SaslWrapper;

/**
 * The sasl server implementation for the JBOSS-DOMAIN-SERVER SASL mechanism.
 *
 * This implementation borrows heavily from the Plain mechanism implementation in
 * the WildFly Elytron project.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class DomainServerSaslServer implements SaslServer, SaslWrapper {

    private final SecurityDomain securityDomain;
    private final Predicate<Evidence> evidenceVerifier;

    private final CallbackHandler callbackHandler;
    private boolean complete;
    private String authorizedId;

    /**
     * Construct a new instance.
     *
     * @param securityDomain the security domain we will create the identity from
     * @param evidenceVerifier the direct evidence verifier we will use
     * @param callbackHandler the callback handler
     * @param permissionVerifier the permission verifier to be associated with any identity
     */
    public DomainServerSaslServer(final SecurityDomain securityDomain, final Predicate<Evidence> evidenceVerifier,
            final CallbackHandler callbackHandler) {
        this.securityDomain = securityDomain;
        this.evidenceVerifier = evidenceVerifier;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public String getAuthorizationID() {
        if (! isComplete()) {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
        return authorizedId;
    }

    @Override
    public String getMechanismName() {
        return JBOSS_DOMAIN_SERVER;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        if (complete) {
            throw AS_ROOT_LOGGER.mechMessageAfterComplete().toSaslException();
        }
        complete = true;
        if (response.length >= 65536) {
            throw AS_ROOT_LOGGER.mechMessageTooLong().toSaslException();
        }
        CodePointIterator i = CodePointIterator.ofUtf8Bytes(response);
        String loginName;
        String token;
        try {
            final CodePointIterator delimIter = i.delimitedBy(0);
            loginName = delimIter.drainToString();
            i.next(); // skip delimiter
            token = delimIter.drainToString();
        } catch (NoSuchElementException ignored) {
            throw AS_ROOT_LOGGER.mechInvalidMessageReceived().toSaslException();
        }

        // The message has now been parsed, split and converted to UTF-8 Strings
        // now it is time to use the evidenceVerifier to validate the supplied credentials.

        NamePrincipal namePrincipal = new NamePrincipal(loginName);
        DomainServerEvidence evidence = new DomainServerEvidence(namePrincipal, token);

        if (evidenceVerifier.test(evidence) == false) {
            throw AS_ROOT_LOGGER.mechTokenNotVerified().toSaslException();
        }

        // Create our fully populated ad-hoc identity.
        SecurityIdentity identity = securityDomain.createAdHocIdentity(namePrincipal);
        identity = identity.withPrivateCredential(new DomainServerCredential(token));
        identity = identity.withDefaultRoleMapper(RoleMapper.constant(Roles.of(JBOSS_DOMAIN_SERVER)));

        CachedIdentity cachedIdentity = new CachedIdentity(JBOSS_DOMAIN_SERVER, false, identity);

        // Now check the authorization id
        CachedIdentityAuthorizeCallback ciac = new CachedIdentityAuthorizeCallback(new IdentityCache() {

            @Override
            public CachedIdentity remove() {
                return cachedIdentity;
            }

            @Override
            public void put(SecurityIdentity identity) {}

            @Override
            public CachedIdentity get() {
                return cachedIdentity;
            }
        });

        try {
            callbackHandler.handle(new Callback[] { ciac });
        } catch (SaslException e) {
            throw e;
        } catch (IOException | UnsupportedCallbackException e) {
            throw AS_ROOT_LOGGER.mechServerSideAuthenticationFailed(e).toSaslException();
        }

        if (ciac.isAuthorized() == true) {
            authorizedId = namePrincipal.getName();
        } else {
            throw AS_ROOT_LOGGER.mechAuthorizationFailed(loginName, namePrincipal.getName()).toSaslException();
        }
        return null;
    }

    @Override
    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        if (complete) {
            throw AS_ROOT_LOGGER.mechNoSecurityLayer();
        } else {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
    }

    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        if (complete) {
            throw AS_ROOT_LOGGER.mechNoSecurityLayer();
        } else {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
    }

    @Override
    public Object getNegotiatedProperty(final String propName) {
        if (! complete) {
            throw AS_ROOT_LOGGER.mechAuthenticationNotComplete();
        }
        return null;
    }

    @Override
    public void dispose() throws SaslException {
    }
}
