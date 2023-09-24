/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.security.manager.logging.SecurityManagerLogger;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public class SecurityManagerExtensionTransformerRegistration implements ExtensionTransformerRegistration {
    private static final ModelVersion EAP_7_0_0_MODEL_VERSION = ModelVersion.create(2, 0, 0);

    @Override
    public String getSubsystemName() {
        return Constants.SUBSYSTEM_NAME;
    }

    /**
     * Registers the transformers for JBoss EAP 7.0.0.
     *
     * @param subsystemRegistration contains data about the subsystem registration
     */
    @Override
    public void registerTransformers(SubsystemTransformerRegistration subsystemRegistration) {
        ResourceTransformationDescriptionBuilder builder = ResourceTransformationDescriptionBuilder.Factory.createSubsystemInstance();
        builder.addChildResource(DeploymentPermissionsResourceDefinition.DEPLOYMENT_PERMISSIONS_PATH).
                getAttributeBuilder().addRejectCheck(new RejectAttributeChecker.DefaultRejectAttributeChecker() {

            @Override
            protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode value, TransformationContext context) {
                // reject the maximum set if it is defined and empty as that would result in complete incompatible policies
                // being used in nodes running earlier versions of the subsystem.
                if (value.isDefined() && value.asList().isEmpty()) { return true; }
                return false;
            }

            @Override
            public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
                return SecurityManagerLogger.ROOT_LOGGER.rejectedEmptyMaximumSet();
            }
        }, DeploymentPermissionsResourceDefinition.MAXIMUM_PERMISSIONS);
        TransformationDescription.Tools.register(builder.build(), subsystemRegistration, EAP_7_0_0_MODEL_VERSION);
    }
}
