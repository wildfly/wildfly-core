/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.model.jvm;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.host.controller.descriptions.HostEnvironmentResourceDefinition;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for JVM configuration resources.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JvmResourceDefinition extends SimpleResourceDefinition {

    public static final JvmResourceDefinition GLOBAL = new JvmResourceDefinition(false);

    public static final JvmResourceDefinition SERVER = new JvmResourceDefinition(true);

    private final boolean server;

    protected JvmResourceDefinition(boolean server) {
        super(new Parameters(PathElement.pathElement(ModelDescriptionConstants.JVM),
            new StandardResourceDescriptionResolver(ModelDescriptionConstants.JVM, HostEnvironmentResourceDefinition.class.getPackage().getName() + ".LocalDescriptions",
            HostEnvironmentResourceDefinition.class.getClassLoader(), true, false))
            .setAddHandler(new JVMAddHandler(JvmAttributes.getAttributes(server)))
            .setRemoveHandler(JVMRemoveHandler.INSTANCE)
            .setMaxOccurs(server ? 1 : Integer.MAX_VALUE)
            .setMinOccurs(0));
        this.server = server;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : JvmAttributes.getAttributes(server)) {
            resourceRegistration.registerReadWriteAttribute(attr, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(JVMOptionAddHandler.DEFINITION, JVMOptionAddHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(JVMOptionRemoveHandler.DEFINITION, JVMOptionRemoveHandler.INSTANCE);
    }
}
