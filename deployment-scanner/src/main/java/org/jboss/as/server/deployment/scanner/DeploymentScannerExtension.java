/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger.ROOT_LOGGER;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;

/**
 * @author Emanuel Muckenhuber
 */
public class DeploymentScannerExtension implements Extension {

    public static final String SUBSYSTEM_NAME = CommonAttributes.DEPLOYMENT_SCANNER;
    protected static final PathElement SCANNERS_PATH = PathElement.pathElement("scanner");
    static final String DEFAULT_SCANNER_NAME = "default"; // we actually need a scanner name to make it addressable
    private static final String RESOURCE_NAME = DeploymentScannerExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final int MANAGEMENT_API_MAJOR_VERSION = 2;
    private static final int MANAGEMENT_API_MINOR_VERSION = 0;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);

    static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, DeploymentScannerExtension.class.getClassLoader(), true, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(ExtensionContext context) {
        ROOT_LOGGER.debug("Initializing Deployment Scanner Extension");

        if (context.getProcessType().isHostController()) {
            throw DeploymentScannerLogger.ROOT_LOGGER.deploymentScannerNotForDomainMode();
        }

        final SubsystemRegistration subsystem = context.registerSubsystem(CommonAttributes.DEPLOYMENT_SCANNER, CURRENT_VERSION);
        subsystem.registerXMLElementWriter(DeploymentScannerParser_2_0::new);

        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new DeploymentScannerSubsystemDefinition());
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        final ManagementResourceRegistration scanner = registration.registerSubModel(new DeploymentScannerDefinition(context.getPathManager()));
        if (context.getProcessType().isServer()) {
            final ResolvePathHandler resolvePathHandler = ResolvePathHandler.Builder.of(context.getPathManager())
                    .setRelativeToAttribute(DeploymentScannerDefinition.RELATIVE_TO)
                    .setPathAttribute(DeploymentScannerDefinition.PATH)
                    .build();
            scanner.registerOperationHandler(resolvePathHandler.getOperationDefinition(), resolvePathHandler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.DEPLOYMENT_SCANNER_1_0.getUriString(), DeploymentScannerParser_1_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.DEPLOYMENT_SCANNER_1_1.getUriString(), DeploymentScannerParser_1_1::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.DEPLOYMENT_SCANNER_2_0.getUriString(), DeploymentScannerParser_2_0::new);

    }

}
