/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.HTTP_CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.PROPERTY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Tomaz Cerar
 */
public class PropertyResource extends ConnectorChildResource {

    static final PathElement PATH = PathElement.pathElement(PROPERTY);

    static final SimpleAttributeDefinition VALUE = SimpleAttributeDefinitionBuilder.create(CommonAttributes.VALUE, ModelType.STRING)
            .setDefaultValue(null)
            .setRequired(false)
            .setAllowExpression(true)
            .build();
    static final PropertyResource INSTANCE_CONNECTOR = new PropertyResource(CONNECTOR);
    static final PropertyResource INSTANCE_HTTP_CONNECTOR = new PropertyResource(HTTP_CONNECTOR);


    private final String parent;

     protected PropertyResource(String parent) {
        super(PATH,
                RemotingExtension.getResourceDescriptionResolver(PROPERTY),
                new AddResourceConnectorRestartHandler(parent, PropertyResource.VALUE),
                new RemoveResourceConnectorRestartHandler(parent));
        this.parent = parent;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(VALUE, null, new RestartConnectorWriteAttributeHandler(parent, VALUE));
    }
}
