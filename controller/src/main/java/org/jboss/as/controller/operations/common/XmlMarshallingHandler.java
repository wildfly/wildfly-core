/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.dmr.ModelType;

/**
 * A {@link org.jboss.as.controller.OperationStepHandler} that can output a model in XML form
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class XmlMarshallingHandler extends AbstractXmlMarshallingHandler {

    private static final String OPERATION_NAME = ModelDescriptionConstants.READ_CONFIG_AS_XML_OPERATION;

    public static SimpleOperationDefinitionBuilder createOperationDefinitionBuilder() {
        return new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver())
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.STRING)
            .setReadOnly()
            .setRuntimeOnly();
    }

    public XmlMarshallingHandler(final ConfigurationPersister configPersister) {
        super(configPersister);
    }

    @Override
    protected void attachResult(OperationContext context, ByteArrayOutputStream baos) {
        String xml = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        context.getResult().set(xml.replace('\"', '\''));
    }
}
