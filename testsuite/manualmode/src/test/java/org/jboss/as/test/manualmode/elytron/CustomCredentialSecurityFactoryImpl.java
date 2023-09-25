/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.elytron;

import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;

import org.wildfly.security.SecurityFactory;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;

public class CustomCredentialSecurityFactoryImpl implements SecurityFactory<Credential> {

    boolean throwException = false;

    @Override
    public Credential create() throws GeneralSecurityException {
        if (throwException) {
            throw new RuntimeException("This exception is expected");
        }

        final PasswordFactory passwordFactory;
        final PasswordSpec passwordSpec;

        passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
        passwordSpec = new ClearPasswordSpec("password".toCharArray());

        try {
            return new PasswordCredential(passwordFactory.generatePassword(passwordSpec));
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    public void initialize(Map<String, String> configuration) {
        throwException = Boolean.parseBoolean(configuration.get("throwException"));
    }

    private static PasswordFactory getPasswordFactory(final String algorithm) {
        try {
            return PasswordFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
