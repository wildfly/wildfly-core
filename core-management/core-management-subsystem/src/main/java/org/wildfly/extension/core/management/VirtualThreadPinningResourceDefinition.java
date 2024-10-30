/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logging.Logger;

final class VirtualThreadPinningResourceDefinition extends PersistentResourceDefinition { // TODO this is on PRD because parent resource getChildren requires it

    static final RuntimeCapability<Void> RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.extension.core-management.virtual-thread-pinning")
                    .setServiceType(Void.class)
                    .build();
    private static final String VIRTUAL_THREAD_PINNING_RECORDER = "thread-pinning-recorder";
    public static final Stability STABILITY = Stability.PREVIEW;
    public static final PathElement PATH = PathElement.pathElement(SERVICE, VIRTUAL_THREAD_PINNING_RECORDER);
    static final ResourceRegistration RESOURCE_REGISTRATION = ResourceRegistration.of(PATH, STABILITY);

    static final AttributeDefinition START_MODE = SimpleAttributeDefinitionBuilder.create("start-mode", ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(StartMode.ON_DEMAND.toString()))
            .setValidator(EnumValidator.create(StartMode.class))
            .build();

    static final AttributeDefinition LOG_LEVEL = SimpleAttributeDefinitionBuilder.create("log-level", ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(Logger.Level.WARN.toString()))
            .setValidator(EnumValidator.create(Logger.Level.class))
            .build();

    static final AttributeDefinition MAX_STACK_DEPTH = SimpleAttributeDefinitionBuilder.create("max-stack-depth", ModelType.INT)
            .setRequired(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(20))
            .setValidator(new IntRangeValidator(-1))
            .build();

    private static final AttributeDefinition PINNING_COUNT = SimpleAttributeDefinitionBuilder.create("pinning-count", ModelType.INT)
            .setRequired(true)
            .setStorageRuntime()
            .build();

    private static final AttributeDefinition TOTAL_PINNED_TIME = SimpleAttributeDefinitionBuilder.create("total-pinned-time", ModelType.LONG)
            .setRequired(true)
            .setStorageRuntime()
            .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
            .build();

    private static final AttributeDefinition AVERAGE_PINNED_TIME = SimpleAttributeDefinitionBuilder.create("average-pinned-time", ModelType.INT)
            .setRequired(true)
            .setStorageRuntime()
            .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
            .build();

    static VirtualThreadPinningResourceDefinition create() {
        // A ref to any PinningRecorderData that Add creates; we share it among Remove and the attribute read/write OSHs
        AtomicReference<PinningRecorderData> recorderData = new AtomicReference<>();
        Add addHandler = new Add(recorderData);
        OperationStepHandler removeHandler = new Remove(addHandler);
        return new VirtualThreadPinningResourceDefinition(recorderData, addHandler, removeHandler);
    }

    /** Shared ref to the PinningRecorderData, if Add is called to create one */
    private final AtomicReference<PinningRecorderData> recorderData;

