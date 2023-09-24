/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.util;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

/**
 * Factory to supply a CallbackHandler for use during tests.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class Authentication {

    public static CallbackHandler getCallbackHandler() {
        return new CallbackHandlerImpl("", "", null);
    }

    public static CallbackHandler getCallbackHandler(String username, String password, String realm) {
        return new CallbackHandlerImpl(username, password, realm);
    }

    private static class CallbackHandlerImpl implements javax.security.auth.callback.CallbackHandler {

        private final String username;
        private final String password;
        private final String realm;

        private CallbackHandlerImpl(String username, String password, String realm) {
            this.username = username;
            this.password = password;
            this.realm = realm;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName(username);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password.toCharArray());
                } else if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    rcb.setText(realm != null ? realm : rcb.getDefaultText());
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
        }
    }
}
