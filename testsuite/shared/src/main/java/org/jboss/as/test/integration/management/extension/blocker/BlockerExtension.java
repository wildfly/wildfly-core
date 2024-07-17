/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.extension.blocker;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Set;
import java.util.logging.Logger;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
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
import org.jboss.as.controller.client.helpers.MeasurementUnit;
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
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Extension that can block threads.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class BlockerExtension implements Extension {

    public static final String MODULE_NAME = "org.wildfly.extension.blocker-test";
    public static final String SUBSYSTEM_NAME = "blocker-test";
    public static final AttributeDefinition CALLER = SimpleAttributeDefinitionBuilder.create("caller", ModelType.STRING, true)
            .setDefaultValue(new ModelNode("unknown")).build();
    public static final AttributeDefinition TARGET_HOST = SimpleAttributeDefinitionBuilder.create(HOST, ModelType.STRING, true).build();
    public static final AttributeDefinition TARGET_SERVER = SimpleAttributeDefinitionBuilder.create(SERVER, ModelType.STRING, true).build();
    public static final AttributeDefinition BLOCK_POINT = SimpleAttributeDefinitionBuilder.create("block-point", ModelType.STRING)
            .setValidator(EnumValidator.create(BlockPoint.class))
            .build();
    public static final AttributeDefinition BLOCK_TIME = SimpleAttributeDefinitionBuilder.create("block-time", ModelType.LONG, true)
            .setDefaultValue(new ModelNode(20000))
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .build();

    public static final AttributeDefinition FOO = SimpleAttributeDefinitionBuilder.create("foo", ModelType.BOOLEAN, true).build();
    public static final String REGISTERED_MESSAGE = "Registered blocker-test operations";

    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser("urn:wildfly:extension:blocker-test:1.0");
    private static final Logger log = Logger.getLogger(BlockerExtension.class.getCanonicalName());

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        subsystem.setHostCapable();
        subsystem.registerSubsystemModel(new BlockerSubsystemResourceDefinition(context.getProcessType().isHostController()));
        subsystem.registerXMLElementWriter(PARSER);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, PARSER.getNamespace(), PARSER);
    }

    private static class BlockerSubsystemResourceDefinition extends SimpleResourceDefinition {

        private final boolean forHost;
        private BlockerSubsystemResourceDefinition(boolean forHost) {
            super(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME), NonResolvingResourceDescriptionResolver.INSTANCE,
                    ModelOnlyAddStepHandler.INSTANCE,
                    ModelOnlyRemoveStepHandler.INSTANCE);
            this.forHost = forHost;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(BlockHandler.DEFINITION, new BlockHandler());
            if (forHost) {
                resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
            }
            // Don't remove this as some tests check for it in the log
            log.info(REGISTERED_MESSAGE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            resourceRegistration.registerReadWriteAttribute(FOO, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }

    public enum BlockPoint {
        MODEL,
        RUNTIME,
        SERVICE_START,
        SERVICE_STOP,
        VERIFY,
        COMMIT,
        ROLLBACK
    }

    private static class BlockHandler implements OperationStepHandler {

        private static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("block", NonResolvingResourceDescriptionResolver.INSTANCE)
                .setParameters(CALLER, TARGET_HOST, TARGET_SERVER, BLOCK_POINT, BLOCK_TIME)
                .setRuntimeOnly()
                .build();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode targetServer = TARGET_SERVER.resolveModelAttribute(context, operation);
            ModelNode targetHost = TARGET_HOST.resolveModelAttribute(context, operation);
            final BlockPoint blockPoint = BlockPoint.valueOf(BLOCK_POINT.resolveModelAttribute(context, operation).asString());
            log.info("block requested by " + CALLER.resolveModelAttribute(context, operation).asString() + " for " +
                targetHost.asString() + "/" + targetServer.asString() + "(" + blockPoint + ")");
            boolean forMe = false;
            if (context.getProcessType() == ProcessType.STANDALONE_SERVER) {
                forMe = true;
                // WFCORE-3406 explicitly get the exclusive lock for standalone server
                context.acquireControllerLock();
            } else {
                context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS); // To help with WFCORE-263 testing, get the exclusive lock on this process
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
                        name = "primary";
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
                final long blockTime = BLOCK_TIME.resolveModelAttribute(context, operation).asLong();
                log.info("will block at " + blockPoint + " for " + blockTime);
                switch (blockPoint) {
                    case MODEL: {
                        block(blockTime);
                        break;
                    }
                    case RUNTIME: {
                        context.addStep(new BlockStep(blockTime), OperationContext.Stage.RUNTIME);
                        break;
                    }
                    case SERVICE_START: {
                        context.addStep(new OperationStepHandler() {
                            @Override
                            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                BlockingService service = new BlockingService(blockTime, true);
                                context.getServiceTarget().addService(BlockingService.SERVICE_NAME, service).install();
                                context.completeStep(new OperationContext.ResultHandler() {
                                    @Override
                                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                                        log.info("BlockingService step completed: result = " + resultAction);
                                        context.removeService(BlockingService.SERVICE_NAME);
                                    }
                                });
                            }
                        }, OperationContext.Stage.RUNTIME);
                        break;
                    }
                    //This is used by PreparedResponseTestCase where we only add the service which will be stopped by stopping/reloading the server in the test.
                    //This might not be the original intent of the BLockerExtension so be careful if you want to change it.
                    case SERVICE_STOP: {
                        context.addStep(new OperationStepHandler() {
                            @Override
                            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                BlockingService service = new BlockingService(blockTime, false);
                                context.getServiceTarget().addService(BlockingService.SERVICE_NAME, service).install();
                            }
                        }, OperationContext.Stage.RUNTIME);
                        break;
                    }
                    case VERIFY: {
                        context.addStep(new BlockStep(blockTime), OperationContext.Stage.VERIFY);
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
                        throw new IllegalStateException(blockPoint.toString());
                }
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if ((blockPoint == BlockPoint.COMMIT && resultAction == OperationContext.ResultAction.KEEP)
                            || (blockPoint == BlockPoint.ROLLBACK && resultAction == OperationContext.ResultAction.ROLLBACK)) {
                            block(blockTime);
                        }
                    }
                });
            }
        }

        private static void block(long time) {
            try {
                log.info("blocking");
                Thread.sleep(time);
            } catch (InterruptedException e) {
                log.info("interrupted");
                throw new RuntimeException(e);
            }
        }

        private static class BlockStep implements OperationStepHandler {
            private final long blockTime;

            private BlockStep(long blockTime) {
                this.blockTime = blockTime;
            }

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                block(blockTime);
            }
        }
    }

    private static class BlockingService implements Service<BlockingService> {

        private static final ServiceName SERVICE_NAME = ServiceName.of("jboss", "test", "blocking-service");
        private final long blockTime;
        private final boolean blockStart;

        private final Object waitObject = new Object();

        private BlockingService(long blockTime, boolean blockStart) {
            this.blockTime = blockTime;
            this.blockStart = blockStart;
        }

        @Override
        public void start(final StartContext context) throws StartException {
            if (blockStart) {
//                Runnable r = new Runnable() {
//                    @Override
//                    public void run() {
                        try {
                            synchronized (waitObject) {
                                log.info("BlockService blocking in start");
                                waitObject.wait(blockTime);
                            }
                            context.complete();
                        } catch (InterruptedException e) {
                            log.info("BlockService interrupted");
//                            context.failed(new StartException(e));
                            throw new StartException(e);
                        }
//                    }
//                };
//                Thread thread = new Thread(r);
//                thread.start();
//                context.asynchronous();
            }
        }

        @Override
        public void stop(final StopContext context) {
            if (!blockStart) {
                try {
                    synchronized (waitObject) {
                        log.info("BlockService blocking in stop");
                        waitObject.wait(blockTime);
                    }
                    context.complete();
                } catch (InterruptedException e) {
                    log.info("BlockService interrupted");
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            } else {
                synchronized (waitObject) {
                    log.info("BlockService Stopping");
                    waitObject.notifyAll();
                }
            }
        }

        @Override
        public BlockingService getValue() throws IllegalStateException, IllegalArgumentException {
            return this;
        }
    }
}
