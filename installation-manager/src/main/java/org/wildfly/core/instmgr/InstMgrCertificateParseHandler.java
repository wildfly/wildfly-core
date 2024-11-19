/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_DESCRIPTION;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_FINGERPRINT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_KEY_ID;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_STATUS;

import java.io.InputStream;
import java.nio.file.Path;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.TrustCertificate;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler to parse the certificate file and read the certificate information. Expects am ASCII-armored PGP public key.
 */
public class InstMgrCertificateParseHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "certificate-parse";

    protected static final AttributeDefinition CERT_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.CERT_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRequired(true)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setReplyType(ModelType.OBJECT)
            .setRuntimeOnly()
            .setReplyValueType(ModelType.OBJECT)
            .addParameter(CERT_FILE)
            .build();

    InstMgrCertificateParseHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                    try (InputStream is = context.getAttachmentStream(CERT_FILE.resolveModelAttribute(context, operation).asInt())) {
                        final TrustCertificate tc = installationManager.parseCertificate(is);

                        final ModelNode entry = new ModelNode();
                        entry.get(CERT_KEY_ID).set(tc.getKeyID());
                        entry.get(CERT_FINGERPRINT).set(tc.getFingerprint());
                        entry.get(CERT_DESCRIPTION).set(tc.getDescription());
                        entry.get(CERT_STATUS).set(tc.getStatus());
                        context.getResult().set(entry);
                    }
                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
