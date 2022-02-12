/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.jboss.as.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectMapAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
class ComplexRuntimeAttributesExtension implements Extension {

    static final String NAMESPACE = "urn:jboss:mbean.model.test";

    private static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, "test");

    private static final AttributeDefinition ONE_RUNTIME = new SimpleAttributeDefinitionBuilder("one", ModelType.LONG)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .setFlags(AttributeAccess.Flag.STORAGE_RUNTIME, AttributeAccess.Flag.COUNTER_METRIC)
            .build();

    private static final AttributeDefinition TWO_RUNTIME = new SimpleAttributeDefinitionBuilder("two", ModelType.STRING)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .setFlags(AttributeAccess.Flag.STORAGE_RUNTIME, AttributeAccess.Flag.COUNTER_METRIC)
            .build();

    private static final AttributeDefinition MAP_OF_MAPS = ObjectMapAttributeDefinition.Builder.of("map-of-maps",
                ObjectTypeAttributeDefinition.create(
                        "internal",
                        ONE_RUNTIME,
                        TWO_RUNTIME
                ).build()
            )
            .setRequired(false)
            .setFlags(AttributeAccess.Flag.STORAGE_RUNTIME)
            .build();

    @Override
    public void initialize(ExtensionContext context) {

        final SubsystemRegistration subsystem = context.registerSubsystem("test", ModelVersion.create(1));

        ResourceBuilder builder = ResourceBuilder.Factory.create(SUBSYSTEM_PATH, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddOperation(TestSubystemAdd.INSTANCE)
                .addMetric(MAP_OF_MAPS, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        ModelNode result = new ModelNode();
                        result.get("A", "one").set(1001L);
                        result.get("A", "two").set("Hello a");
                        result.get("B", "one").set(1002L);
                        result.get("B", "two").set("Hello b");
                        result.get("C", "one").set(1003L);
                        result.get("C", "two").set("Hello c");
                        context.getResult().set(result);
                    }
                });

        subsystem.registerSubsystemModel(builder.build());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping("test", NAMESPACE, new TestExtensionParser());
    }

    static class TestExtensionParser implements XMLElementReader<List<ModelNode>> {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            reader.next();
            ModelNode add = new ModelNode();
            add.get(OP).set(ADD);
            add.get(OP_ADDR).set(PathAddress.pathAddress(SUBSYSTEM_PATH).toModelNode());
            list.add(add);
        }
    }

    static class TestSubystemAdd extends AbstractAddStepHandler {
        static final TestSubystemAdd INSTANCE = new TestSubystemAdd();

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        }
    }
}
