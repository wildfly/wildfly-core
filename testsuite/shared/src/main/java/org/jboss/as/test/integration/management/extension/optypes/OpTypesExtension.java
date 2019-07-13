/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.test.integration.management.extension.optypes;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Extension whose subsystem exposes different types of operations besides the typical ones.
 * For testing of handling of such operations.
 *
 * @author Brian Stansberry
 */
@SuppressWarnings("deprecation")
public class OpTypesExtension implements Extension {

    public static final String EXTENSION_NAME = "org.wildfly.extension.operation-types-test";
    public static final String SUBSYSTEM_NAME = "operation-types-test";

    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser("urn:wildfly:extension:operation-types-test:1.0");

    private static final OperationDefinition PUBLIC = new SimpleOperationDefinitionBuilder("public", NonResolvingResourceDescriptionResolver.INSTANCE)
            .build();

    private static final OperationDefinition HIDDEN = new SimpleOperationDefinitionBuilder("hidden", NonResolvingResourceDescriptionResolver.INSTANCE)
            .withFlags(OperationEntry.Flag.HIDDEN)
            .build();
    private static final OperationDefinition DOMAIN_HIDDEN  = new SimpleOperationDefinitionBuilder("domain-hidden", NonResolvingResourceDescriptionResolver.INSTANCE)
            .withFlags(OperationEntry.Flag.HIDDEN)
            .setRuntimeOnly()
            .build();

    private static final OperationDefinition PRIVATE = new SimpleOperationDefinitionBuilder("private", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .build();
    private static final OperationDefinition DOMAIN_PRIVATE = new SimpleOperationDefinitionBuilder("domain-private", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setRuntimeOnly()
            .build();
    private static final OperationDefinition DOMAIN_SERVER_PRIVATE = new SimpleOperationDefinitionBuilder("domain-private", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .setRuntimeOnly()
            .build();

    private static final AttributeDefinition TARGET_HOST = SimpleAttributeDefinitionBuilder.create(HOST, ModelType.STRING, true).build();

    private static final OperationDefinition RUNTIME_ONLY = new SimpleOperationDefinitionBuilder("runtime-only", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setParameters(TARGET_HOST)
            .setRuntimeOnly()
            .build();
    private static final OperationDefinition DOMAIN_RUNTIME_PRIVATE = new SimpleOperationDefinitionBuilder("domain-runtime-private", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setRuntimeOnly()
            .build();
    private static final OperationDefinition DOMAIN_SERVER_RUNTIME_PRIVATE = new SimpleOperationDefinitionBuilder("domain-runtime-private", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setPrivateEntry()
            .setRuntimeOnly()
            .build();
    private static final OperationDefinition RUNTIME_READ_ONLY = new SimpleOperationDefinitionBuilder("runtime-read-only", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    private static final AttributeDefinition RUNTIME_ONLY_ATTR = SimpleAttributeDefinitionBuilder.create("runtime-only-attr", ModelType.BOOLEAN)
            .setRequired(false)
            .setStorageRuntime()
            .build();
    private static final AttributeDefinition METRIC = SimpleAttributeDefinitionBuilder.create("metric", ModelType.BOOLEAN)
            .setUndefinedMetricValue(ModelNode.FALSE)
            .build();

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        subsystem.setHostCapable();
        subsystem.registerSubsystemModel(new OperationTypesSubsystemResourceDefinition(context.getProcessType()));
        subsystem.registerXMLElementWriter(PARSER);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PARSER.getNamespace(), PARSER);
    }

    private static class OperationTypesSubsystemResourceDefinition extends SimpleResourceDefinition {

        private final ProcessType processType;

        private OperationTypesSubsystemResourceDefinition(ProcessType processType) {
            super(new Parameters(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME), new NonResolvingResourceDescriptionResolver())
                    .setAddHandler(new ModelOnlyAddStepHandler())
                    .setRemoveHandler(new ModelOnlyRemoveStepHandler())
            );
            this.processType = processType;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);

            resourceRegistration.registerOperationHandler(PUBLIC, ((context, operation) -> context.getResult().set(true)));
            resourceRegistration.registerOperationHandler(HIDDEN, NoopOperationStepHandler.WITH_RESULT);
            resourceRegistration.registerOperationHandler(PRIVATE, NoopOperationStepHandler.WITH_RESULT);
            resourceRegistration.registerOperationHandler(RUNTIME_ONLY, RuntimeOnlyHandler.INSTANCE);
            resourceRegistration.registerOperationHandler(RUNTIME_READ_ONLY, RuntimeOnlyHandler.INSTANCE);

            if (processType == ProcessType.DOMAIN_SERVER) {
                resourceRegistration.registerOperationHandler(DOMAIN_HIDDEN, NoopOperationStepHandler.WITH_RESULT);
                resourceRegistration.registerOperationHandler(DOMAIN_SERVER_PRIVATE, NoopOperationStepHandler.WITH_RESULT);
                resourceRegistration.registerOperationHandler(DOMAIN_SERVER_RUNTIME_PRIVATE, RuntimeOnlyHandler.INSTANCE);
            } else if (!processType.isServer()) {
                resourceRegistration.registerOperationHandler(DOMAIN_PRIVATE, NoopOperationStepHandler.WITH_RESULT);
                resourceRegistration.registerOperationHandler(DOMAIN_RUNTIME_PRIVATE, RuntimeOnlyHandler.INSTANCE);
            }
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadOnlyAttribute(RUNTIME_ONLY_ATTR, RuntimeOnlyHandler.INSTANCE);
            resourceRegistration.registerMetric(METRIC, RuntimeOnlyHandler.INSTANCE);
        }
    }

    private static class RuntimeOnlyHandler implements OperationStepHandler {

        private static final RuntimeOnlyHandler INSTANCE = new RuntimeOnlyHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            boolean forMe = context.getProcessType() == ProcessType.DOMAIN_SERVER;
            if (!forMe) {
                String targetHost = TARGET_HOST.resolveModelAttribute(context, operation).asStringOrNull();
                if (targetHost != null) {
                    Set<String> hosts = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false).getChildrenNames(HOST);
                    String name = hosts.size() > 1 ? "master": hosts.iterator().next();
                    forMe = targetHost.equals(name);
                }
            }
            if (forMe) {
                context.addStep((ctx, op) -> ctx.getResult().set(true), OperationContext.Stage.RUNTIME);
            }
        }
    }
}
