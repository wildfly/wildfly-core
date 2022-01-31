/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
