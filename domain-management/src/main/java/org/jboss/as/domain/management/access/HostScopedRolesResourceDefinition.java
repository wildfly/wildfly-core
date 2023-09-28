/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.constraint.HostEffectConstraint;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for an administrative role that is
 * scoped to a particular set of hosts.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class HostScopedRolesResourceDefinition extends ScopedRoleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(HOST_SCOPED_ROLE);

    public static final ListAttributeDefinition HOSTS = SimpleListAttributeDefinition.Builder.of(ModelDescriptionConstants.HOSTS,
                new SimpleAttributeDefinitionBuilder(HOST, ModelType.STRING)
                        .setAttributeMarshaller(new AttributeMarshaller() {
                            @Override
                            public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                                writer.writeEmptyElement(Element.HOST.getLocalName());
                                writer.writeAttribute(Attribute.NAME.getLocalName(), resourceModel.asString());
                            }
                        }).build())
            .setRequired(false)
            .setWrapXmlList(false)
            .build();

    private final OperationStepHandler addHandler;
    private final OperationStepHandler removeHandler;
    private final OperationStepHandler writeAttributeHandler;

    public HostScopedRolesResourceDefinition(WritableAuthorizerConfiguration authorizerConfiguration) {
        super(PATH_ELEMENT, DomainManagementResolver.getResolver("core.access-control.host-scoped-role"), authorizerConfiguration);

        Map<String, HostEffectConstraint> constraintMap = new HashMap<String, HostEffectConstraint>();
        this.addHandler = new HostScopedRoleAdd(constraintMap, authorizerConfiguration);
        this.removeHandler =  new HostScopedRoleRemove(constraintMap, authorizerConfiguration);
        this.writeAttributeHandler = new HostScopedRoleWriteAttributeHandler(constraintMap);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        registerAddOperation(resourceRegistration, addHandler);
        OperationDefinition removeDef = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.REMOVE, getResourceDescriptionResolver())
                .build();
        resourceRegistration.registerOperationHandler(removeDef, removeHandler);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        resourceRegistration.registerReadWriteAttribute(HOSTS, null, writeAttributeHandler);
    }
}
