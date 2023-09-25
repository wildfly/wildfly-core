/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

/**
 * Utility class to interact with Elytron subsystem.
 *
 * @author jdenise@redhat.com
 */
public abstract class ElytronUtil {

    private static final String PLAIN_MECHANISM = "PLAIN";
    private static final String DIGEST_MD5_MECHANISM = "DIGEST-MD5";
    private static final String EXTERNAL_MECHANISM = "EXTERNAL";
    private static final String JBOSS_LOCAL_USER_MECHANISM = "JBOSS-LOCAL-USER";

    private static final String BASIC_MECHANISM = "BASIC";
    private static final String DIGEST_MECHANISM = "DIGEST";
    private static final String FORM_MECHANISM = "FORM";
    private static final String CLIENT_CERT_MECHANISM = "CLIENT_CERT";

    private static final String SCRAM_SHA_1 = "SCRAM-SHA-1";
    private static final String SCRAM_SHA_1_PLUS = "SCRAM-SHA-1-PLUS";
    private static final String SCRAM_SHA_256 = "SCRAM-SHA-256";
    private static final String SCRAM_SHA_256_PLUS = "SCRAM-SHA-256-PLUS";
    private static final String SCRAM_SHA_384 = "SCRAM-SHA-384";
    private static final String SCRAM_SHA_384_PLUS = "SCRAM-SHA-384-PLUS";
    private static final String SCRAM_SHA_512 = "SCRAM-SHA-512";
    private static final String SCRAM_SHA_512_PLUS = "SCRAM-SHA-512-PLUS";

    private static final String DIGEST_SHA = "DIGEST-SHA";
    private static final String DIGEST_SHA_256 = "DIGEST-SHA-256";
    private static final String DIGEST_SHA_384 = "DIGEST-SHA-384";
    private static final String DIGEST_SHA_512 = "DIGEST-SHA-512";

    public static final String JKS = "JKS";
    public static final String PKCS12 = "PKCS12";
    public static final String TLS_V1_2 = "TLSv1.2";

    public static String OOTB_MANAGEMENT_SASL_FACTORY = "management-sasl-authentication";
    public static String OOTB_MANAGEMENT_HTTP_FACTORY = "management-http-authentication";
    public static String OOTB_APPLICATION_HTTP_FACTORY = "application-http-authentication";
    public static String OOTB_APPLICATION_DOMAIN = "ApplicationDomain";

    public static final String SASL_SERVER_CAPABILITY = "org.wildfly.security.sasl-server-factory";
    public static final String HTTP_SERVER_CAPABILITY = "org.wildfly.security.http-server-mechanism-factory";

    private static final Set<String> MECHANISMS_WITH_REALM = new HashSet<>();

    private static final Set<String> MECHANISMS_WITH_TRUST_STORE = new HashSet<>();

    private static final Set<String> MECHANISMS_LOCAL_USER = new HashSet<>();

