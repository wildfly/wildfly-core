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

package org.jboss.as.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Represents simple key=value map equivalent of java.util.Map<String,String>()
 *
 * @author Jason T. Greene
 * @author Tomaz Cerar<
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
//todo maybe replace with SimpleMapAttributeDefinition?
public final class PropertiesAttributeDefinition extends MapAttributeDefinition {

    private PropertiesAttributeDefinition(final Builder builder) {
        super(builder);
    }

    @Override
    protected void addValueTypeDescription(ModelNode node, ResourceBundle bundle) {
        setValueType(node);
    }

    @Override
    protected void addAttributeValueTypeDescription(ModelNode node, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        setValueType(node);
    }

    @Override
    protected void addOperationParameterValueTypeDescription(ModelNode node, String operationName, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        setValueType(node);
    }

    void setValueType(ModelNode node) {
        node.get(ModelDescriptionConstants.VALUE_TYPE).set(ModelType.STRING);
        if (isAllowExpression()) {
            node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(ModelNode.TRUE);
        }
    }

    public Map<String, String> unwrap(final ExpressionResolver context, final ModelNode model) throws OperationFailedException {
        ModelNode value = resolveModelAttribute(context, model);
        if (value.isDefined()) {
            return unwrapModel(context, value);
        } else {
            return Collections.emptyMap();
        }
    }

    public static Map<String, String> unwrapModel(final ExpressionResolver context, final ModelNode model) throws OperationFailedException {
        if (!model.isDefined()) return Collections.emptyMap();
        Map<String, String> props = new HashMap<>();
        for (Property p : model.asPropertyList()) {
            // TODO this is wasteful if we are called from unwrap(...) as the passed in model is already fully resolved
            ModelNode value = context.resolveExpressions(p.getValue());
            props.put(p.getName(), value.isDefined() ? value.asString() : null);
        }
        return props;
    }

    public static class Builder extends MapAttributeDefinition.Builder<Builder, PropertiesAttributeDefinition> {
        //for backward compatibility, until we get new core out and used by wildfly full.
        private boolean xmlNameExplicitlySet = false;

        public Builder(final String name, boolean optional) {
            super(name, optional);
        }

        public Builder(final PropertiesAttributeDefinition basis) {
            super(basis);
        }

        public Builder(final MapAttributeDefinition basis) {
            super(basis);
        }

        /**
         * @deprecated use setParser(new AttributeParser.PropertiesParsers(wrapper)
         */
        @Override
        public Builder setXmlName(String xmlName) {
            this.xmlNameExplicitlySet = true;
            return super.setXmlName(xmlName);
        }

        @Override
        public PropertiesAttributeDefinition build() {
            if (elementValidator == null) {
                elementValidator = new ModelTypeValidator(ModelType.STRING);
            }
            String xmlName = getXmlName();
            String elementName = getName().equals(xmlName) ? null : xmlName;
            if (getAttributeMarshaller() == null) {
                setAttributeMarshaller(new AttributeMarshallers.PropertiesAttributeMarshaller(null, xmlNameExplicitlySet ? xmlName : elementName, true));
            }
            if (getParser() == null) {
                setAttributeParser(new AttributeParsers.PropertiesParser(null, xmlNameExplicitlySet ? xmlName : elementName, true));
            }

            return new PropertiesAttributeDefinition(this);
        }
    }
}