    VirtualThreadPinningResourceDefinition(AtomicReference<PinningRecorderData> recorderData,
                                           OperationStepHandler addHandler,
                                           OperationStepHandler removeHandler) {
        super(new Parameters(RESOURCE_REGISTRATION,
                CoreManagementExtension.getResourceDescriptionResolver(VIRTUAL_THREAD_PINNING_RECORDER))
                .setAddHandler(addHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveHandler(removeHandler)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .addCapabilities(RUNTIME_CAPABILITY)
        );
        this.recorderData = recorderData;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        // We override the only use of this method. The proper value to return varies between a server
        // and an HC, and it's a waste to do all the bookkeeping for that for something never used.
        // This subsystem should move to wildfly-subsystem and drop use of PersistentResourceDefinition
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {

        resourceRegistration.registerReadWriteAttribute(START_MODE, null, ReloadRequiredWriteAttributeHandler.INSTANCE);

        OperationStepHandler modelWrite = new ConfigWrite(recorderData);
        resourceRegistration.registerReadWriteAttribute(LOG_LEVEL, null, modelWrite);
        resourceRegistration.registerReadWriteAttribute(MAX_STACK_DEPTH, null, modelWrite);

        if (resourceRegistration.getProcessType().isServer()) {
            OperationStepHandler metricsRead = new MetricsRead(recorderData);
            resourceRegistration.registerMetric(PINNING_COUNT, metricsRead);
            resourceRegistration.registerMetric(TOTAL_PINNED_TIME, metricsRead);
            resourceRegistration.registerMetric(AVERAGE_PINNED_TIME, metricsRead);
        }
    }

    enum StartMode {
        ON_DEMAND,
        ALWAYS;

        public String toString() {
            return super.toString().toLowerCase(Locale.ENGLISH);
        }
    }

    static final class PinningRecorderData {
        final AtomicReference<Logger.Level> level = new AtomicReference<>();
        final AtomicInteger stackDepth = new AtomicInteger();
        final Metrics metrics = new Metrics();
    }

    /**
     * Tracks metrics data about the number and duration of pinning events.
     */
    static final class Metrics {
        private final AtomicReference<ElapsedPinning> elapsed = new AtomicReference<>(new ElapsedPinning());

        /**
         * Recora a new pinning event.
         * @param pinDuration the duration of the event. Cannot be {@code null}
         */
        void recordPinning(Duration pinDuration) {
            ElapsedPinning existing;
            do {
                existing = elapsed.get();
            } while (!elapsed.compareAndSet(existing, existing.plus(pinDuration)));
        }

        /** Only for unit testing */
        ElapsedPinning getElapsedPinning() {
            return elapsed.get();
        }

    }

    /**
     * Encapsulates the total duration of a set of pinning events and the number of events in the set.
     * Package protected only for unit testing.
     */
    static final class ElapsedPinning {
        private final int count;
        private final Duration duration;

        private ElapsedPinning() {
            this(0, Duration.ZERO);
        }

        private ElapsedPinning(int count, Duration duration) {
            this.count = count;
            this.duration = duration;
        }

        /** Create a new ElapsedPinning that includes the duration of the given additional event. */
        private ElapsedPinning plus(Duration toAdd) {
            return new ElapsedPinning(count + 1, duration.plus(toAdd));
        }

        int getCount() {
            return count;
        }

        long getTotalPinnedTime() {
            return duration.toNanos();
        }

        int getAveragePinnedTime() {
            return count == 0 ? 0 : Math.toIntExact(duration.toNanos() / count);
        }

    }

    private static class Add extends AbstractAddStepHandler {
        private final AtomicReference<PinningRecorderData> recorderData;

        private Add(AtomicReference<PinningRecorderData> recorderData) {
            this.recorderData = recorderData;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            performRuntime(context, model, new PinningRecorderData());
        }

        /** Called from ourselves and from Remove.recoverServices */
        private void performRuntime(OperationContext context, ModelNode model, PinningRecorderData pinningRecorderData) throws OperationFailedException {

            pinningRecorderData.level.set(Logger.Level.valueOf(LOG_LEVEL.resolveModelAttribute(context, model).asString().toUpperCase(Locale.ENGLISH)));
            pinningRecorderData.stackDepth.set(MAX_STACK_DEPTH.resolveModelAttribute(context, model).asInt());

            VirtualThreadPinningRecorderService.install(context.getCapabilityServiceTarget(),
                    StartMode.valueOf(START_MODE.resolveModelAttribute(context, model).asString().toUpperCase(Locale.ENGLISH)),
                    pinningRecorderData);

            recorderData.set(pinningRecorderData);
        }
    }

    private static class Remove extends AbstractRemoveStepHandler {

        /** Used to pass a ref to a removed PinningRecorderData between performRuntime and recoverServices */
        private static final OperationContext.AttachmentKey<PinningRecorderData> RECORDER_KEY =
                OperationContext.AttachmentKey.create(PinningRecorderData.class);
        private final Add addHandler;

        private Remove(Add addHandler) {
            this.addHandler = addHandler;
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) {

            context.removeService(RUNTIME_CAPABILITY.getCapabilityServiceName(Void.class));

            // Store a ref to the PinningRecorderData in the context so we can recover it if needed
            context.attach(RECORDER_KEY, addHandler.recorderData.get());
            addHandler.recorderData.set(null);
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            PinningRecorderData removed = context.getAttachment(RECORDER_KEY);
            addHandler.performRuntime(context, model, removed);
        }
    }

    /** write-attribute handler for the pin logging configuration attributes */
    private static class ConfigWrite extends AbstractWriteAttributeHandler<Void> {

        private final AtomicReference<PinningRecorderData> recorderData;

        private ConfigWrite(AtomicReference<PinningRecorderData> recorderData) {
            this.recorderData = recorderData;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                               ModelNode resolvedValue, ModelNode currentValue,
                                               HandbackHolder<Void> handbackHolder) {
            updatePinningRecorderData(attributeName, resolvedValue);
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                             ModelNode valueToRestore, ModelNode valueToRevert, Void handback) {
            updatePinningRecorderData(attributeName, valueToRestore);
        }

        private void updatePinningRecorderData(String attributeName, ModelNode value) {
            if (MAX_STACK_DEPTH.getName().equals(attributeName)) {
                recorderData.get().stackDepth.set(value.asInt());
            } else if (LOG_LEVEL.getName().equals(attributeName)) {
                recorderData.get().level.set(Logger.Level.valueOf(value.asString().toUpperCase(Locale.ENGLISH)));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    /** read-attribute handler for the pinning metrics */
    private static class MetricsRead implements OperationStepHandler {
        private static final OperationContext.AttachmentKey<ElapsedPinning> ELAPSED_PINNING_KEY =
                OperationContext.AttachmentKey.create(ElapsedPinning.class);
        private final AtomicReference<PinningRecorderData> recorderData;

        private MetricsRead(AtomicReference<PinningRecorderData> recorderData) {
            this.recorderData = recorderData;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) {
            // If we are already recording pinning metrics for this overall operation,
            // for consistency use the data we previously used
            ElapsedPinning elapsedPinning = context.getAttachment(ELAPSED_PINNING_KEY);
            if (elapsedPinning == null) {
                // First read for this operation.
                PinningRecorderData prd = recorderData.get();
                prd = prd == null ? new PinningRecorderData() : prd; // guard against a 'remove' concurrent with a read
                elapsedPinning = prd.metrics.getElapsedPinning();

                // Store with the context for any subsequent reads during this overall operation.
                context.attach(ELAPSED_PINNING_KEY, elapsedPinning);
            }

            String attributeName = operation.require(NAME).asString();
            if (PINNING_COUNT.getName().equals(attributeName)) {
                context.getResult().set(elapsedPinning.getCount());
            } else if (TOTAL_PINNED_TIME.getName().equals(attributeName)) {
                context.getResult().set(elapsedPinning.getTotalPinnedTime());
            } else if (AVERAGE_PINNED_TIME.getName().equals(attributeName)) {
                context.getResult().set(elapsedPinning.getAveragePinnedTime());
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
