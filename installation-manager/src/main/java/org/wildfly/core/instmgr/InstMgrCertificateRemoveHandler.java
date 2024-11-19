/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.wildfly.core.instmgr.InstMgrConstants.CERT_KEY_ID;

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
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler to remove one of imported component certificates. The certificate is identified by it's ID (in hex format).
 * Once the certificate is removed, the user will have to re-import it to apply and updates including a component
 * signed by that certificate.
 */
public class InstMgrCertificateRemoveHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "certificate-remove";

    protected static final AttributeDefinition KEY_ID = SimpleAttributeDefinitionBuilder.create(CERT_KEY_ID, ModelType.STRING)
            .setStorageRuntime()
            .setRequired(true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setReplyType(ModelType.OBJECT)
            .setRuntimeOnly()
            .setReplyValueType(ModelType.OBJECT)
            .addParameter(KEY_ID)
            .build();

    InstMgrCertificateRemoveHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String keyId = KEY_ID.resolveModelAttribute(context, operation).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                    installationManager.revokeTrustedCertificate(keyId);
                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
