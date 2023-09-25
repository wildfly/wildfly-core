/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUST_STORE_NAME;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Base class to build a complete SSL configuration set of requests.
 *
 * @author jdenise@redhat.com
 */
public abstract class SSLSecurityBuilder implements SecurityCommand.FailureConsumer {

    public FailureDescProvider NO_DESC = new FailureDescProvider() {
        @Override
        public String stepFailedDescription() {
            return null;
        }
    };

    public interface FailureDescProvider {

        String stepFailedDescription();
    }

    private final List<FailureDescProvider> providers = new ArrayList<>();
    private final List<FailureDescProvider> finalProviders = new ArrayList<>();
    private final List<FailureDescProvider> effectiveProviders = new ArrayList<>();

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private String sslContextName;
    private String keyManagerName;
    private final ModelNode composite = new ModelNode();
    private ServerSSLContext sslContext;
    private File trustedCertificate;
    private String trustStoreName;
    private String trustStoreFileName;
    private String generatedTrustStore;
    private String trustStoreFilePassword;
    private String newTrustStoreName;
    private String newTrustManagerName;

    private boolean validateCertificate;

    private final Set<String> ksToStore = new HashSet<>();
    private final List<ModelNode> finalSteps = new ArrayList<>();

    public SSLSecurityBuilder() throws CommandException {
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
    }

    protected void needKeyStoreStore(String keyStoreName) {
        ksToStore.add(keyStoreName);
    }

    protected void addFinalstep(ModelNode step, FailureDescProvider ex) {
        finalSteps.add(step);
        finalProviders.add(ex);
    }

    public void setNewTrustStoreName(String newTrustStoreName) {
        this.newTrustStoreName = newTrustStoreName;
    }

    public void setNewTrustManagerName(String newTrustManagerName) {
        this.newTrustManagerName = newTrustManagerName;
    }

    // Sort and order steps to avoid unwanted generation
    public ModelNode buildExecutableRequest(CommandContext ctx) throws Exception {
        try {
            for (FailureDescProvider h : providers) {
                effectiveProviders.add(h);
            }
            // In case some key-store needs to be persisted
            for (String ks : ksToStore) {
                composite.get(Util.STEPS).add(ElytronUtil.storeKeyStore(ctx, ks));
                effectiveProviders.add(new FailureDescProvider() {
                    @Override
                    public String stepFailedDescription() {
                        return "Storing the key-store " + ksToStore;
                    }
                });
            }
            // Final steps
            for (int i = 0; i < finalSteps.size(); i++) {
                composite.get(Util.STEPS).add(finalSteps.get(i));
                effectiveProviders.add(finalProviders.get(i));
            }
            return composite;
        } catch (Exception ex) {
            try {
                failureOccured(ctx, null);
            } catch (Exception ex2) {
                ex.addSuppressed(ex2);
            }
            throw ex;
        }
    }

    public File getTrustedCertificatePath() {
        return trustedCertificate;
    }

    public void setTrustedCertificatePath(File trustedCertificate) {
        this.trustedCertificate = trustedCertificate;
    }

    public void setValidateCertificate(boolean validateCertificate) {
        this.validateCertificate = validateCertificate;
    }

    public void addStep(ModelNode step, FailureDescProvider ex) {
        checkNotNullParamWithNullPointerException("step", step);
        checkNotNullParamWithNullPointerException("ex", ex);
        composite.get(Util.STEPS).add(step);
        providers.add(ex);
    }

    public ServerSSLContext getServerSSLContext() {
        return sslContext;
    }

    public SSLSecurityBuilder setSSLContextName(String sslContextName) {
        this.sslContextName = sslContextName;
        return this;
    }

    public SSLSecurityBuilder setKeyManagerName(String keyManagerName) {
        this.keyManagerName = keyManagerName;
        return this;
    }

    protected abstract KeyStore buildKeyStore(CommandContext ctx, boolean workaroundComposite) throws Exception;

    public void buildRequest(CommandContext ctx, boolean buildRequest) throws Exception {
        KeyStore keyStore;
        KeyManager km;
        try {
            // First build the keyStore.
            keyStore = buildKeyStore(ctx, buildRequest);

            // The trust manager
            KeyManager trustManager = buildTrustManager(ctx, buildRequest);

            // Then the key manager
            km = buildKeyManager(ctx, keyManagerName, keyStore);

            // Finally the SSLContext;
            sslContext = buildServerSSLContext(ctx, km, trustManager);
        } catch (Exception ex) {
            try {
                failureOccured(ctx, null);
            } catch (Exception ex2) {
                ex.addSuppressed(ex2);
            }
            throw ex;
        }
    }

