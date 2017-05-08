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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;

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

            resourceRegistration.registerOperationHandler(PUBLIC, NoopOperationStepHandler.WITH_RESULT);
            resourceRegistration.registerOperationHandler(HIDDEN, NoopOperationStepHandler.WITH_RESULT);
            resourceRegistration.registerOperationHandler(PRIVATE, NoopOperationStepHandler.WITH_RESULT);

            if (processType == ProcessType.DOMAIN_SERVER) {
                resourceRegistration.registerOperationHandler(DOMAIN_HIDDEN, NoopOperationStepHandler.WITH_RESULT);
                resourceRegistration.registerOperationHandler(DOMAIN_SERVER_PRIVATE, NoopOperationStepHandler.WITH_RESULT);
            } else if (!processType.isServer()) {
                resourceRegistration.registerOperationHandler(DOMAIN_PRIVATE, NoopOperationStepHandler.WITH_RESULT);
            }
        }
    }
}
