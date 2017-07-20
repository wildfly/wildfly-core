/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.elytron;

import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;

import org.wildfly.extension.elytron.Configurable;
import org.wildfly.extension.elytron.capabilities.CredentialSecurityFactory;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;

public class CustomCredentialSecurityFactoryImpl implements CredentialSecurityFactory, Configurable {

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

    @Override
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