    protected KeyManager buildTrustManager(CommandContext ctx, boolean buildRequest) throws Exception {
        KeyManager trustManager = null;
        if (trustedCertificate != null || trustStoreName != null) {
            KeyStore trustStore = null;
            String id = UUID.randomUUID().toString();
            // create a new key-store for the trustore and import the certificate.
            if (newTrustStoreName == null) {
                newTrustStoreName = "trust-store-" + id;
            } else if (ElytronUtil.keyStoreExists(ctx, newTrustStoreName)) {
                throw new CommandException("The key-store " + newTrustStoreName + " already exists");
            }
            if (trustStoreName == null) {
                if (trustStoreFileName == null) {
                    trustStoreFileName = "server-" + id + ".trustore";
                } else {
                    List<String> ksNames = ElytronUtil.findMatchingKeyStores(ctx, new File(trustStoreFileName), Util.JBOSS_SERVER_CONFIG_DIR);
                    if (!ksNames.isEmpty()) {
                        throw new CommandException("Error, the file " + trustStoreFileName + " is already referenced from " + ksNames
                                + " resources. Use " + SecurityCommand.formatOption(OPT_TRUST_STORE_NAME) + " option or choose another file name.");
                    }
                }
                generatedTrustStore = newTrustStoreName;
                String password = trustStoreFilePassword == null ? generateRandomPassword() : trustStoreFilePassword;
                ModelNode request = ElytronUtil.addKeyStore(ctx, newTrustStoreName, new File(trustStoreFileName),
                        Util.JBOSS_SERVER_CONFIG_DIR, password, ElytronUtil.JKS, false, null);
                // For now that is a workaround because we can't add and call operation in same composite.
                // REMOVE WHEN WFCORE-3491 is fixed.
                if (buildRequest) { // echo-dmr
                    addStep(request, NO_DESC);
                } else {
                    SecurityCommand.execute(ctx, request, SecurityCommand.DEFAULT_FAILURE_CONSUMER);
                }
                trustStore = new KeyStore(newTrustStoreName, password, false);
                // import the certificate, hard code that we check against cacert.
                ModelNode certImport = ElytronUtil.importCertificate(ctx, trustedCertificate, id, validateCertificate, trustStore, true);
                addStep(certImport, new FailureDescProvider() {
                    @Override
                    public String stepFailedDescription() {
                        return "Importing certificate "
                                + trustedCertificate.getAbsolutePath()
                                + " in trust-store " + newTrustStoreName;
                    }
                });
                needKeyStoreStore(trustStore.getName());
            } else {
                trustStore = ElytronUtil.getKeyStore(ctx, trustStoreName);
            }
            //Create a trust-manager
            trustManager = buildTrustManager(ctx, newTrustManagerName, trustStore);
        }
        return trustManager;
    }

