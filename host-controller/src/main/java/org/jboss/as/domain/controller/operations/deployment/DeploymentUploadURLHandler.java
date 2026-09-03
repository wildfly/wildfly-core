/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.TwoWayPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;

/**
 * Handler for the upload-deployment-url operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentUploadURLHandler
extends AbstractDeploymentUploadHandler {

    public static final String OPERATION_NAME = UPLOAD_DEPLOYMENT_URL;

    private static final AuthenticationContextConfigurationClient AUTH_CONFIG_CLIENT =
            doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    /**
     * Constructor
     *
     * @param repository the master content repository. If {@code null} this handler will function as a slave handler would.
     */
    private DeploymentUploadURLHandler(final ContentRepository repository) {
        super(repository, DeploymentAttributes.URL_NOT_NULL);
    }

    public static void registerMaster(final ManagementResourceRegistration registration, final ContentRepository repository) {
        new DeploymentUploadURLHandler(repository).register(registration);
    }

    public static void registerSlave(final ManagementResourceRegistration registration) {
        new DeploymentUploadURLHandler(null).register(registration);
    }

    private void register(ManagementResourceRegistration registration) {
        registration.registerOperationHandler(DeploymentAttributes.DOMAIN_UPLOAD_URL_DEFINITION, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream getContentInputStream(OperationContext operationContext, ModelNode operation) throws OperationFailedException {

        String urlSpec = operation.get(URL).asString();
        try {
            URL url = new URL(urlSpec);
            ModelNode authCtxNode = DeploymentAttributes.URL_AUTHENTICATION_CONTEXT.resolveModelAttribute(operationContext, operation);
            if (authCtxNode.isDefined()) {
                String authCtxName = authCtxNode.asString();
                AuthenticationContext authCtx = operationContext.getCapabilityRuntimeAPI(
                        "org.wildfly.security.authentication-context", authCtxName, AuthenticationContext.class);
                return openAuthenticatedStream(url, authCtx);
            }
            return url.openStream();
        } catch (MalformedURLException e) {
            throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.invalidUrl(urlSpec, e.toString()));
        } catch (IOException e) {
            throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.errorObtainingUrlStream(urlSpec, e.toString()));
        }
    }

    static InputStream openAuthenticatedStream(URL url, AuthenticationContext authCtx) throws IOException {
        try {
            URI uri = url.toURI();
            AuthenticationConfiguration authConfig = AUTH_CONFIG_CLIENT.getAuthenticationConfiguration(uri, authCtx, -1, null, null);
            URLConnection connection = url.openConnection();

            // Apply TLS configuration for HTTPS connections
            if (connection instanceof HttpsURLConnection) {
                try {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(
                            AUTH_CONFIG_CLIENT.getSSLContext(uri, authCtx).getSocketFactory());
                } catch (GeneralSecurityException e) {
                    throw new IOException(e);
                }
            }

            // Extract credentials and set Authorization header for HTTP(S) connections
            if (connection instanceof HttpURLConnection) {
                String credentials = extractCredentials(authConfig);
                if (credentials != null) {
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    connection.setRequestProperty("Authorization", "Basic " + encoded);
                }
            }

            return connection.getInputStream();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /**
     * Extracts "username:password" credentials from an {@link AuthenticationConfiguration}, or {@code null} if
     * credentials cannot be obtained.
     */
    private static String extractCredentials(AuthenticationConfiguration authConfig) {
        CallbackHandler callbackHandler = AUTH_CONFIG_CLIENT.getCallbackHandler(authConfig);
        NameCallback nameCallback = new NameCallback("Username");
        CredentialCallback credentialCallback = new CredentialCallback(PasswordCredential.class);
        char[] password = null;
        try {
            callbackHandler.handle(new Callback[] { nameCallback, credentialCallback });
            TwoWayPassword twoWayPassword = credentialCallback.applyToCredential(PasswordCredential.class,
                    c -> c.getPassword().castAs(TwoWayPassword.class));
            if (twoWayPassword != null) {
                PasswordFactory factory = PasswordFactory.getInstance(twoWayPassword.getAlgorithm(),
                        AUTH_CONFIG_CLIENT.getProviderSupplier(authConfig));
                password = factory.getKeySpec(factory.translate(twoWayPassword), ClearPasswordSpec.class).getEncodedPassword();
            }
        } catch (UnsupportedCallbackException e) {
            if (e.getCallback() == credentialCallback) {
                // Fall back to a plain PasswordCallback
                PasswordCallback passwordCallback = new PasswordCallback("Password", false);
                try {
                    callbackHandler.handle(new Callback[] { nameCallback, passwordCallback });
                    password = passwordCallback.getPassword();
                } catch (IOException | UnsupportedCallbackException ex) {
                    DomainControllerLogger.ROOT_LOGGER.tracef(ex, "Unable to obtain password for deployment URL authentication");
                }
            }
        } catch (Exception e) {
            DomainControllerLogger.ROOT_LOGGER.tracef(e, "Unable to obtain credentials for deployment URL authentication");
        }
        String name = nameCallback.getName();
        if (name == null || password == null) return null;
        return name + ":" + new String(password);
    }

}
