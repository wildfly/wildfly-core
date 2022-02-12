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

package org.jboss.as.test.integration.management.extension.customcontext;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import io.undertow.io.Receiver;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.Methods;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.mgmt.domain.ExtensibleHttpManagement;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Extension whose subsystem adds custom contexts to the http management interface.
 *
 * @author Brian Stansberry
 */
@SuppressWarnings("deprecation")
public class CustomContextExtension implements Extension {

    private static final Logger log = Logger.getLogger(CustomContextExtension.class);

    public static final String EXTENSION_NAME = "org.wildfly.extension.custom-context-test";
    public static final String SUBSYSTEM_NAME = "custom-context-test";

    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser("urn:wildfly:extension:custom-context-test:1.0");


    private static final String REQUIRED_CAP = "org.wildfly.management.http.extensible";
    private static final RuntimeCapability<Void> CAP = RuntimeCapability.Builder.of(EXTENSION_NAME)
            .addRequirements(REQUIRED_CAP)
            .build();

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        subsystem.setHostCapable();
        subsystem.registerSubsystemModel(new CustomContextSubsystemResourceDefinition());
        subsystem.registerXMLElementWriter(PARSER);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PARSER.getNamespace(), PARSER);
    }

    private static class CustomContextSubsystemResourceDefinition extends SimpleResourceDefinition {

        private CustomContextSubsystemResourceDefinition() {
            super(new Parameters(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME), NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(ADD_HANDLER)
                    .setRemoveHandler(REMOVE_HANDLER)
                    .setCapabilities(CAP)
            );
        }
    }

    private static AbstractAddStepHandler ADD_HANDLER = new AbstractAddStepHandler(){
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ServiceTarget target = context.getServiceTarget();
            CustomContextService service = new CustomContextService(context.getProcessType().isServer());
            target.addService(CustomContextService.SERVICE_NAME, service)
                .addDependency(context.getCapabilityServiceName(REQUIRED_CAP, ExtensibleHttpManagement.class),
                        ExtensibleHttpManagement.class, service.getHttpManagementInjector())
                .install();
        }
    };

    private static AbstractRemoveStepHandler REMOVE_HANDLER = new AbstractRemoveStepHandler() {
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            context.removeService(CustomContextService.SERVICE_NAME);
        }
    };

    private static class CustomContextService implements Service<Void> {

        private static final ServiceName SERVICE_NAME = ServiceName.of("test", "customcontext");

        private final boolean forServer;
        private final InjectedValue<ExtensibleHttpManagement> httpManagementInjector = new InjectedValue<>();

        private CustomContextService(boolean forServer) {
            this.forServer = forServer;
        }

        @Override
        public void start(StartContext context) throws StartException {
            ExtensibleHttpManagement httpManagement = httpManagementInjector.getValue();

            ResourceManager rm = new ClassPathResourceManager(getClass().getClassLoader(), getClass().getPackage());
            httpManagement.addStaticContext("static", rm);
            log.info("Added context 'static'");

            ExtensibleHttpManagement.PathRemapper remapper = new ExtensibleHttpManagement.PathRemapper() {
                @Override
                public String remapPath(String originalPath) {
                    if ("/foo".equals(originalPath)) {
                        String prefix = forServer ? "" : "/host/master";
                        return prefix + "/extension/" + EXTENSION_NAME;
                    }
                    return null;
                }
            };
            httpManagement.addManagementGetRemapContext("remap", remapper);
            log.info("Added context 'remap'");


            HttpHandler dynamicHandler = new HttpHandler() {

                private String reply = "OK";
                @Override
                public void handleRequest(HttpServerExchange exchange) {
                    if (exchange.getRequestMethod() == Methods.GET) {
                        exchange.getResponseSender().send(reply);
                    } else if (exchange.getRequestMethod() == Methods.POST) {
                        exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
                            @Override
                            public void handle(HttpServerExchange exchange, String message) {
                                reply = message;
                                exchange.setStatusCode(204);
                            }
                        });
                    }
                }
            };

            httpManagement.addManagementHandler("secured-dynamic", true, dynamicHandler);
            log.info("Added context 'secured-dynamic'");
            httpManagement.addManagementHandler("unsecured-dynamic", false, dynamicHandler);
            log.info("Added context 'unsecured-dynamic'");
        }

        @Override
        public void stop(StopContext context) {
            ExtensibleHttpManagement httpManagement = httpManagementInjector.getValue();
            httpManagement.removeContext("static");
            log.info("Removed context 'static'");
            httpManagement.removeContext("remap");
            log.info("Removed context 'remap'");
            httpManagement.removeContext("secured-dynamic");
            log.info("Removed context 'secured-dynamic'");
            httpManagement.removeContext("unsecured-dynamic");
            log.info("Removed context 'unsecured-dynamic'");
        }

        @Override
        public Void getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }

        InjectedValue<ExtensibleHttpManagement> getHttpManagementInjector() {
            return httpManagementInjector;
        }
    }
}
