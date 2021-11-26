/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.expressions.module;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;


/**
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestSecureExpressionsExtension implements Extension {

    private static final Logger log = Logger.getLogger(TestSecureExpressionsExtension.class.getPackage().getName());

    public static final String MODULE_NAME = "test.secure.expressions.module";
    public static final String SUBSYSTEM_NAME = "test-secure-expressions";


    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);

    public static final AttributeDefinition ATTR = SimpleAttributeDefinitionBuilder.create("attribute", ModelType.STRING)
            .setAllowExpression(true)
            .setRequired(true)
            .setRestartAllServices()
            .build();

    public static final AttributeDefinition PARAM_EXPRESSION = SimpleAttributeDefinitionBuilder.create("expression", ModelType.STRING, false)
            .setAllowExpression(true)
            .build();

    public static final OperationDefinition RESOLVE = new SimpleOperationDefinitionBuilder("resolve", new NonResolvingResourceDescriptionResolver())
            .addParameter(PARAM_EXPRESSION)
            .build();

    public static final AttributeDefinition PARAM_SYS_PROP = SimpleAttributeDefinitionBuilder.create("system-property", ModelType.STRING, false)
            .build();

    public static final OperationDefinition READ_SYS_PROP = new SimpleOperationDefinitionBuilder("read-system-property", new NonResolvingResourceDescriptionResolver())
            .addParameter(PARAM_SYS_PROP)
            .build();

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        registration.registerSubsystemModel(new ResourceDescription());
        registration.registerXMLElementWriter(new Parser());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        //Don't need a parser, just register a dummy writer in the initialize() method
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Parser.NAMESPACE, new Parser());
    }

    public static final class Parser extends PersistentResourceXMLParser {

        private static final String NAMESPACE = "urn:jboss:domain:test-secure-expressions:1.0";

        @Override
        public PersistentResourceXMLDescription getParserDescription() {
            return builder(PATH, NAMESPACE)
                    .addAttribute(ATTR)
                    .build();
        }

    }

    public static class ResourceDescription extends PersistentResourceDefinition {

        public ResourceDescription() {
            super(new SimpleResourceDefinition.Parameters(PATH, new NonResolvingResourceDescriptionResolver())
                    .setAddHandler(new AbstractAddStepHandler(ATTR))
                    .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(RESOLVE, new ResolveHandler());
            resourceRegistration.registerOperationHandler(READ_SYS_PROP, new ReadSystemPropertyHandler());
        }

        @Override
        public Collection<AttributeDefinition> getAttributes() {
            return Collections.singleton(ATTR);
        }

    }

    public static class ResolveHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            PARAM_EXPRESSION.validateOperation(operation);
            log.fine("Resolving " + operation.get(PARAM_EXPRESSION.getName()) + " for " + context.getCurrentAddress());
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation)
                        throws OperationFailedException {
                    context.getResult().set(context.resolveExpressions(operation.get(PARAM_EXPRESSION.getName())));
                }
            }, OperationContext.Stage.RUNTIME);
        }

    }

    public static class ReadSystemPropertyHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            String prop = operation.get(PARAM_SYS_PROP.getName()).asString();
            log.fine("Checking property " + prop);
            String value = System.getProperty(prop);
            ModelNode result = context.getResult();
            if (value != null) {
                result.set(value);
            }
        }

    }
}
