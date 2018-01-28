/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Utility class to interact with Elytron subsystem.
 *
 * @author jdenise@redhat.com
 */
public abstract class ElytronUtil {

    public static final String JKS = "JKS";
    public static final String PKCS12 = "PKCS12";
    public static final String TLS_V1_2 = "TLSv1.2";

    private ElytronUtil() {
    }

    static String retrieveKeyStorePassword(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.addProperty(Util.NAME, Util.CREDENTIAL_REFERENCE);
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        String password = null;
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                if (res.hasDefined(Util.CLEAR_TEXT)) {
                    password = res.get(Util.CLEAR_TEXT).asString();
                }
            }
        }
        return password;
    }

    static String findMatchingKeyStore(CommandContext ctx, File path, String relativeTo, String password, String type, Boolean required, String alias) throws OperationFormatException, IOException {
        ModelNode resource = buildKeyStoreResource(path, relativeTo, password, type, required, alias);
        List<String> names = findMatchingResources(ctx, Util.KEY_STORE, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    static List<String> findMatchingKeyStores(CommandContext ctx, File path, String relativeTo) throws OperationFormatException, IOException {
        ModelNode resource = buildKeyStoreResource(path, relativeTo, null, null, null, null);
        return findMatchingResources(ctx, Util.KEY_STORE, resource);
    }

    private static ModelNode buildKeyStoreResource(File path, String relativeTo,
            String password, String type, Boolean required, String alias) throws IOException {
        ModelNode localKS = new ModelNode();
        if (path != null) {
            localKS.get(Util.PATH).set(path.getPath());
        }
        if (relativeTo != null) {
            localKS.get(Util.RELATIVE_TO).set(relativeTo);
        } else {
            localKS.get(Util.RELATIVE_TO);
        }
        if (password != null) {
            localKS.get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(password));
        }
        if (type != null) {
            localKS.get(Util.TYPE).set(type);
        }
        if (required != null) {
            localKS.get(Util.REQUIRED).set(required);
        }
        if (alias != null) {
            localKS.get(Util.ALIAS_FILTER, alias);
        } else {
            localKS.get(Util.ALIAS_FILTER);
        }
        return localKS;
    }

    private static ModelNode buildKeyManagerResource(KeyStore keyStore, String alias, String algorithm) {
        ModelNode localKM = new ModelNode();
        if (keyStore.getPassword() != null) {
            localKM.get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(keyStore.getPassword()));
        }
        localKM.get(Util.KEY_STORE).set(keyStore.getName());
        if (alias != null) {
            localKM.get(Util.ALIAS_FILTER, alias);
        } else {
            localKM.get(Util.ALIAS_FILTER);
        }
        if (algorithm != null) {
            localKM.get(Util.ALGORITHM, algorithm);
        } else {
            localKM.get(Util.ALGORITHM);
        }
        return localKM;
    }

    private static ModelNode buildTrustManagerResource(KeyStore keyStore, String alias, String algorithm) {
        // No password in trust-store
        ModelNode mn = buildKeyManagerResource(keyStore, alias, algorithm);
        mn.remove(Util.CREDENTIAL_REFERENCE);
        return mn;
    }

    private static List<String> findMatchingResources(CommandContext ctx, String type,
            ModelNode resource) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, type);
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        List<String> matches = new ArrayList<>();
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                for (String ksName : res.keys()) {
                    ModelNode ks = res.get(ksName);
                    List<String> meaninglessKeys = new ArrayList<>();
                    for (String k : ks.keys()) {
                        if (!resource.keys().contains(k)) {
                            meaninglessKeys.add(k);
                        }
                    }
                    for (String s : meaninglessKeys) {
                        ks.remove(s);
                    }
                    if (resource.equals(ks)) {
                        matches.add(ksName);
                    }
                }
            }
        }
        return matches;
    }

    static String findMatchingKeyManager(CommandContext ctx, KeyStore keyStore,
            String alias, String algorithm) throws OperationFormatException, IOException {
        ModelNode resource = buildKeyManagerResource(keyStore, alias, algorithm);
        List<String> names = findMatchingResources(ctx, Util.KEY_MANAGER, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    static String findMatchingTrustManager(CommandContext ctx, KeyStore keyStore,
            String alias, String algorithm) throws OperationFormatException, IOException {
        ModelNode resource = buildTrustManagerResource(keyStore, alias, algorithm);
        List<String> names = findMatchingResources(ctx, Util.TRUST_MANAGER, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    static String findMatchingSSLContext(CommandContext ctx, ServerSSLContext sslCtx) throws OperationFormatException, IOException {
        List<String> names = findMatchingResources(ctx, Util.SERVER_SSL_CONTEXT, sslCtx.buildResource());
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    static ModelNode addKeyStore(CommandContext ctx, String name, File path,
            String relativeTo, String password, String type, Boolean required, String alias) throws Exception {
        ModelNode mn = buildKeyStoreResource(path, relativeTo, password, type, required, alias);
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    static ModelNode generateKeyPair(CommandContext ctx, String name, String dn, String alias, Long validity, String keyAlg, int keySize) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.GENERATE_KEY_PAIR);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.getModelNode().get(Util.DISTINGUISHED_NAME).set(dn);
        builder.getModelNode().get(Util.ALGORITHM).set(keyAlg);
        builder.getModelNode().get(Util.KEY_SIZE).set(keySize);
        builder.getModelNode().get(Util.ALIAS).set(alias);
        if (validity != null) {
            builder.getModelNode().get(Util.VALIDITY).set(validity);
        }
        return builder.buildRequest();
    }

    static ModelNode importCertificate(CommandContext ctx, File trustedCertificate,
            String alias, boolean validate, KeyStore trustStore, boolean trust) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.IMPORT_CERTIFICATE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, trustStore.getName());
        builder.getModelNode().get(Util.ALIAS).set(alias);
        builder.getModelNode().get(Util.PATH).set(trustedCertificate.getAbsolutePath());
        builder.getModelNode().get(Util.TRUST_CACERTS).set(trust);
        builder.getModelNode().get(Util.VALIDATE).set(validate);
        return builder.buildRequest();
    }

    static ModelNode storeKeyStore(CommandContext ctx, String name) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.STORE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        return builder.buildRequest();
    }

    static ModelNode removeKeyStore(CommandContext ctx, String name) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.REMOVE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        return builder.buildRequest();
    }

    static ModelNode exportCertificate(CommandContext ctx, String name, File path, String relativeTo, String alias, boolean pem) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.EXPORT_CERTIFICATE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.getModelNode().get(Util.PATH).set(path.getPath());
        builder.getModelNode().get(Util.ALIAS).set(alias);
        if (relativeTo != null) {
            builder.getModelNode().get(Util.RELATIVE_TO).set(relativeTo);
        }
        builder.getModelNode().get(Util.PEM).set(pem);
        return builder.buildRequest();
    }

    static ModelNode generateSigningRequest(CommandContext ctx, String name,
            File path, String relativeTo, String alias) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.GENERATE_CERTIFICATE_SIGNING_REQUEST);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        builder.getModelNode().get(Util.PATH).set(path.getPath());
        builder.getModelNode().get(Util.ALIAS).set(alias);
        if (relativeTo != null) {
            builder.getModelNode().get(Util.RELATIVE_TO).set(relativeTo);
        }
        return builder.buildRequest();
    }

    static ModelNode addKeyManager(CommandContext ctx, KeyStore keyStore, String name, String alias, String algorithm) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_MANAGER, name);
        ModelNode mn = buildKeyManagerResource(keyStore, alias, algorithm);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        builder.getModelNode().get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(keyStore.getPassword()));
        return builder.buildRequest();
    }

    static ModelNode addTrustManager(CommandContext ctx, KeyStore keyStore, String name, String alias, String algorithm) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.TRUST_MANAGER, name);
        ModelNode mn = buildKeyManagerResource(keyStore, alias, algorithm);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        if (keyStore.getPassword() != null) {
            builder.getModelNode().get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(keyStore.getPassword()));
        }
        return builder.buildRequest();
    }

    static ModelNode addServerSSLContext(CommandContext ctx, ServerSSLContext sslCtx, String name) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SERVER_SSL_CONTEXT, name);
        ModelNode mn = sslCtx.buildResource();
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    private static ModelNode buildCredentialReferences(String password) {
        ModelNode mn = new ModelNode();
        mn.get(Util.CLEAR_TEXT).set(password);
        return mn;
    }

    static boolean keyManagerExists(CommandContext ctx, String name) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_MANAGER, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean trustManagerExists(CommandContext ctx, String name) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.TRUST_MANAGER, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean keyStoreExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static boolean serverSSLContextExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SERVER_SSL_CONTEXT, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static List<String> getKeyStoreNames(ModelControllerClient client) {
        return getNames(client, Util.KEY_STORE);
    }

    private static List<String> getNames(ModelControllerClient client, String type) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addProperty(Util.CHILD_TYPE, type);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (Util.isSuccess(outcome)) {
                return Util.getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    private static ModelNode getChildResource(String name, String type, CommandContext ctx) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(type, name);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                return outcome.get(Util.RESULT);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static ServerSSLContext getServerSSLContext(CommandContext context, String sslContextName) {
        ModelNode sslContext = getChildResource(sslContextName, Util.SERVER_SSL_CONTEXT, context);
        ServerSSLContext ctx = null;
        if (sslContext != null) {
            String kmName = sslContext.get(Util.KEY_MANAGER).asString();
            ModelNode km = getChildResource(kmName, Util.KEY_MANAGER, context);
            String ksName = km.get(Util.KEY_STORE).asString();
            KeyStore keyStore = new KeyStore(ksName, null, true);
            KeyManager keyManager = new KeyManager(kmName, keyStore, true);
            KeyManager trustManager = null;
            if (sslContext.hasDefined(Util.TRUST_MANAGER)) {
                trustManager = new KeyManager(sslContext.get(Util.TRUST_MANAGER).asString(), keyStore, true);
            }
            ctx = new ServerSSLContext(ksName, keyManager, trustManager, true);
        }
        return ctx;
    }

    public static KeyStore getKeyStore(CommandContext ctx, String name) throws OperationFormatException, IOException {
        String password;
        password = ElytronUtil.retrieveKeyStorePassword(ctx, name);

        if (password == null) {
            // Can be null for trust-store, inconcistency in API.
            //throw new OperationFormatException("Can't retrieve password for " + name);
        }
        return new KeyStore(name, password, true);
    }

    public static boolean isElytronSupported(CommandContext commandContext) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        ModelNode response = commandContext.getModelControllerClient().execute(builder.buildRequest());
        return Util.isSuccess(response);
    }

    public static boolean isKeyStoreManagementSupported(CommandContext commandContext) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_OPERATION_DESCRIPTION);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, "?");
        builder.addProperty(Util.NAME, Util.GENERATE_KEY_PAIR);
        ModelNode response = commandContext.getModelControllerClient().execute(builder.buildRequest());
        return Util.isSuccess(response);
    }

}
