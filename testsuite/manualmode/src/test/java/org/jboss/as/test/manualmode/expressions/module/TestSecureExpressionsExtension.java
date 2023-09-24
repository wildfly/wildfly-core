/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.expressions.module;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
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
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
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
    public static final String INPUT_PROPERTY = "test.secure.expression.input";
    public static final String OUTPUT_PROPERTY = "test.secure.expression.output";


    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);

    public static final AttributeDefinition ATTR = SimpleAttributeDefinitionBuilder.create("attribute", ModelType.STRING)
            .setAllowExpression(true)
            .setRequired(true)
            .setRestartAllServices()
            .build();

    public static final AttributeDefinition PARAM_EXPRESSION = SimpleAttributeDefinitionBuilder.create("expression", ModelType.STRING, false)
            .setAllowExpression(true)
            .build();

    public static final OperationDefinition RESOLVE = new SimpleOperationDefinitionBuilder("resolve", NonResolvingResourceDescriptionResolver.INSTANCE)
            .addParameter(PARAM_EXPRESSION)
            .build();

    public static final AttributeDefinition PARAM_SYS_PROP = SimpleAttributeDefinitionBuilder.create("system-property", ModelType.STRING, false)
            .build();

    public static final OperationDefinition READ_SYS_PROP = new SimpleOperationDefinitionBuilder("read-system-property", NonResolvingResourceDescriptionResolver.INSTANCE)
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
            super(new SimpleResourceDefinition.Parameters(PATH, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(new AddHandler())
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

    public static final class AddHandler extends AbstractBoottimeAddStepHandler {

        private AddHandler() {
            super(ATTR);
        }

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

            // Clean up any left behind trash
            System.clearProperty(OUTPUT_PROPERTY);

            System.setProperty(INPUT_PROPERTY, operation.get(ATTR.getName()).asString());

            if (context.isNormalServer()) {
                context.addStep(new AbstractDeploymentChainStep() {
                    @Override
                    protected void execute(DeploymentProcessorTarget processorTarget) {
                        processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_EE_FUNCTIONAL_RESOLVERS, new Processor());
                    }
                }, OperationContext.Stage.RUNTIME);
            }
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

    public static final class Processor implements DeploymentUnitProcessor {

        @Override
        public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

            String secureExpressionInput = System.getProperty(INPUT_PROPERTY);
            if (secureExpressionInput == null) throw new IllegalStateException("null " + INPUT_PROPERTY);

            // Sanity check
            String secureExpressionOutput = System.getProperty(OUTPUT_PROPERTY);
            if (secureExpressionOutput != null) throw new IllegalStateException(OUTPUT_PROPERTY + " is already defined");

            log.fine("Resolving " + secureExpressionInput + " for " + phaseContext.getDeploymentUnit().getName());

            List<Function<String, String>> functions = phaseContext.getDeploymentUnit().getAttachment(Attachments.DEPLOYMENT_EXPRESSION_RESOLVERS);
            for (Function<String, String> funct : functions) {
                secureExpressionOutput = funct.apply(secureExpressionInput);
                if (secureExpressionOutput != null) {
                    System.setProperty(OUTPUT_PROPERTY, secureExpressionOutput);
                }
            }
        }

        @Override
        public void undeploy(DeploymentUnit context) {
            System.clearProperty(OUTPUT_PROPERTY);
        }
    }
}
