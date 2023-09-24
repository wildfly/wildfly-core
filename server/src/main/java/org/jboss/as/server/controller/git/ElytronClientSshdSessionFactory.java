/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.git;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.transport.sshd.CachingKeyPairProvider;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.IdentityPasswordProvider;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSession;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;
import org.jboss.as.server.logging.ServerLogger;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.ElytronXmlParser;
import org.wildfly.security.credential.KeyPairCredential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.SSHCredential;
import org.wildfly.security.password.interfaces.ClearPassword;


/**
 * SshdSessionFactory for JGIt using Elytron.
 * Supports specifying ssh-keys as {@link KeyPairCredential} or {@link SSHCredential}
 *
 * @author <a href="mailto:aabdelsa@redhat.com">Ashley Abdel-Sayed</a>
 */
class ElytronClientSshdSessionFactory extends SshdSessionFactory {

    private static AuthenticationContextConfigurationClient CLIENT = AccessController.doPrivileged(AuthenticationContextConfigurationClient.ACTION);
    private final AuthenticationContext context;

    private final Map<Tuple, Iterable<KeyPair>> defaultKeys;
    private String[] defaultIdentities;
    private KeyPair keyPair;
    private URI uri;
    private String knownHostsFile;

    ElytronClientSshdSessionFactory(URI authenticationConfig) throws ConfigXMLParseException, GeneralSecurityException {
        if (authenticationConfig != null) {
            context = ElytronXmlParser.parseAuthenticationClientConfiguration(authenticationConfig).create();
        } else {
            context = null;
        }

        this.defaultKeys = new ConcurrentHashMap<>();
        this.setSshDirectory(SSHCredential.DEFAULT_SSH_DIRECTORY);
        this.defaultIdentities = SSHCredential.DEFAULT_PRIVATE_KEYS;
        this.keyPair = null;
        this.knownHostsFile = SSHCredential.DEFAULT_KNOWN_HOSTS;
    }

    @Override
    @NonNull
    protected List<Path> getDefaultIdentities(@NonNull File sshDir) {
        return Stream.of(defaultIdentities)
                .map(s -> sshDir.toPath().resolve(s))
                .filter(path -> Files.exists(path))
                .collect(Collectors.toList());
    }

    @Override
    public SshdSession getSession(URIish urIish, CredentialsProvider credentialsProvider, FS fs, int tms) throws TransportException {
        if (context == null) return super.getSession(urIish, credentialsProvider, fs, tms);
        try {
            this.uri = urIishToUri(urIish);
            AuthenticationConfiguration config = CLIENT.getAuthenticationConfiguration(this.uri, context);
            CredentialCallback locationCallback = new CredentialCallback(SSHCredential.class, null);
            CredentialCallback keyPairCallback = new CredentialCallback(KeyPairCredential.class, null);
            Callback[] callbacks = {locationCallback, keyPairCallback};
            CLIENT.getCallbackHandler(config).handle(callbacks);

            if (locationCallback.getCredential() != null) {
                SSHCredential sshCredential = locationCallback.getCredential().castAs(SSHCredential.class);
                this.setSshDirectory(sshCredential.getSshDirectory());
                this.defaultIdentities = sshCredential.getPrivateKeyIdentities();
                this.knownHostsFile = sshCredential.getKnownHostsFile();
            }

            if (keyPairCallback.getCredential() != null) {
                KeyPairCredential keyPairCredential = keyPairCallback.getCredential().castAs(KeyPairCredential.class);
                this.keyPair = keyPairCredential.getKeyPair();
            }

        } catch (IOException  | UnsupportedCallbackException | URISyntaxException ex) {
            throw ServerLogger.ROOT_LOGGER.failedToLoadSSHCredentials(ex, ex.getMessage());
        }
        return super.getSession(urIish, credentialsProvider, fs, tms);
    }

    @Override
    protected KeyPasswordProvider createKeyPasswordProvider(CredentialsProvider provider) {
        if (context == null) return super.createKeyPasswordProvider(provider);
        URI uri = this.uri;
        return new IdentityPasswordProvider(new CredentialsProvider() {
           @Override
           public boolean isInteractive() {
               return false;
           }

           @Override
           public boolean supports(CredentialItem... credentialItems) {
               for (CredentialItem i : credentialItems) {
                   if (!(i instanceof CredentialItem.Password)) {
                       return false;
                   }
               }
               return true;
           }

           @Override
           public boolean get(URIish urIish, CredentialItem... credentialItems) throws UnsupportedCredentialItem {
               try {
                   AuthenticationConfiguration config = CLIENT.getAuthenticationConfiguration(uri, context);

                   for (CredentialItem i : credentialItems) {
                       if (i instanceof CredentialItem.Password) {
                           //Only SSHCredential will require a passphrase to decrypt the private key, a KeyPairCredential already contains the decrypted private key
                           CredentialCallback callback = new CredentialCallback(SSHCredential.class, null);
                           Callback[] callbacks = {callback};
                           CLIENT.getCallbackHandler(config).handle(callbacks);
                           SSHCredential credential = null;
                           if (callback != null && callback.getCredential() != null) {
                               credential = callback.getCredential().castAs(SSHCredential.class);
                               char[] password = credential.getPassphrase().castAndApply(PasswordCredential.class, c -> c.getPassword().castAndApply(ClearPassword.class, ClearPassword::getPassword));
                               ((CredentialItem.Password) i).setValue(password);
                           }
                           continue;
                       }
                   }
               } catch (IOException  | UnsupportedCallbackException ex) {
                   throw new UnsupportedCredentialItem(urIish, ex.getMessage());
               }

               return true;
           }
       });
    }

    @Override
    @NonNull
    protected List<Path> getDefaultKnownHostsFiles(@NonNull File sshDir) {
        return Collections.singletonList(sshDir.toPath().resolve(this.knownHostsFile));
    }

    @Override
    @NonNull
    protected Iterable<KeyPair> getDefaultKeys(@NonNull File sshDir) {
        if(keyPair == null) {
            List<Path> defaultIdentities = this.getDefaultIdentities(sshDir);
            return this.defaultKeys.computeIfAbsent(new Tuple(defaultIdentities.toArray(new Path[0])), (t) ->
                    new CachingKeyPairProvider(defaultIdentities, this.getKeyCache()));
        } else {
            return Collections.singletonList(keyPair);
        }
    }

    //Copied from org.eclipse.jgit.transport.sshd.SshdSessionFactory
    private static final class Tuple {
        private final Object[] objects;

        public Tuple(Object[] objects) {
            this.objects = objects;
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj != null && obj.getClass() == Tuple.class) {
                Tuple other = (Tuple)obj;
                return Arrays.equals(this.objects, other.objects);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Arrays.hashCode(this.objects);
        }
    }

    private URI urIishToUri(URIish urIish) throws URISyntaxException {
        String scheme = "ssh";
        String user = urIish.getUser();
        String host = urIish.getHost();
        int port = urIish.getPort();
        String path = null;
        //Github SSH urls use the "git" in place of username and a colon to separate the path from the host rather than a backslash
        // (ex. "git@github.com:wildfly/wildfly-config.git") so we must add a backslash to the beginning of the path
        if (user.equals("git")) {
            path = "/" + urIish.getPath();
        } else {
            path = urIish.getPath();
        }
        return new URI(scheme, user, host, port, path, null, null);
    }
}
