/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.scanner.api.DeploymentScanner;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar
 * @created 25.1.12 17:24
 */
public class DeploymentScannerDefinition extends SimpleResourceDefinition {

    static final String PATH_MANAGER_CAPABILITY = "org.wildfly.management.path-manager";

    static final RuntimeCapability<Void> SCANNER_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.management.deployment-scanner", true, DeploymentScanner.class)
            .addRequirements(PATH_MANAGER_CAPABILITY)
            .build();

    private final PathManager pathManager;

    DeploymentScannerDefinition(final PathManager pathManager) {
        super(new Parameters(DeploymentScannerExtension.SCANNERS_PATH,
                DeploymentScannerExtension.getResourceDescriptionResolver("deployment.scanner"))
                .setAddHandler(new DeploymentScannerAdd(pathManager))
                .setRemoveHandler(DeploymentScannerRemove.INSTANCE)
                .setCapabilities(SCANNER_CAPABILITY)
        );
        this.pathManager = pathManager;
    }

    protected static final SimpleAttributeDefinition NAME =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.NAME, ModelType.STRING, false)
                    .setXmlName(CommonAttributes.NAME)
                    .setAllowExpression(false)
                    .setValidator(new StringLengthValidator(1))
                    .build();

    protected static final SimpleAttributeDefinition PATH =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.PATH, ModelType.STRING, false)
                    .setXmlName(CommonAttributes.PATH)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
                    .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
                    .build();
    protected static final SimpleAttributeDefinition RELATIVE_TO =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.RELATIVE_TO, ModelType.STRING, true)
                    .setXmlName(CommonAttributes.RELATIVE_TO)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
                    .setCapabilityReference("org.wildfly.management.path")
                    .build();
    protected static final SimpleAttributeDefinition SCAN_ENABLED =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.SCAN_ENABLED, ModelType.BOOLEAN, true)
                    .setXmlName(CommonAttributes.SCAN_ENABLED)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.TRUE)
                    .build();
    protected static final SimpleAttributeDefinition SCAN_INTERVAL =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.SCAN_INTERVAL, ModelType.INT, true)
                    .setXmlName(CommonAttributes.SCAN_INTERVAL)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.ZERO)
                    .build();
    protected static final SimpleAttributeDefinition AUTO_DEPLOY_ZIPPED =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTO_DEPLOY_ZIPPED, ModelType.BOOLEAN, true)
                    .setXmlName(CommonAttributes.AUTO_DEPLOY_ZIPPED)
                    .setDefaultValue(ModelNode.TRUE)
                    .setAllowExpression(true)
                    .build();
    protected static final SimpleAttributeDefinition AUTO_DEPLOY_EXPLODED =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTO_DEPLOY_EXPLODED, ModelType.BOOLEAN, true)
                    .setXmlName(CommonAttributes.AUTO_DEPLOY_EXPLODED)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();

    protected static final SimpleAttributeDefinition AUTO_DEPLOY_XML =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTO_DEPLOY_XML, ModelType.BOOLEAN, true)
                    .setXmlName(CommonAttributes.AUTO_DEPLOY_XML)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.TRUE)
                    .build();

    protected static final SimpleAttributeDefinition DEPLOYMENT_TIMEOUT =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.DEPLOYMENT_TIMEOUT, ModelType.LONG, true)
                    .setXmlName(CommonAttributes.DEPLOYMENT_TIMEOUT)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode().set(600))
                    .build();

    protected static final SimpleAttributeDefinition RUNTIME_FAILURE_CAUSES_ROLLBACK =
            new SimpleAttributeDefinitionBuilder(CommonAttributes.RUNTIME_FAILURE_CAUSES_ROLLBACK, ModelType.BOOLEAN, true)
                    .setXmlName(CommonAttributes.RUNTIME_FAILURE_CAUSES_ROLLBACK)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();

    protected static final SimpleAttributeDefinition[] ALL_ATTRIBUTES = {PATH,RELATIVE_TO,SCAN_ENABLED,SCAN_INTERVAL,AUTO_DEPLOY_EXPLODED,AUTO_DEPLOY_XML,AUTO_DEPLOY_ZIPPED,DEPLOYMENT_TIMEOUT,RUNTIME_FAILURE_CAUSES_ROLLBACK};

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        //resourceRegistration.registerReadOnlyAttribute(NAME, null);
        resourceRegistration.registerReadWriteAttribute(PATH, null, new WritePathAttributeHandler(pathManager));
        resourceRegistration.registerReadWriteAttribute(RELATIVE_TO, null, new WriteRelativeToAttributeHandler(pathManager));
        UpdateScannerWriteAttributeHandler commonHandler = new UpdateScannerWriteAttributeHandler();
        resourceRegistration.registerReadWriteAttribute(SCAN_ENABLED, null, commonHandler);
        resourceRegistration.registerReadWriteAttribute(SCAN_INTERVAL, null, commonHandler);
        resourceRegistration.registerReadWriteAttribute(AUTO_DEPLOY_ZIPPED, null, commonHandler);
        resourceRegistration.registerReadWriteAttribute(AUTO_DEPLOY_EXPLODED, null, commonHandler);
        resourceRegistration.registerReadWriteAttribute(AUTO_DEPLOY_XML, null, commonHandler);
        resourceRegistration.registerReadWriteAttribute(DEPLOYMENT_TIMEOUT, null, commonHandler);
        resourceRegistration.registerReadWriteAttribute(RUNTIME_FAILURE_CAUSES_ROLLBACK, null, commonHandler);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(FileSystemDeploymentScanHandler.DEFINITION, FileSystemDeploymentScanHandler.INSTANCE);
        PathInfoHandler.registerOperation(resourceRegistration,
                PathInfoHandler.Builder.of(pathManager).addAttribute(PATH, RELATIVE_TO).build());
    }

}
