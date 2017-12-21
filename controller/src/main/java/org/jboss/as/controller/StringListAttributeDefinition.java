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
import java.util.LinkedList;
import java.util.List;

import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public final class StringListAttributeDefinition extends PrimitiveListAttributeDefinition {

    private StringListAttributeDefinition(Builder builder) {
        super(builder, ModelType.STRING);
    }

    public List<String> unwrap(final ExpressionResolver context, final ModelNode model) throws OperationFailedException {
        ModelNode value = resolveModelAttribute(context, model);
        if (value.isDefined()) {
            return unwrapValue(context, value);
        }else {
            return Collections.emptyList();
        }
    }

    public static List<String> unwrapValue(final ExpressionResolver context, final ModelNode model) throws OperationFailedException {
        if (!model.isDefined()) {
            return Collections.emptyList();
        }
        List<String> result = new LinkedList<>();
        for (ModelNode p : model.asList()) {
            result.add(context.resolveExpressions(p).asString());
        }
        return result;
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        handleCapabilityRequirements(context, resource, attributeValue, false);
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        handleCapabilityRequirements(context, resource, attributeValue, true);
    }

    private void handleCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue, boolean remove) {
        CapabilityReferenceRecorder refRecorder = getReferenceRecorder();
        if (refRecorder != null && attributeValue.isDefined()) {
            List<ModelNode> valueList = attributeValue.asList();
            String[] attributeValues = new String[valueList.size()];
            int position = 0;
            for (ModelNode current : valueList) {
                if (current.isDefined() == false || current.getType().equals(ModelType.EXPRESSION)) {
                    return;
                }
                attributeValues[position++] = current.asString();
            }
            if (remove) {
                refRecorder.removeCapabilityRequirements(context, resource, getName(), attributeValues);
            } else {
                refRecorder.addCapabilityRequirements(context, resource, getName(), attributeValues);
            }
        }
    }

    public static class Builder extends ListAttributeDefinition.Builder<Builder, StringListAttributeDefinition> {

        public static Builder of(final String name) {
            return new Builder(name);
        }

        public Builder(final String name) {
            super(name);
            setAttributeParser(AttributeParser.STRING_LIST);
            setAttributeMarshaller(AttributeMarshaller.STRING_LIST);
            setElementValidator(new ModelTypeValidator(ModelType.STRING));
        }

        public Builder(final StringListAttributeDefinition basic) {
            super(basic);
            setAttributeParser(AttributeParser.STRING_LIST);
            setAttributeMarshaller(AttributeMarshaller.STRING_LIST);
        }

        @Override
        public StringListAttributeDefinition build() {
            return new StringListAttributeDefinition(this);
        }

    }

}