    private KeyManager buildKeyManager(CommandContext ctx, String ksManagerName, KeyStore keyStore) throws Exception {
        boolean lookupExisting = false;
        if (ksManagerName == null) {
            ksManagerName = DefaultResourceNames.buildDefaultKeyManagerName(ctx, keyStore.getName());
            lookupExisting = true;
        } else if (ElytronUtil.keyManagerExists(ctx, ksManagerName)) {
            throw new CommandException("The key-manager " + ksManagerName + " already exists");
        }
        String name = null;
        boolean exists = false;
        // Lookup for a matching key manager only if the keystore already exists,
        // the KeyManager doesn't exist and no name has been provided
        if (keyStore.exists() && lookupExisting) {
            name = ElytronUtil.findMatchingKeyManager(ctx, keyStore, null, null);
        }
        if (name == null) {
            name = ksManagerName;
            final String kmName = name;
            addStep(ElytronUtil.addKeyManager(ctx, keyStore, ksManagerName, null, null), new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Adding key-manager " + kmName;
                }
            });
        } else {
            exists = true;
        }
        return new KeyManager(name, keyStore, exists);
    }

    private KeyManager buildTrustManager(CommandContext ctx, String ksManagerName, KeyStore keyStore) throws Exception {
        boolean lookupExisting = false;
        if (ksManagerName == null) {
            ksManagerName = DefaultResourceNames.buildDefaultKeyManagerName(ctx, keyStore.getName());
            lookupExisting = true;
        } else if (ElytronUtil.trustManagerExists(ctx, ksManagerName)) {
            throw new CommandException("The key-manager " + ksManagerName + " already exists");
        }
        String name = null;
        boolean exists = false;
        // Lookup for a matching key manager only if the keystore already exists.
        if (keyStore.exists() && lookupExisting) {
            name = ElytronUtil.findMatchingTrustManager(ctx, keyStore, null, null);
        }
        if (name == null) {
            name = ksManagerName;
            final String tmName = ksManagerName;
            addStep(ElytronUtil.addTrustManager(ctx, keyStore, ksManagerName, null, null), new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Adding trust-manager " + tmName;
                }
            });
        } else {
            exists = true;
        }
        return new KeyManager(name, keyStore, exists);
    }

    private ServerSSLContext buildServerSSLContext(CommandContext ctx, KeyManager manager, KeyManager trustManager) throws Exception {
        boolean lookupExisting = false;
        if (sslContextName == null) {
            sslContextName = DefaultResourceNames.buildDefaultSSLContextName(ctx, manager.getKeyStore().getName());
            lookupExisting = true;
        } else if (ElytronUtil.serverSSLContextExists(ctx, sslContextName)) {
            throw new CommandException("The ssl-context " + sslContextName + " already exists");
        }
        List<String> lst = DefaultResourceNames.getDefaultProtocols(ctx);
        String name = null;
        boolean exists = false;

        boolean need = trustManager != null;
        // Lookup for a matching sslContext only if the keymanager already exists
        // and no name has been provided
        if (manager.exists() && lookupExisting) {
            ServerSSLContext sslCtx = new ServerSSLContext(null, manager, trustManager, false);
            sslCtx.setNeed(need);
            sslCtx.setProtocols(lst);
            name = ElytronUtil.findMatchingSSLContext(ctx, sslCtx);
        }

        if (name == null) {
            name = sslContextName;
        } else {
            exists = true;
        }
        ServerSSLContext sslCtx = new ServerSSLContext(name, manager, trustManager, exists);
        sslCtx.setNeed(need);
        sslCtx.setProtocols(lst);
        if (!exists) {
            addStep(ElytronUtil.addServerSSLContext(ctx, sslCtx, sslContextName), new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Adding ssl-context " + sslContextName;
                }
            });
        }

        return sslCtx;
    }

    private String getFailedStepDescription(CommandContext ctx, ModelNode responseNode) {
        if (responseNode == null) {
            return null;
        }
        ModelNode mn = responseNode.get(Util.RESULT);
        StringBuilder msg = new StringBuilder();
        if (mn.isDefined()) {
            int index = 0;
            ModelNode fd = responseNode.get(Util.FAILURE_DESCRIPTION);
            if (fd.isDefined()) {
                for (Property prop : mn.asPropertyList()) {
                    ModelNode val = prop.getValue();
                    if (val.hasDefined(Util.FAILURE_DESCRIPTION)) {
                        String description = effectiveProviders.get(index).
                                stepFailedDescription();
                        msg.append("\nERROR, security changes have not been applied.\n");
                        if (description != null) {
                            msg.append("Failed action: ").append(description).append("\n");
                        }
                        msg.append("Cause: ").append(val.get(Util.FAILURE_DESCRIPTION).asString()).append("\n");
                        break;
                    }
                    index += 1;
                }
            }
        }

        return msg.toString();
    }

    @Override
    public void failureOccured(CommandContext ctx, ModelNode mn) throws CommandException {
        StringBuilder builder = new StringBuilder();
        boolean failure = false;
        // A step failed
        if (mn != null) {
            String desc = getFailedStepDescription(ctx, mn);
            builder.append(desc).append("\n");
            failure = true;
        }
        try {
            // REMOVE WHEN WFCORE-3491 is fixed.
            if (generatedTrustStore != null) {
                ModelNode req = ElytronUtil.removeKeyStore(ctx, generatedTrustStore);
                SecurityCommand.execute(ctx, req, SecurityCommand.DEFAULT_FAILURE_CONSUMER, false);
            }
        } catch (Exception ex) {
            builder.append("Error while cleaning up key-stores " + ex).append("\n");
            failure = true;
        } finally {
            try {
                doFailureOccured(ctx);
            } catch (Exception ex) {
                builder.append("Error while cleaning up " + ex);
                failure = true;
            }
        }
        if (failure) {
            throw new CommandException(builder.toString());
        }
    }

    protected abstract void doFailureOccured(CommandContext ctx) throws Exception;

    /**
     * @return the trustStoreName
     */
    public String getTrustStoreName() {
        return trustStoreName;
    }

    /**
     * @param trustStoreName the trustStoreName to set
     */
    public void setTrustStoreName(String trustStoreName) {
        this.trustStoreName = trustStoreName;
    }

    /**
     * @return the trustStoreFileName
     */
    public String getTrustStoreFileName() {
        return trustStoreFileName;
    }

    /**
     * @param trustStoreFileName the trustStoreFileName to set
     */
    public void setTrustStoreFileName(String trustStoreFileName) {
        this.trustStoreFileName = trustStoreFileName;
    }

    public void setTrustStoreFilePassword(String trustStoreFilePassword) {
        this.trustStoreFilePassword = trustStoreFilePassword;
    }

    static String generateRandomPassword() {
        return generateRandomString(8);
    }

    static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (RANDOM.nextDouble() * CHARS.length());
            builder.append(CHARS.substring(index, index + 1));
        }
        return builder.toString();
    }

}
