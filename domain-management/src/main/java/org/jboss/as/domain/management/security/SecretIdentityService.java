/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import org.jboss.as.domain.management.CallbackHandlerFactory;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import java.io.IOException;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.callback.OptionalNameCallback;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * A simple identity service for an identity represented by a single secret or password.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SecretIdentityService implements Service {

    private static final String SERVICE_SUFFIX = "secret";

    private final String password;
    private final boolean base64;

    private final Consumer<CallbackHandlerFactory> callbackHandlerFactoryConsumer;
    private final ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier;

    public SecretIdentityService(final Consumer<CallbackHandlerFactory> callbackHandlerFactoryConsumer,
                                 final ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier,
                                 final String password, boolean base64) {
        this.callbackHandlerFactoryConsumer = callbackHandlerFactoryConsumer;
        this.credentialSourceSupplier = credentialSourceSupplier;
        this.password = password;
        this.base64 = base64;
    }

    @Override
    public void start(final StartContext startContext) throws StartException {
        final char[] thePassword;
        if (base64) {
            byte[] value = Base64.getDecoder().decode(password);
            String tempPassword = new String(value, StandardCharsets.ISO_8859_1);
            String trimmedPassword = tempPassword.trim();
            if (tempPassword.equals(trimmedPassword) == false) {
                ROOT_LOGGER.whitespaceTrimmed();
            }

            thePassword = trimmedPassword.toCharArray();
        } else {
            thePassword = password.toCharArray();
        }

        callbackHandlerFactoryConsumer.accept((String username) -> new SecretCallbackHandler(username, resolvePassword(thePassword)));
    }

    public void stop(final StopContext stopContext) {
        callbackHandlerFactoryConsumer.accept(null);
    }

    private char[] resolvePassword(char[] thePassword) {
        try {
            ExceptionSupplier<CredentialSource, Exception> sourceSupplier = credentialSourceSupplier;
            if (sourceSupplier == null) {
                return thePassword;
            }
            CredentialSource cs = sourceSupplier.get();
            if (cs == null) {
                return thePassword;
            }
            org.wildfly.security.credential.PasswordCredential credential = cs.getCredential(org.wildfly.security.credential.PasswordCredential.class);
            if (credential == null) {
                return thePassword;
            }
            ClearPassword clearPassword = credential.getPassword(ClearPassword.class);
            if (clearPassword == null) {
                return thePassword;
            }
            return clearPassword.getPassword();
        } catch (Exception ex) {
            return thePassword;
        }
    }
    private class SecretCallbackHandler implements CallbackHandler {

        private final String userName;
        private final char[] password;

        SecretCallbackHandler(final String userName, final char[] password) {
            this.userName = userName;
            this.password = password;
        }


        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    String defaultText = rcb.getDefaultText();
                    rcb.setText(defaultText); // For now just use the realm suggested.
                } else if (current instanceof RealmChoiceCallback) {
                    throw DomainManagementLogger.ROOT_LOGGER.realmNotSupported(current);
                } else if (current instanceof OptionalNameCallback) {
                    // Do nothing as we don't want to set a name for local authentication.
                } else if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName(userName);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
        }
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }

    }
}
