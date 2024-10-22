/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.wildfly.core.instmgr.InstMgrConstants.CERT_FILE;

import java.io.FileOutputStream;
import java.io.InputStream;
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
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;
import org.xnio.streams.Streams;

/**
 * Operation handler to get the history of the installation manager changes, either artifacts or configuration metadata as
 * channel changes.
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
                    final Path controllerTempDir = imService.getControllerTempDir();
                    final boolean offline = OFFLINE.resolveModelAttribute(context, operation).asBoolean();
                    final MavenOptions mavenOptions = new MavenOptions(null, offline);
                    final InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                    final Collection<InputStream> downloadedCerts = installationManager.downloadRequiredCertificates();

                    final ModelNode mCertificates = new ModelNode().addEmptyList();
                    int i=0;
                    for (InputStream is : downloadedCerts) {
                        final Path certFile = controllerTempDir.resolve("required-cert-" + i++ + ".crt");
                        try (FileOutputStream fos = new FileOutputStream(certFile.toFile())) {
                            Streams.copyStream(is, fos);
                            is.close();
                        }

                        final ModelNode entry = new ModelNode();
                        entry.get(CERT_FILE).set(certFile.toAbsolutePath().toString());
                        mCertificates.add(entry);
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
