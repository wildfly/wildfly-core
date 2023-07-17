/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package org.wildfly.test.security.common.elytron;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link javax.security.auth.spi.LoginModule} implementation used in the JAAS security realm tests. It uses a static
 * map of username -> password to determine if a login is successful or not.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class TestLoginModule implements LoginModule {

    private final Map<String, char[]> usersMap = new HashMap<>();
    private Subject subject;
    private CallbackHandler handler;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.handler = callbackHandler;
        this.usersMap.put("test", "passwordTest".toCharArray());
    }

    @Override
    public boolean login() throws LoginException {
        // obtain the incoming username and password from the callback handler
        NameCallback nameCallback = new NameCallback("Username");
        PasswordCallback passwordCallback = new PasswordCallback("Password", false);
        Callback[] callbacks = new Callback[]{nameCallback, passwordCallback};
        try {
            this.handler.handle(callbacks);
        } catch(UnsupportedCallbackException | IOException e) {
            throw new LoginException("Error handling callback: " + e.getMessage());
        }

        final String username = nameCallback.getName();
        final char[] password = passwordCallback.getPassword();

        char[] storedPassword = this.usersMap.get(username);
        return password != null && username != null && Arrays.equals(storedPassword, password);
    }

    @Override
    public boolean commit() throws LoginException {
        this.subject.getPrincipals().add(new Roles("Admin"));
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        this.subject.getPrincipals().clear();
        return true;
    }

    private static class Roles implements Principal {

        private final String name;

        Roles(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }
}