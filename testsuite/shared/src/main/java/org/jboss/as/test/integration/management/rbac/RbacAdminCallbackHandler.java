/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.rbac;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

/**
* {@link CallbackHandler} for use in RBAC testing.
*
* @author Brian Stansberry (c) 2013 Red Hat Inc.
*/
public class RbacAdminCallbackHandler implements CallbackHandler {

    /** The standard password used for test user accounts */
    public static final String STD_PASSWORD = "t3stSu!tePassword";

    private final String userName;
    private final String password;

    public RbacAdminCallbackHandler(String userName) {
        this(userName, STD_PASSWORD);
    }

    public RbacAdminCallbackHandler(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback current : callbacks) {
            if (current instanceof NameCallback) {
                NameCallback ncb = (NameCallback) current;
                ncb.setName(userName);
                System.out.println("set user " + userName);
            } else if (current instanceof PasswordCallback) {
                PasswordCallback pcb = (PasswordCallback) current;
                pcb.setPassword(password.toCharArray());
                System.out.println("set password " + password);
            } else if (current instanceof RealmCallback) {
                RealmCallback rcb = (RealmCallback) current;
                rcb.setText(rcb.getDefaultText());
            } else {
                throw new UnsupportedCallbackException(current);
            }
        }
    }
}
