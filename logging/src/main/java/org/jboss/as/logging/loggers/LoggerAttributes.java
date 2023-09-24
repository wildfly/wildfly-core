/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.loggers;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.handlers.LogHandlerListAttributeDefinition;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggerAttributes {

    static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING)
            .setAllowExpression(false)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setCapabilityReference(Capabilities.LOGGER_HANDLER_REFERENCE_RECORDER)
            .build();

    public static final LogHandlerListAttributeDefinition HANDLERS = LogHandlerListAttributeDefinition.Builder.of("handlers", HANDLER)
            .setAllowDuplicates(false)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    public static final PropertyAttributeDefinition FILTER_SPEC = PropertyAttributeDefinition.Builder.of("filter-spec", ModelType.STRING, true)
            .addAlternatives("filter")
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setCapabilityReference(Capabilities.LOGGER_FILTER_REFERENCE_RECORDER)
            .build();
}
