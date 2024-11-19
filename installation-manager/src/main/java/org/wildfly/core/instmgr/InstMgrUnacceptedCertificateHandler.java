/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CERTIFICATE_INFO;
import static org.wildfly.core.instmgr.InstMgrConstants.CERTIFICATE_CONTENT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_DESCRIPTION;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_FINGERPRINT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_KEY_ID;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_STATUS;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;

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
 * Operation handler to get the certificates potentially required by the update or revert operation. Those certificates
 * are listed by the channel and may be used to sign the components included in the channel.
 */
public class InstMgrUnacceptedCertificateHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "unaccepted-certificates";

    protected static final AttributeDefinition OFFLINE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.OFFLINE, ModelType.BOOLEAN)
            .setStorageRuntime()
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setReplyType(ModelType.OBJECT)
            .setRuntimeOnly()
            .setReplyValueType(ModelType.OBJECT)
            .addParameter(OFFLINE)
            .build();

    InstMgrUnacceptedCertificateHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    final Path serverHome = imService.getHomeDir();
                    final boolean offline = OFFLINE.resolveModelAttribute(context, operation).asBoolean();
                    final MavenOptions mavenOptions = new MavenOptions(null, offline);
                    final InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                    final Collection<InputStream> downloadedCerts = installationManager.downloadRequiredCertificates();

                    final ModelNode mCertificates = new ModelNode().addEmptyList();
                    for (InputStream is : downloadedCerts) {
                        final ModelNode mCert = new ModelNode();
                        try (BufferedInputStream bis = new BufferedInputStream(is)) {
                            ByteArrayOutputStream buf = new ByteArrayOutputStream();
                            for (int result = bis.read(); result != -1; result = bis.read()) {
                                buf.write((byte) result);
                            }

                            final String certContent = buf.toString(StandardCharsets.UTF_8);
                            mCert.get(CERTIFICATE_CONTENT).set(certContent);
                            try (final ByteArrayInputStream bais = new ByteArrayInputStream(certContent.getBytes(StandardCharsets.UTF_8))) {
                                final TrustCertificate tc = installationManager.parseCertificate(bais);
                                final ModelNode info = new ModelNode();
                                info.get(CERT_KEY_ID).set(tc.getKeyID());
                                info.get(CERT_FINGERPRINT).set(tc.getFingerprint());
                                info.get(CERT_DESCRIPTION).set(tc.getDescription());
                                info.get(CERT_STATUS).set(tc.getStatus());
                                context.getResult().set(info);

                                mCert.get(CERTIFICATE_INFO).set(info);
                            }
                        }


                        mCertificates.add(mCert);
                    }

                    final ModelNode result = context.getResult();
                    result.set(mCertificates);
                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
