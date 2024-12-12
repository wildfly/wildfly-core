/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.shared;



import java.io.File;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.security.encryption.CipherUtil;
import org.wildfly.security.encryption.SecretKeyUtil;

/**
 * Utilities for dealing with 'secure' expressions (e.g. Elytron credential store expressions)
 * in tests.
 */
public final class SecureExpressionUtil {

    private static final PathAddress SUBSYSTEM = PathAddress.pathAddress("subsystem", "elytron");
    private static final PathAddress EXPRESSION_RESOLVER = SUBSYSTEM.append("expression", "encryption");

    private static SecretKey expressionKey;
    private static String exportedExpressionKey;

    /**
     * Data class for passing information about secure expressions from/to the caller
     * of the SecurityExpressionUtil utility methods.
     */
    public static class SecureExpressionData {
        private final String clearText;
        private volatile String expression;

        /**
         * Constructs a SecureExpressionData object.
         *
         * @param clearText the clear text value the secure expression should resolve to. Cannot be {@code null}.
         */
        public SecureExpressionData(String clearText) {
            assert clearText != null : "clearText is null";
            this.clearText = clearText;
        }

        /**
         * Gets the value of the secure expression that will resolve to the {@code clearText} parameter passed
         * to the constructor. This value will be set by utility methods that determine the appropriate expression.
         *
         * @return the expression string. Will not return {@code null}, as invoking this method on an object
         *         that has not had its expression value set will result in an {@link IllegalStateException}.
         *
         * @throws IllegalStateException if invoked before a utility method that sets the value has been called.
         */
        public String getExpression() {
            String result = expression;
            if (result == null) {
                throw new IllegalStateException("Expression cannot be read before it has been created");
            }
            return result;
        }
    }

    /**
     * Uses an internal {@link SecretKey} to create credential store expressions for the clear text values
     * in the provided {@link SecureExpressionData} instances, and stores the generated expressions in those instances.
     *
     * @param storeName the name to use for the credential store and expression resolver. Cannot be {@code null}
     * @param toConfigure data objects to use for getting clear text and storing credential store expressions. Cannot be {@code null}
     *
     * @throws Exception if a problem occurs
     *
     * @see #setupCredentialStore(ManagementClient, String, String) to create server resources that can resolve the expressions
     */
    public static void setupCredentialStoreExpressions(String storeName,
                                                       SecureExpressionData... toConfigure) throws Exception {
        // Get or create the key that will be imported into the server credential store
        // and use it to create expressions
        SecretKey secretKey = getExpressionKey();
        for (SecureExpressionData expressionData : toConfigure) {
            expressionData.expression = "${ENC::" + storeName + ":"
                    + CipherUtil.encrypt(expressionData.clearText, secretKey) + "}";
        }
    }

    /**
     * Uses the given management client to create an Elytron subsystem secret-key-credential store resource with the
     * given {@code storeName}, as well as an expression encryption resource with a resolver with that name configured
     * to reference the credential store. Imports the Elytron {@link SecretKey} that this class uses to
     * {@link #setupCredentialStoreExpressions(String, SecureExpressionData...) create credential store expressions}
     * into the credential store so it can use it to resolve those expressions.
     * <p/>
     * This variant calls the variant that takes a {@code PathAddress}, passing {@link PathAddress#EMPTY_ADDRESS}.
     *
     * @param client the client to use to invoke on the server's management layer. Cannot be {@code null}
     * @param storeName the name to use for the credential store and expression resolver. Cannot be {@code null}
     * @param storeLocation absolute path of the file to use to back the credential store. Cannot be {@code null}
     *
     * @throws Exception if a problem occurs
     */
    public static void setupCredentialStore(ManagementClient client, String storeName, String storeLocation) throws Exception {
        setupCredentialStore(client, PathAddress.EMPTY_ADDRESS, storeName, storeLocation);
    }

