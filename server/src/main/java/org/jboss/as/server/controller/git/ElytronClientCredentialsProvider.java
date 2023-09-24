/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.git;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.ElytronXmlParser;

/**
 * Credential provider for JGIt using Elytron.
 * Currently only login/password is supported.
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
class ElytronClientCredentialsProvider extends CredentialsProvider {

    private static AuthenticationContextConfigurationClient CLIENT = AccessController.doPrivileged(AuthenticationContextConfigurationClient.ACTION);
    private final AuthenticationContext context;

    ElytronClientCredentialsProvider(URI authenticationConfig) throws ConfigXMLParseException, GeneralSecurityException {
        context =  ElytronXmlParser.parseAuthenticationClientConfiguration(authenticationConfig).create();
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem i : items) {
            if (!(i instanceof CredentialItem.Username) && !(i instanceof CredentialItem.Password)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        try {
            AuthenticationConfiguration config = CLIENT.getAuthenticationConfiguration(new URI(uri.toString()), context);
            for (CredentialItem i : items) {
                if (i instanceof CredentialItem.Username) {
                    NameCallback callback = new NameCallback("git username");
                    Callback[] callbacks = {callback};
                    CLIENT.getCallbackHandler(config).handle(callbacks);
                    ((CredentialItem.Username) i).setValue(callback.getName());
                    continue;
                }
                if (i instanceof CredentialItem.Password) {
                    PasswordCallback callback = new PasswordCallback("git username", false);
                    Callback[] callbacks = {callback};
                    CLIENT.getCallbackHandler(config).handle(callbacks);
                    ((CredentialItem.Password) i).setValue(callback.getPassword());
                    continue;
                }
                if (i instanceof CredentialItem.StringType) {
                    if (i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
                        PasswordCallback callback = new PasswordCallback("git username", false);
                        Callback[] callbacks = {callback};
                        CLIENT.getCallbackHandler(config).handle(callbacks);
                        ((CredentialItem.StringType) i).setValue(new String(callback.getPassword()));
                        continue;
                    }
                }
                throw new UnsupportedCredentialItem(uri, i.getClass().getName()
                        + ":" + i.getPromptText()); //$NON-NLS-1$
            }
        } catch (IOException  | UnsupportedCallbackException | URISyntaxException ex) {
            throw new UnsupportedCredentialItem(uri, ex.getMessage());
        }

        return true;
    }

}
