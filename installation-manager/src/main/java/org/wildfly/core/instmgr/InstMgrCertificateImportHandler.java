/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;
import static org.wildfly.core.instmgr.InstMgrConstants.CERTIFICATE_CONTENT;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler to import a certificate used to validate components installed during updates.
 * The certificate has to be an ASCII-armored PGP public certificate.
 */
public class InstMgrCertificateImportHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "certificate-import";

    protected static final AttributeDefinition CERT_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.CERT_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRequired(false)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .build();
    protected static final AttributeDefinition CERT_CONTENT = SimpleAttributeDefinitionBuilder.create(CERTIFICATE_CONTENT, ModelType.STRING)
            .setStorageRuntime()
            .setRequired(false)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setReplyType(ModelType.OBJECT)
            .setRuntimeOnly()
            .setReplyValueType(ModelType.OBJECT)
            .addParameter(CERT_FILE)
            .build();

    InstMgrCertificateImportHandler(InstMgrService imService, InstallationManagerFactory imf) {
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

                    final Integer streamIndex = CERT_FILE.resolveModelAttribute(context, operation).asIntOrNull();
                    final String certificateContent = CERT_CONTENT.resolveModelAttribute(context, operation).asStringOrNull();

                    if (streamIndex != null && certificateContent != null) {
                        throw InstMgrLogger.ROOT_LOGGER.mutuallyExclusiveOptions(CERT_FILE.getName(), CERT_CONTENT.getName());
                    }

                    if (streamIndex != null) {
                        try (InputStream is = context.getAttachmentStream(streamIndex)) {
                            installationManager.acceptTrustedCertificates(is);
                        }
                    } else if (certificateContent != null) {
                        try (InputStream is = new ByteArrayInputStream(certificateContent.getBytes(StandardCharsets.UTF_8))) {
                            installationManager.acceptTrustedCertificates(is);
                        }
                    } else {
                        // throw exception
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
