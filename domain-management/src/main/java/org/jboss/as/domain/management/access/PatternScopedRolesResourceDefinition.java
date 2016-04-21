/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATTERN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATTERN_SCOPED_ROLE;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.PatternScopedConstraint;
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
 * scoped to a particular set of addresses or ObjectNames.
 *
 * @author Brian Stansberry
 */
public class PatternScopedRolesResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(PATTERN_SCOPED_ROLE);

    public static final SimpleAttributeDefinition BASE_ROLE =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.BASE_ROLE, ModelType.STRING)
                    .setRestartAllServices()
                    .build();


    public static final ListAttributeDefinition PATTERNS = SimpleListAttributeDefinition.Builder.of(ModelDescriptionConstants.PATTERNS,
            new SimpleAttributeDefinitionBuilder(PATTERN, ModelType.STRING)
                    .setAttributeMarshaller(new AttributeMarshaller() {
                        @Override
                        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                            writer.writeEmptyElement(Element.PATTERN.getLocalName());
                            writer.writeAttribute(Attribute.REGULAR_EXPRESSION.getLocalName(), resourceModel.asString());
                        }
                    }).build())
            .setAllowNull(true)
            .setWrapXmlList(false)
            .build();

    private final PatternScopedRoleAdd addHandler;
    private final PatternScopedRoleRemove removeHandler;
    private final PatternScopedRoleWriteAttributeHandler writeAttributeHandler;

    public PatternScopedRolesResourceDefinition(WritableAuthorizerConfiguration authorizerConfiguration) {
        super(PATH_ELEMENT, DomainManagementResolver.getResolver("core.access-control.pattern-scoped-role"));

        Map<String, PatternScopedConstraint> constraintMap = new HashMap<>();
        this.addHandler = new PatternScopedRoleAdd(constraintMap, authorizerConfiguration);
        this.removeHandler =  new PatternScopedRoleRemove(constraintMap, authorizerConfiguration);
        this.writeAttributeHandler = new PatternScopedRoleWriteAttributeHandler(constraintMap);
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

        resourceRegistration.registerReadWriteAttribute(BASE_ROLE, null, new ReloadRequiredWriteAttributeHandler(BASE_ROLE));
        resourceRegistration.registerReadWriteAttribute(PATTERNS, null, writeAttributeHandler);
    }
}
