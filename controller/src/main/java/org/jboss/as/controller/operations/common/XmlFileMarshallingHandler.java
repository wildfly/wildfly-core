/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 *
 * A {@link org.jboss.as.controller.OperationStepHandler} that can output a model in XML file.
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public class XmlFileMarshallingHandler extends AbstractXmlMarshallingHandler {

    private static final String OPERATION_NAME = ModelDescriptionConstants.READ_CONFIG_AS_XML_FILE_OPERATION;

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver())
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyParameters(new SimpleAttributeDefinitionBuilder(UUID, ModelType.STRING, false).build())
            .setReadOnly()
            .setRuntimeOnly()
            .setStability(Stability.COMMUNITY)
            .build();

    public XmlFileMarshallingHandler(final ConfigurationPersister configPersister) {
        super(configPersister);
    }

    @Override
    protected void attachResult(OperationContext context, ByteArrayOutputStream baos) {
        String uuid = context.attachResultStream("application/xml", new ByteArrayInputStream(baos.toByteArray()));
        context.getResult().get(UUID).set(uuid);
    }

}