    /**
     * Uses the given management client to create an Elytron subsystem secret-key-credential store resource with the
     * given {@code storeName}, as well as an expression encryption resource with a resolver with that name configured
     * to reference the credential store. Imports the Elytron {@link SecretKey} that this class uses to
     * {@link #setupCredentialStoreExpressions(String, SecureExpressionData...) create credential store expressions}
     * into the credential store so it can use it to resolve those expressions.
     *
     * @param client the client to use to invoke on the server's management layer. Cannot be {@code null}
     * @param baseAddress address of the Elytron subsystem resource's parent
     * @param storeName the name to use for the credential store and expression resolver. Cannot be {@code null}
     * @param storeLocation absolute path of the file to use to back the credential store. Cannot be {@code null}
     *
     * @throws Exception if a problem occurs
     */
    public static void setupCredentialStore(ManagementClient client, PathAddress baseAddress, String storeName, String storeLocation) throws Exception {

        cleanStore(storeLocation);

        // Add store
        PathAddress storeAddress = baseAddress.append(SUBSYSTEM).append("secret-key-credential-store", storeName);
        ModelNode storeAdd = Util.createAddOperation(storeAddress);
        storeAdd.get("path").set(storeLocation);
        storeAdd.get("populate").set(false);
        client.executeForResult(storeAdd);

        // Import the key we used to create expressions so the store can resolve them
        ModelNode importKey = Util.createEmptyOperation("import-secret-key", storeAddress);
        importKey.get("alias").set(storeName);
        importKey.get("key").set(getExportedEpressionKey());
        client.executeForResult(importKey);

        // Add expression-resolver
        ModelNode exprsAdd = Util.createAddOperation(baseAddress.append(EXPRESSION_RESOLVER));
        ModelNode resolver = new ModelNode();
        resolver.get("name").set(storeName);
        resolver.get("credential-store").set(storeName);
        resolver.get("secret-key").set(storeName);
        exprsAdd.get("resolvers").add(resolver);
        client.executeForResult(exprsAdd);
    }

    /**
     * Removes Elytron subsystem expression encryption and secret-key-credential store resources created by
     * {@link #setupCredentialStore(ManagementClient, String, String)}.
     *
     * @param client the client to use to invoke on the server's management layer. Cannot be {@code null}
     * @param storeName the name to use for the credential store and expression resolver. Cannot be {@code null}
     * @param storeLocation location of the file that backs the credential store. Cannot be {@code null}
     *
     * @throws Exception  if a problem occurs
     */
    public static void teardownCredentialStore(ManagementClient client, String storeName, String storeLocation) throws Exception {
        teardownCredentialStore(client, PathAddress.EMPTY_ADDRESS, storeName, storeLocation,
                () -> {
                    try {
                        ServerReload.reloadIfRequired(client.getControllerClient());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Removes Elytron subsystem expression encryption and secret-key-credential store resources created by
     * {@link #setupCredentialStore(ManagementClient, String, String)}.
     * <p/>
     * This variant calls the variant that takes a {@code PathAddress}, passing {@link PathAddress#EMPTY_ADDRESS}.
     *
     * @param client the client to use to invoke on the server's management layer. Cannot be {@code null}
     * @param baseAddress address of the Elytron subsystem resource's parent
     * @param storeName the name to use for the credential store and expression resolver. Cannot be {@code null}
     * @param storeLocation location of the file that backs the credential store. Cannot be {@code null}
     *
     * @throws Exception  if a problem occurs
     */
    public static void teardownCredentialStore(ManagementClient client, PathAddress baseAddress,
                                               String storeName, String storeLocation,
                                               Runnable reloadFunction) throws Exception {

        UnsuccessfulOperationException toThrow = null;
        try {
            // Remove expression-resolver
            client.executeForResult(Util.createRemoveOperation(baseAddress.append(EXPRESSION_RESOLVER)));
        } catch (UnsuccessfulOperationException uoe) {
            toThrow = uoe;
        } catch (RuntimeException re) {
            toThrow = new UnsuccessfulOperationException(re.toString());
        } finally {
            try {
                // Remove store
                client.executeForResult(Util.createRemoveOperation(baseAddress.append(SUBSYSTEM).append("secret-key-credential-store", storeName)));
            } catch (UnsuccessfulOperationException uoe) {
                if (toThrow == null) {
                    toThrow = uoe;
                }
            } catch (RuntimeException re) {
                if (toThrow == null) {
                    toThrow = new UnsuccessfulOperationException(re.toString());
                }
            }
        }

        if (toThrow != null) {
            throw toThrow;
        }

        if (reloadFunction != null) {
            reloadFunction.run();
        }

        cleanStore(storeLocation);
    }

    private static synchronized SecretKey getExpressionKey() throws GeneralSecurityException {
        if (expressionKey == null) {
            expressionKey = SecretKeyUtil.generateSecretKey(256);
        }
        return expressionKey;
    }

    private static synchronized String getExportedEpressionKey() throws GeneralSecurityException {
        if (exportedExpressionKey == null) {
            exportedExpressionKey = SecretKeyUtil.exportSecretKey(expressionKey);
        }
        return exportedExpressionKey;
    }

    private static void cleanStore(String storeLocation) {

        if (storeLocation != null) {
            File f = new File(storeLocation);
            if (f.exists()) {
                if (!f.delete()) {
                    // TODO log
                }
            }
        }

    }

    private SecureExpressionUtil() {
        // prevent instantiation
    }
}
