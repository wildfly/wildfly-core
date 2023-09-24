/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.http;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * @author Stuart Douglas
 */
public class Authentication {

    public static final String USERNAME = "testSuite";
    public static final String PASSWORD = "testSuitePassword";


    @Deprecated
    public static Authenticator getAuthenticator() {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
            }
        };
    }

    /**
     * @deprecated this could cause tests ran after this is set to fail, use the Apache HttpClient
     */
    @Deprecated
    public static void setupDefaultAuthenticator() {
        Authenticator.setDefault(getAuthenticator());
    }
}
