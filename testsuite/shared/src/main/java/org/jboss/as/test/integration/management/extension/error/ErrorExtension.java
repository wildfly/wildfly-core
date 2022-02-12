/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.management.extension.error;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Extension that throws {@link Error}.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ErrorExtension implements Extension {

    public static final String MODULE_NAME = "org.wildfly.extension.error-test";
    public static final String SUBSYSTEM_NAME = "error-test";
    public static final AttributeDefinition CALLER = SimpleAttributeDefinitionBuilder.create("caller", ModelType.STRING, true)
            .setDefaultValue(new ModelNode("unknown")).build();
    public static final AttributeDefinition TARGET_HOST = SimpleAttributeDefinitionBuilder.create(HOST, ModelType.STRING, true).build();
    public static final AttributeDefinition TARGET_SERVER = SimpleAttributeDefinitionBuilder.create(SERVER, ModelType.STRING, true).build();
    public static final AttributeDefinition ERROR_POINT = SimpleAttributeDefinitionBuilder.create("error-point", ModelType.STRING)
            .setValidator(EnumValidator.create(ErrorPoint.class, EnumSet.allOf(ErrorPoint.class)))
            .build();

    public static final String REGISTERED_MESSAGE = "Registered error-test operations";
    public static final String ERROR_MESSAGE = "Deliberate failure to test java.lang.Error handling";
    public static final String FAIL_REMOVAL = "org.wildfly.extension.error-test.failure";

    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser("urn:wildfly:extension:error-test:1.0");
    private static final Logger log = Logger.getLogger(ErrorExtension.class.getCanonicalName());

    private static final StringBuilder oomeSB = new StringBuilder();

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        subsystem.setHostCapable();
        subsystem.registerSubsystemModel(new BlockerSubsystemResourceDefinition(context.getProcessType().isHostController()));
        subsystem.registerXMLElementWriter(PARSER);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PARSER.getNamespace(), () -> PARSER);
    }

    private static class BlockerSubsystemResourceDefinition extends SimpleResourceDefinition {

        private final boolean forHost;
        private BlockerSubsystemResourceDefinition(boolean forHost) {
            super(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME), NonResolvingResourceDescriptionResolver.INSTANCE,
                    new AbstractAddStepHandler(), ErrorRemovingBlockingSubsystemStepHandler.REMOVE_SUBSYSTEM_INSTANCE);
            this.forHost = forHost;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(ErroringHandler.DEFINITION, new ErroringHandler());
            if (forHost) {
                resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
            }
            // Don't remove this as some tests check for it in the log
            log.info(REGISTERED_MESSAGE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
        }
    }

    public enum ErrorPoint {
        MODEL,
        RUNTIME,
        SERVICE_START,
        SERVICE_STOP,
        VERIFY,
        COMMIT,
        ROLLBACK
    }

    private static void error() {
        log.info("erroring");
        throw new Error(ERROR_MESSAGE);
    }

    private static class ErroringHandler implements OperationStepHandler {

        private static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("error", NonResolvingResourceDescriptionResolver.INSTANCE)
                .setParameters(CALLER, TARGET_HOST, TARGET_SERVER, ERROR_POINT)
                .build();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode targetServer = TARGET_SERVER.resolveModelAttribute(context, operation);
            ModelNode targetHost = TARGET_HOST.resolveModelAttribute(context, operation);
            final ErrorPoint errorPoint = ErrorPoint.valueOf(ERROR_POINT.resolveModelAttribute(context, operation).asString());
            log.info("error requested by " + CALLER.resolveModelAttribute(context, operation).asString() + " for " +
                targetHost.asString() + "/" + targetServer.asString() + "(" + errorPoint + ")");
            boolean forMe = false;
            if (context.getProcessType() == ProcessType.STANDALONE_SERVER) {
                forMe = true;
            } else {
                Resource rootResource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
                if (targetServer.isDefined()) {
                    if (context.getProcessType().isServer()) {
                        String name = System.getProperty(ServerEnvironment.SERVER_NAME);
                        forMe = targetServer.asString().equals(name);
                    }
                } else if (context.getProcessType().isHostController()) {
                    Set<String> hosts = rootResource.getChildrenNames(HOST);
                    String name;
                    if (hosts.size() > 1) {
                        name = "master";
                    } else {
                        name = hosts.iterator().next();
                    }
                    if (!targetHost.isDefined()) {
                        throw new OperationFailedException("target-host required");
                    }
                    forMe = targetHost.asString().equals(name);
                }
            }
            if (forMe) {
                log.info("will error at " + errorPoint);
                switch (errorPoint) {
                    case MODEL: {
                        error();
                        break;
                    }
                    case RUNTIME: {
                        context.addStep(new ErrorStep(), OperationContext.Stage.RUNTIME);
                        break;
                    }
                    case SERVICE_START:
                    case SERVICE_STOP: {
                        context.addStep(new OperationStepHandler() {
                            @Override
                            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                                // We always add the service, regardless of whether we want it to fail in start or stop
                                // Otherwise it's not there to fail in stop!
                                boolean induceOOME = false; // = context.getProcessType().isServer() && !"master".equals(targetHost.asString());
                                final ErroringService service = new ErroringService(errorPoint == ErrorPoint.SERVICE_START, induceOOME);

                                final ServiceController<?> serviceController =
                                        context.getServiceTarget().addService(ErroringService.SERVICE_NAME, service).install();

                                if (errorPoint == ErrorPoint.SERVICE_STOP) {
                                    // Add a separate step to remove the service, triggering stop
                                    context.addStep(new OperationStepHandler() {
                                        @Override
                                        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                            // Make sure the service has started
                                            long timeout = System.currentTimeMillis() + 30000;
                                            boolean started;
                                            do {
                                                started = serviceController.getState() == ServiceController.State.UP;
                                                if (!started) {
                                                    try {
                                                        Thread.sleep(10);
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                        break;
                                                    }
                                                }
                                            } while (!started && System.currentTimeMillis() < timeout);

                                            if (started) {
                                                context.removeService(ErroringService.SERVICE_NAME);
                                            } else {
                                                // Something's wrong.
                                                // Tell the service not to fail any more so we can successfully
                                                // clean it up in the rollback handler
                                                service.errored.set(true);
                                                throw new IllegalStateException(ErroringService.SERVICE_NAME + " service did not start; state is " + serviceController.getState());
                                            }
                                        }
                                    }, OperationContext.Stage.RUNTIME);
                                }

                                // Always try and remove the service on rollback
                                context.completeStep(new OperationContext.RollbackHandler() {
                                    @Override
                                    public void handleRollback(OperationContext context, ModelNode operation) {
                                        context.removeService(ErroringService.SERVICE_NAME);
                                    }
                                });
                            }
                        }, OperationContext.Stage.RUNTIME);
                        break;
                    }
                    case VERIFY: {
                        context.addStep(new ErrorStep(), OperationContext.Stage.VERIFY);
                        break;
                    }
                    case ROLLBACK:
                        context.addStep(new OperationStepHandler() {
                            @Override
                            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                context.getFailureDescription().set("rollback");
                                context.setRollbackOnly();
                            }
                        }, OperationContext.Stage.MODEL);
                        break;
                    case COMMIT:
                        break;
                    default:
                        throw new IllegalStateException(errorPoint.toString());
                }
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if ((errorPoint == ErrorPoint.COMMIT && resultAction == OperationContext.ResultAction.KEEP)
                            || (errorPoint == ErrorPoint.ROLLBACK && resultAction == OperationContext.ResultAction.ROLLBACK)) {
                            error();
                        }
                    }
                });
            }
        }

        private static class ErrorStep implements OperationStepHandler {

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                error();
            }
        }
    }

    private static class ErroringService implements Service<ErroringService> {

        private static final ServiceName SERVICE_NAME = ServiceName.of("jboss", "test", "erroring-service");
        private final boolean errorInStart;
        private final boolean induceOOME;
        private final AtomicBoolean errored = new AtomicBoolean();

        private ErroringService(boolean errorInStart, boolean induceOOME) {
            this.errorInStart = errorInStart;
            this.induceOOME = induceOOME;
        }

        @Override
        public void start(final StartContext context) throws StartException {
            if (errorInStart) {
                if (induceOOME) {  // this will only be true if ErroringHandler is edited to make it possible
                    while (System.currentTimeMillis() > 1) {
                        oomeSB.append("more and more and more");
                    }
                    log.info(oomeSB.toString());
                } else {
                    error();
                }
            }
        }

        @Override
        public void stop(final StopContext context) {
            log.info("ErroringService Stopping");
            if (!errorInStart && errored.compareAndSet(false, true)) {
                error();
            }
        }

        @Override
        public ErroringService getValue() throws IllegalStateException, IllegalArgumentException {
            return this;
        }
    }

    private static class ErrorRemovingBlockingSubsystemStepHandler extends ReloadRequiredRemoveStepHandler {
        private static final ErrorRemovingBlockingSubsystemStepHandler REMOVE_SUBSYSTEM_INSTANCE = new ErrorRemovingBlockingSubsystemStepHandler();
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if(Boolean.getBoolean(FAIL_REMOVAL)) {
                throw new OperationFailedException(ERROR_MESSAGE);
            }
            super.execute(context, operation);
        }
    }
}