    static {
        MECHANISMS_WITH_REALM.add(PLAIN_MECHANISM);
        MECHANISMS_WITH_REALM.add(DIGEST_MD5_MECHANISM);
        MECHANISMS_WITH_REALM.add(DIGEST_MECHANISM);
        MECHANISMS_WITH_REALM.add(FORM_MECHANISM);
        MECHANISMS_WITH_REALM.add(BASIC_MECHANISM);

        MECHANISMS_WITH_REALM.add(SCRAM_SHA_1);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_1_PLUS);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_256);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_256_PLUS);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_384);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_384_PLUS);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_512);
        MECHANISMS_WITH_REALM.add(SCRAM_SHA_512_PLUS);

        MECHANISMS_WITH_REALM.add(DIGEST_SHA);
        MECHANISMS_WITH_REALM.add(DIGEST_SHA_256);
        MECHANISMS_WITH_REALM.add(DIGEST_SHA_384);
        MECHANISMS_WITH_REALM.add(DIGEST_SHA_512);

        MECHANISMS_WITH_TRUST_STORE.add(EXTERNAL_MECHANISM);
        MECHANISMS_WITH_TRUST_STORE.add(CLIENT_CERT_MECHANISM);

        MECHANISMS_LOCAL_USER.add(JBOSS_LOCAL_USER_MECHANISM);
    }

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

    public static List<String> getSecurityDomainNames(ModelControllerClient client) {
        return getNames(client, Util.SECURITY_DOMAIN);
    }

    public static List<String> getConstantRoleMappers(ModelControllerClient client) {
        return getNames(client, Util.CONSTANT_ROLE_MAPPER);
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

    public static boolean hasServerSSLContext(CommandContext context, String sslContextName) {
        return getChildResource(sslContextName, Util.SERVER_SSL_CONTEXT, context) != null;
    }

    public static ServerSSLContext getServerSSLContext(CommandContext context, String sslContextName) {
        ModelNode sslContext = getChildResource(sslContextName, Util.SERVER_SSL_CONTEXT, context);
        ServerSSLContext ctx = null;
        if (sslContext != null) {
            String kmName = sslContext.get(Util.KEY_MANAGER).asString();
            ModelNode km = getChildResource(kmName, Util.KEY_MANAGER, context);
            if (km == null) {
                throw new IllegalArgumentException("The ServerSSLContext " + sslContextName + " references the KeyManager " + kmName + " that doesn't exist.");
            }
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

    public static ModelNode getAuthFactoryResource(String authFactory, AuthFactorySpec spec, CommandContext ctx) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(spec.getResourceType(), authFactory);
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

    public static ModelNode reorderSASLFactory(CommandContext ctx, List<String> order, String factoryName) throws Exception {
        ModelNode factory = getAuthFactoryResource(factoryName, AuthFactorySpec.SASL, ctx);
        if (factory == null) {
            throw new Exception("Invalid factory name " + factoryName);
        }
        if (factory.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            ModelNode mechanisms = factory.get(Util.MECHANISM_CONFIGURATIONS);
            Set<String> seen = new HashSet<>();
            List<ModelNode> newOrder = new ArrayList<>();
            for (String o : order) {
                for (ModelNode m : mechanisms.asList()) {
                    String name = m.get(Util.MECHANISM_NAME).asString();
                    if (o.equals(name)) {
                        newOrder.add(m);
                    }
                    seen.add(name);
                }
            }
            for (String r : order) {
                if (!seen.contains(r)) {
                    throw new Exception("Mechanism " + r
                            + " is not contained in SASL factory " + factoryName);
                }
            }
            if (!order.containsAll(seen)) {
                throw new Exception("Mechanism list is not complete, existing mechanisms are:" + seen);
            }

            if (newOrder.isEmpty()) {
                throw new Exception("Error: All mechanisms would be removed, this would fully disable access.");
            }
            ModelNode newValue = new ModelNode();
            newValue.set(newOrder);
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.WRITE_ATTRIBUTE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(AuthFactorySpec.SASL.getResourceType(), factoryName);
            builder.addProperty(Util.NAME, Util.MECHANISM_CONFIGURATIONS);
            builder.getModelNode().get(Util.VALUE).set(newValue);
            return builder.buildRequest();
        } else {
            throw new Exception("No mechanism to re-order in Factory " + factoryName);
        }
    }

    public static AuthFactory findMatchingAuthFactory(AuthMechanism newMechanism,
            AuthFactorySpec spec, CommandContext ctx) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, spec.getResourceType());
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        AuthFactory factory = null;
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                for (String ksName : res.keys()) {
                    ModelNode ks = res.get(ksName);
                    AuthFactory fact = getAuthFactory(ks, ksName, spec, ctx);
                    List<AuthMechanism> mecs = fact != null ? fact.getMechanisms() : Collections.emptyList();
                    if (mecs.isEmpty() || mecs.size() > 1) {
                        continue;
                    }
                    AuthMechanism mec = mecs.get(0);
                    // Only compare, type, realmName and realmMapper.
                    if (newMechanism.getType().equals(mec.getType())) {
                        if (newMechanism.getConfig().getRealmMapper() == null) {
                            if (Objects.equals(newMechanism.getConfig().getRealmName(),
                                    mec.getConfig().getRealmName())) {
                                factory = fact;
                                break;
                            }
                        } else if (Objects.equals(newMechanism.getConfig().getRealmMapper(),
                                mec.getConfig().getRealmMapper())) {
                            factory = fact;
                            break;
                        }
                    }
                }
            }
        }
        return factory;
    }

    public static AuthFactory getAuthFactory(String authFactory, AuthFactorySpec spec, CommandContext ctx) {
        ModelNode mn = getAuthFactoryResource(authFactory, spec, ctx);
        return getAuthFactory(mn, authFactory, spec, ctx);
    }

    public static AuthFactory getAuthFactory(ModelNode mn, String authFactory, AuthFactorySpec spec, CommandContext ctx) {
        AuthFactory factory = null;
        if (mn != null) {
            SecurityDomain sc = new SecurityDomain(mn.get(Util.SECURITY_DOMAIN).asString());
            factory = new AuthFactory(authFactory, sc, spec);
            if (mn.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
                ModelNode lst = mn.get(Util.MECHANISM_CONFIGURATIONS);
                for (ModelNode m : lst.asList()) {
                    String name = m.get(Util.MECHANISM_NAME).asString();
                    String realmMapper = null;
                    String realmName = null;
                    if (m.hasDefined(Util.REALM_MAPPER)) {
                        realmMapper = m.get(Util.REALM_MAPPER).asString();
                    }
                    // XXX This could be evolved with new exposed attributes
                    if (m.hasDefined(Util.MECHANISM_REALM_CONFIGURATIONS)) {
                        ModelNode config = m.get(Util.MECHANISM_REALM_CONFIGURATIONS);
                        for (ModelNode c : config.asList()) {
                            if (c.hasDefined(Util.REALM_NAME)) {
                                realmName = c.get(Util.REALM_NAME).asString();
                                break;
                            }
                        }
                    }
                    String finalRealmName = realmName;
                    String finalRealmMapper = realmMapper;
                    AuthMechanism mec = new AuthMechanism(name, new MechanismConfiguration() {
                        @Override
                        public String getRealmName() {
                            return finalRealmName;
                        }

                        @Override
                        public String getRoleDecoder() {
                            return null;
                        }

                        @Override
                        public String getRoleMapper() {
                            return null;
                        }

                        @Override
                        public String getRealmMapper() {
                            return finalRealmMapper;
                        }

                        @Override
                        public String getExposedRealmName() {
                            return finalRealmName;
                        }

                        @Override
                        public void setRealmMapperName(String constantMapper) {
                            // NO-OP
                        }
                    });
                    factory.addMechanism(mec);
                }
            }
        }
        return factory;
    }

    public static String findMatchingUsersPropertiesRealm(CommandContext ctx,
            PropertiesRealmConfiguration config) throws Exception {
        ModelNode resource = buildRealmResource(config);
        List<String> names = findMatchingResources(ctx, Util.PROPERTIES_REALM, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    private static ModelNode buildRealmResource(Realm realm) {
        ModelNode mn = new ModelNode();
        mn.get(Util.REALM).set(realm.getResourceName());
        if (realm.getConfig().getRoleDecoder() != null) {
            mn.get(Util.ROLE_DECODER).set(realm.getConfig().getRoleDecoder());
        }
        if (realm.getConfig().getRoleMapper() != null) {
            mn.get(Util.ROLE_MAPPER).set(realm.getConfig().getRoleMapper());
        }
        return mn;
    }

    private static ModelNode buildRealmResource(PropertiesRealmConfiguration config) throws Exception {
        ModelNode localRealm = new ModelNode();
        localRealm.get(Util.GROUPS_ATTRIBUTE).set(Util.GROUPS);
        if (config.getGroupPropertiesFile() != null) {
            localRealm.get(Util.GROUPS_PROPERTIES).set(buildGroupsResource(config));
        }
        localRealm.get(Util.USERS_PROPERTIES).set(buildUsersResource(config));
        return localRealm;
    }

    private static ModelNode buildGroupsResource(PropertiesRealmConfiguration config) throws IOException {
        ModelNode mn = new ModelNode();
        mn.get(Util.PATH).set(config.getGroupPropertiesFile());
        if (config.getRelativeTo() != null) {
            mn.get(Util.RELATIVE_TO).set(config.getRelativeTo());
        }
        return mn;
    }

    private static ModelNode buildUsersResource(PropertiesRealmConfiguration config) throws IOException {
        ModelNode mn = new ModelNode();
        mn.get(Util.PATH).set(config.getUserPropertiesFile());
        if (config.getRelativeTo() != null) {
            mn.get(Util.RELATIVE_TO).set(config.getRelativeTo());
        }
        mn.get(Util.DIGEST_REALM_NAME).set(config.getExposedRealmName());
        if (config.getPlainText()) {
            mn.get(Util.PLAIN_TEXT).set(config.getPlainText());
        }
        return mn;
    }

    public static boolean serverPropertiesRealmExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.PROPERTIES_REALM, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static ModelNode addUsersPropertiesRealm(CommandContext ctx, String realmName,
            PropertiesRealmConfiguration config) throws Exception {
        ModelNode mn = buildRealmResource(config);
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.PROPERTIES_REALM, realmName);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    public static String findKeyStoreRealm(CommandContext ctx, String trustStore) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        String name = null;
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, Util.KEY_STORE_REALM);
        ModelNode response = ctx.getModelControllerClient().execute(builder.buildRequest());
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode mn = response.get(Util.RESULT);
                for (String key : mn.keys()) {
                    ModelNode ksr = mn.get(key);
                    if (ksr.hasDefined(Util.KEY_STORE)) {
                        String ks = ksr.get(Util.KEY_STORE).asString();
                        if (ks.equals(trustStore)) {
                            name = key;
                            break;
                        }
                    }
                }
            }
        }
        return name;
    }

    public static ModelNode addKeyStoreRealm(CommandContext ctx, String ksRealmName, String keyStore) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE_REALM, ksRealmName);
        builder.addProperty(Util.KEY_STORE, keyStore);
        return builder.buildRequest();
    }

    public static String findConstantRealmMapper(CommandContext ctx, String realmName) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_CHILDREN_RESOURCES);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addProperty(Util.CHILD_TYPE, Util.CONSTANT_REALM_MAPPER);
        ModelNode request = builder.buildRequest();
        ModelNode response = ctx.getModelControllerClient().execute(request);
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                ModelNode res = response.get(Util.RESULT);
                for (String csName : res.keys()) {
                    ModelNode mn = res.get(csName);
                    String constantName = mn.get(Util.REALM_NAME).asString();
                    if (realmName.equals(constantName)) {
                        return csName;
                    }
                }
            }
        }
        return null;
    }

    public static ModelNode addConstantRealmMapper(CommandContext ctx, String realmName) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.CONSTANT_REALM_MAPPER, realmName);
        builder.addProperty(Util.REALM_NAME, realmName);
        return builder.buildRequest();
    }

    public static boolean securityDomainExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SECURITY_DOMAIN, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static ModelNode addSecurityDomain(CommandContext ctx, Realm realm,
            String newSecurityDomain) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SECURITY_DOMAIN, newSecurityDomain);
        ModelNode mn = buildSecurityDomainResource(realm);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    private static ModelNode buildSecurityDomainResource(Realm realm) {
        ModelNode sd = new ModelNode();
        if (realm != null) {
            sd.get(Util.REALMS).add(buildRealmResource(realm));
        }
        sd.get(Util.PERMISSION_MAPPER).set(Util.DEFAULT_PERMISSION_MAPPER);

        return sd;
    }

    public static boolean factoryExists(CommandContext ctx, String name, AuthFactorySpec spec) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(spec.getResourceType(), name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static ModelNode addAuthFactory(CommandContext ctx, SecurityDomain securityDomain,
            String newAuthFactoryName, AuthFactorySpec spec) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(spec.getResourceType(), newAuthFactoryName);
        ModelNode mn = buildAuthFactoryResource(securityDomain, spec);
        for (String k : mn.keys()) {
            builder.getModelNode().get(k).set(mn.get(k));
        }
        return builder.buildRequest();
    }

    private static ModelNode buildAuthFactoryResource(SecurityDomain domain, AuthFactorySpec spec) {
        ModelNode sd = new ModelNode();
        sd.get(spec.getServerType()).set(spec.getServerValue());
        sd.get(Util.SECURITY_DOMAIN).set(domain.getName());
        return sd;
    }

    public static void addAuthMechanism(CommandContext ctx, AuthFactory authFactory,
            AuthMechanism mechanism, ModelNode steps) throws OperationFormatException {
        ModelNode mechanisms = retrieveMechanisms(ctx, authFactory);
        ModelNode newMechanism = buildMechanismResource(mechanism);
        // check if a mechanism with the same name exists, replace it.
        int index = 0;
        boolean found = false;
        for (ModelNode m : mechanisms.asList()) {
            if (m.hasDefined(Util.MECHANISM_NAME)) {
                String name = m.get(Util.MECHANISM_NAME).asString();
                if (name.equals(mechanism.getType())) {
                    // Already have the exact same mechanism, no need to add it.
                    if (newMechanism.equals(m)) {
                        return;
                    }
                    found = true;
                    break;
                }
            }
            index += 1;
        }

        if (found) {
            mechanisms.remove(index);
            mechanisms.insert(newMechanism, index);
        } else {
            mechanisms.add(newMechanism);
        }

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(authFactory.getSpec().getResourceType(), authFactory.getName());
        builder.getModelNode().get(Util.VALUE).set(mechanisms);
        builder.getModelNode().get(Util.NAME).set(Util.MECHANISM_CONFIGURATIONS);
        steps.add(builder.buildRequest());
    }

    private static ModelNode buildMechanismResource(AuthMechanism mechanism) {
        ModelNode mn = new ModelNode();
        mn.get(Util.MECHANISM_NAME).set(mechanism.getType());
        if (mechanism.getConfig().getRealmMapper() != null) {
            mn.get(Util.REALM_MAPPER).set(mechanism.getConfig().getRealmMapper());
        }
        if (mechanism.getConfig().getExposedRealmName() != null) {
            ModelNode realmConfig = new ModelNode();
            realmConfig.get(Util.REALM_NAME).set(mechanism.getConfig().getExposedRealmName());
            mn.get(Util.MECHANISM_REALM_CONFIGURATIONS).add(realmConfig);
        }
        return mn;
    }

    public static void addRealm(CommandContext ctx, SecurityDomain securityDomain, Realm realm, ModelNode steps) throws OperationFormatException {
        ModelNode realms = retrieveSecurityDomainRealms(ctx, securityDomain);
        ModelNode newRealm = buildRealmResource(realm);
        int index = 0;
        boolean found = false;
        for (ModelNode r : realms.asList()) {
            if (r.hasDefined(Util.REALM)) {
                String n = r.get(Util.REALM).asString();
                // Already present, skip....
                if (n.equals(realm.getResourceName())) {
                    if (newRealm.equals(r)) {
                        return;
                    }
                    // We need to replace it with the new one.
                    found = true;
                    break;
                }
            }
            index += 1;
        }
        if (found) {
            realms.remove(index);
            realms.insert(newRealm, index);
        } else {
            realms.add(newRealm);
        }
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.SECURITY_DOMAIN, securityDomain.getName());
        builder.getModelNode().get(Util.VALUE).set(realms);
        builder.getModelNode().get(Util.NAME).set(Util.REALMS);
        steps.add(builder.buildRequest());
    }

    private static ModelNode retrieveSecurityDomainRealms(CommandContext ctx, SecurityDomain domain) {
        ModelNode mn = getSecurityDomainResource(domain, ctx);
        if (mn == null) {
            return new ModelNode().setEmptyList();
        } else if (mn.hasDefined(Util.REALMS)) {
            return mn.get(Util.REALMS);
        } else {
            return new ModelNode().setEmptyList();
        }
    }

    public static ModelNode getSecurityDomainResource(SecurityDomain domain, CommandContext ctx) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(Util.SECURITY_DOMAIN, domain.getName());
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

    private static ModelNode retrieveMechanisms(CommandContext ctx, AuthFactory authFactory) {
        ModelNode mn = getAuthFactoryResource(authFactory.getName(), authFactory.getSpec(), ctx);
        if (mn == null) {
            return new ModelNode().setEmptyList();
        } else if (mn.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            return mn.get(Util.MECHANISM_CONFIGURATIONS);
        } else {
            return new ModelNode().setEmptyList();
        }
    }

    public static final Set<String> getMechanismsWithRealm() {
        return MECHANISMS_WITH_REALM;
    }

    public static final Set<String> getMechanismsWithTrustStore() {
        return MECHANISMS_WITH_TRUST_STORE;
    }

    public static final Set<String> getMechanismsLocalUser() {
        return MECHANISMS_LOCAL_USER;
    }

    private static boolean isMechanismSupported(String name) {
        return getMechanismsWithRealm().contains(name) || getMechanismsWithTrustStore().contains(name) || getMechanismsLocalUser().contains(name);
    }

    public static List<String> getMechanisms(CommandContext ctx, AuthFactorySpec spec) throws OperationFormatException, IOException {
        List<String> lst = new ArrayList<>();
        for (String m : getAvailableMechanisms(ctx, spec)) {
            if (isMechanismSupported(m)) {
                lst.add(m);
            }
        }
        return lst;
    }

    public static List<String> getAvailableMechanisms(CommandContext ctx, AuthFactorySpec spec) throws OperationFormatException, IOException {
        List<String> lst = new ArrayList<>();
        ModelNode resource = getServerFactory(spec.getServerValue(), spec, ctx);
        if (resource == null) {
            return null;
        }
        for (ModelNode m : resource.get(Util.AVAILABLE_MECHANISMS).asList()) {
            lst.add(m.asString());
        }
        return lst;
    }

    public static List<String> getFileSystemRealmNames(ModelControllerClient client) {
        return getNames(client, Util.FILESYSTEM_REALM);
    }

    public static List<String> getPropertiesRealmNames(ModelControllerClient client) {
        return getNames(client, Util.PROPERTIES_REALM);
    }

    public static List<String> getKeyStoreRealmNames(ModelControllerClient client) {
        return getNames(client, Util.KEY_STORE_REALM);
    }

    private static ModelNode getServerFactory(String factory, AuthFactorySpec spec, CommandContext ctx) throws OperationFormatException, IOException {
        for (ModelNode point : getServerFactoriesProviderPoints(ctx, spec)) {
            ModelNode fact = getServerfactory(ctx, point.asString(), factory);
            if (fact != null) {
                return fact;
            }
        }
        return null;
    }

    private static List<ModelNode> getServerFactoriesProviderPoints(CommandContext ctx, AuthFactorySpec spec) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.GET_PROVIDER_POINTS);
        builder.addNode(Util.CORE_SERVICE, Util.CAPABILITY_REGISTRY);
        builder.getModelNode().get(Util.NAME).set(spec.getCapability());
        ModelNode response = ctx.getModelControllerClient().execute(builder.buildRequest());
        if (Util.isSuccess(response)) {
            return response.get(Util.RESULT).asList();
        }
        return null;
    }

    // Simplistic for now, the format is something like: /subsystem=elytron/aggregate-sasl-server-factory=*
    private static ModelNode getServerfactory(CommandContext ctx, String point, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        for (String p : point.split("/")) {
            if (p.isEmpty()) {
                continue;
            }
            String[] ps = p.split("=");
            if (ps[1].equals("*")) {
                ps[1] = name;
            }
            builder.addNode(ps[0], ps[1]);
        }
        builder.getModelNode().get(Util.INCLUDE_RUNTIME).set(true);
        ModelNode response = ctx.getModelControllerClient().execute(builder.buildRequest());
        if (Util.isSuccess(response)) {
            return response.get(Util.RESULT);
        }
        return null;
    }

    public static List<String> getSimpleDecoderNames(ModelControllerClient client) {
        return getNames(client, Util.SIMPLE_ROLE_DECODER);
    }

    public static boolean localUserExists(CommandContext ctx) throws IOException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.IDENTITY_REALM, Util.LOCAL);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static ModelNode removeMechanisms(CommandContext ctx, ModelNode factory,
            String factoryName, AuthFactorySpec spec, Set<String> toRemove) throws Exception {
        if (factory.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            List<ModelNode> remains = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            ModelNode mechanisms = factory.get(Util.MECHANISM_CONFIGURATIONS);
            for (ModelNode m : mechanisms.asList()) {
                String name = m.get(Util.MECHANISM_NAME).asString();
                if (!toRemove.contains(name)) {
                    remains.add(m);
                }
                seen.add(name);
            }
            for (String r : toRemove) {
                if (!seen.contains(r)) {
                    throw new Exception("Mechanism " + r
                            + " is not contained in factory " + factoryName);
                }
            }
            if (remains.isEmpty()) {
                throw new Exception("Error: All mechanisms would be removed, this would fully disable access. "
                        + "To fully disable authentication, don't provide mechanism.");
            }
            ModelNode newValue = new ModelNode();
            newValue.set(remains);
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.WRITE_ATTRIBUTE);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addNode(spec.getResourceType(), factoryName);
            builder.addProperty(Util.NAME, Util.MECHANISM_CONFIGURATIONS);
            builder.getModelNode().get(Util.VALUE).set(newValue);
            return builder.buildRequest();
        } else {
            throw new Exception("No mechanism to remove in Factory " + factoryName);
        }
    }

    public static List<String> getMechanisms(CommandContext ctx, String factoryName, AuthFactorySpec spec) throws Exception {
        ModelNode factory = getAuthFactoryResource(factoryName, spec, ctx);
        if (factory == null) {
            throw new Exception("Invalid factory name " + factoryName);
        }
        List<String> lst = new ArrayList<>();
        if (factory.hasDefined(Util.MECHANISM_CONFIGURATIONS)) {
            ModelNode mechanisms = factory.get(Util.MECHANISM_CONFIGURATIONS);
            for (ModelNode m : mechanisms.asList()) {
                String name = m.get(Util.MECHANISM_NAME).asString();
                lst.add(name);
            }
        } else {
            throw new Exception("No mechanism in Factory " + factoryName);
        }
        return lst;
    }

    static String findMatchingConstantRoleMapper(List<String> roles,
            CommandContext ctx) throws OperationFormatException, IOException {
        ModelNode resource = buildConstantRoleMapperResource(roles);
        List<String> names = findMatchingResources(ctx, Util.CONSTANT_ROLE_MAPPER, resource);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(0);
    }

    private static ModelNode buildConstantRoleMapperResource(List<String> roles) {
        ModelNode mn = new ModelNode();
        ModelNode r = mn.get(Util.ROLES);
        for (String role : roles) {
            r.add(role);
        }
        return mn;
    }

    static ModelNode buildConstantRoleMapper(List<String> roles, String mapperName, CommandContext ctx) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.CONSTANT_ROLE_MAPPER, mapperName);
        builder.getModelNode().get(Util.ROLES).set(buildConstantRoleMapperResource(roles).get(Util.ROLES));
        return builder.buildRequest();
    }

    public static boolean constantRoleMapperExists(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.CONSTANT_ROLE_MAPPER, name);
        return Util.isSuccess(ctx.getModelControllerClient().execute(builder.buildRequest()));
    }

    public static ModelNode addCertificateAuthority(CertificateAuthority certificateAuthority) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.CERTIFICATE_AUTHORITY, certificateAuthority.getName());
        builder.addProperty(Util.URL, certificateAuthority.getUrl());
        return builder.buildRequest();
    }

    public static ModelNode addCertificateAuthorityAccount(String name, String password, String alias, String keyStoreName, List<String> contactUrls, CertificateAuthority customCertificateAuthority) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.CERTIFICATE_AUTHORITY_ACCOUNT, name);
        if(!contactUrls.isEmpty()) {
            builder.getModelNode().get(Util.CONTACT_URLS).set(createModelNodes(contactUrls));
        }
        builder.addProperty(Util.KEY_STORE, keyStoreName);
        builder.addProperty(Util.ALIAS, alias);
        if (customCertificateAuthority != null) {
            builder.addProperty(Util.CERTIFICATE_AUTHORITY, customCertificateAuthority.getName());
        }
        builder.getModelNode().get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(password));
        return builder.buildRequest();
    }

    private static List<ModelNode> createModelNodes(List<String> items) {
        List<ModelNode> modelNodes = new ArrayList<>();
        for (String item : items) {
            if(!item.isEmpty()) {
                modelNodes.add(new ModelNode(item));
            }
        }
        return modelNodes;
    }

    public static ModelNode removeCertificateAuthorityAccount(String name) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.REMOVE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.CERTIFICATE_AUTHORITY_ACCOUNT, name);
        return builder.buildRequest();
    }

    public static ModelNode obtainCertificateRequest(String keyStoreName,
                                                     String alias,
                                                     String password,
                                                     List<String> domainNames,
                                                     String certificateAuthorityAccount,
                                                     boolean agreedToTOS,
                                                     int key_size,
                                                     String key_algorithm) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.OBTAIN_CERTIFICATE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(Util.KEY_STORE, keyStoreName);
        builder.addProperty(Util.ALIAS, alias);
        builder.getModelNode().get(Util.DOMAIN_NAMES).set(createModelNodes(domainNames));
        builder.addProperty(Util.CERTIFICATE_AUTHORITY_ACCOUNT, certificateAuthorityAccount);
        builder.addProperty(Util.AGREE_TO_TERMS_OF_SERVICE, String.valueOf(agreedToTOS));
        builder.getModelNode().get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(password));
        builder.addProperty(Util.ALGORITHM, key_algorithm);
        builder.addProperty(Util.KEY_SIZE, String.valueOf(key_size));
        return builder.buildRequest();
    }

    public static List<String> getCaAccountNames(ModelControllerClient client) {
        return getNames(client, Util.CERTIFICATE_AUTHORITY_ACCOUNT);
    }
}
