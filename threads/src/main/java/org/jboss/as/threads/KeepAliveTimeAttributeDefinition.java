/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.threads;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLStreamException;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.AttributeConverter.DefaultAttributeConverter;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLExtendedStreamReader;

import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;

/**
 * {@link org.jboss.as.controller.AttributeDefinition} for a thread pool resource's keepalive-time attribute.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class KeepAliveTimeAttributeDefinition extends ObjectTypeAttributeDefinition {

    static final SimpleAttributeDefinition KEEPALIVE_TIME_TIME = new SimpleAttributeDefinitionBuilder(CommonAttributes.TIME, ModelType.LONG, false)
            .setAllowExpression(true)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .build();

    static final SimpleAttributeDefinition KEEPALIVE_TIME_UNIT = new SimpleAttributeDefinitionBuilder(CommonAttributes.UNIT, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new EnumValidator<TimeUnit>(TimeUnit.class, false, true))
            .build();

    static final RejectAttributeChecker TRANSFORMATION_CHECKER;

    static final AttributeConverter TIME_UNIT_TRANSFORMER = new TimeUnitAttributeConverter();

    static {
        Map<String, RejectAttributeChecker> fieldCheckers = new HashMap<String, RejectAttributeChecker>();
        fieldCheckers.put(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_TIME.getName(), RejectAttributeChecker.SIMPLE_EXPRESSIONS);
        fieldCheckers.put(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT.getName(), RejectAttributeChecker.SIMPLE_EXPRESSIONS);
        TRANSFORMATION_CHECKER = new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(fieldCheckers);
    }

    KeepAliveTimeAttributeDefinition() {
        super(Builder.of(CommonAttributes.KEEPALIVE_TIME, KEEPALIVE_TIME_TIME).setCorrector(new UnitConvertor()));
    }

    @Override
    protected void addValueTypeDescription(ModelNode node, String prefix, ResourceBundle bundle, final ResourceDescriptionResolver resolver, Locale locale) {
        // Swap out the resolver to use the threadpool.common keys
        ResourceDescriptionResolver override = new StandardResourceDescriptionResolver("threadpool.common", "", getClass().getClassLoader()) {
            @Override
            public ResourceBundle getResourceBundle(Locale locale) {
                return resolver.getResourceBundle(locale);
            }
        };
        super.addValueTypeDescription(node, prefix, bundle, override, locale);
    }

    public void parseAndSetParameter(final ModelNode operation, final XMLExtendedStreamReader reader) throws XMLStreamException {

        ModelNode model = new ModelNode();

        final int attrCount = reader.getAttributeCount();
        Set<Attribute> required;
        Namespace namespace = Namespace.forUri(reader.getNamespaceURI());
        if (namespace == Namespace.THREADS_1_0 || namespace == Namespace.THREADS_1_1) {
            required = EnumSet.of(Attribute.TIME, Attribute.UNIT);
        } else {
            required = EnumSet.of(Attribute.TIME);
        }
        for (int i = 0; i < attrCount; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case TIME: {
                    KEEPALIVE_TIME_TIME.parseAndSetParameter(value, model, reader);
                    break;
                }
                case UNIT: {
                    KEEPALIVE_TIME_UNIT.parseAndSetParameter(value, model, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        ParseUtils.requireNoContent(reader);

        operation.get(getName()).set(model);
    }

    private static class UnitConvertor implements ParameterCorrector {

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            if (newValue.isDefined()) {
                if (newValue.hasDefined(CommonAttributes.TIME) && newValue.get(CommonAttributes.TIME).getType() != ModelType.EXPRESSION) {
                    if (newValue.hasDefined(CommonAttributes.UNIT) && newValue.get(CommonAttributes.UNIT).getType() != ModelType.EXPRESSION) {
                        TimeUnit unit = TimeUnit.valueOf(newValue.get(CommonAttributes.UNIT).asString());
                        newValue.get(CommonAttributes.TIME).set(unit.toMillis(newValue.get(CommonAttributes.TIME).asLong()));
                        newValue.remove(CommonAttributes.UNIT);
                    }
                }
            }
            return newValue;
        }
    }

    private static class TimeUnitAttributeConverter extends DefaultAttributeConverter {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.hasDefined(CommonAttributes.TIME) && !attributeValue.hasDefined(CommonAttributes.UNIT)) {
                attributeValue.get(CommonAttributes.UNIT).set(TimeUnit.MILLISECONDS.name());
            }
        }
    }
}
