/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.interfaces.InetAddressUtil;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Base class for objects that store environment information for a process.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class ProcessEnvironment implements FeatureRegistry {
    /** The name of the file used to store the process UUID */
    protected static final String UUID_FILE = "process-uuid";

    /** The name of the directory used to store WildFly kernel specific files */
    protected static final String KERNEL_DIR = "kernel";

    /** The special process name value that triggers calculation of a UUID */
    public static final String JBOSS_DOMAIN_UUID = "jboss.domain.uuid";

    /** {@link AttributeDefinition} for the {@code name} attribute for a processes root resource */
    public static final AttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, true)
            .setAllowExpression(true).build();

    public static final String QUALITY = "jboss.quality";

    /**
     * Gets an {@link OperationStepHandler} that can read the {@code name} attribute for a processes root resource
     * @return the handler
     */
    public OperationStepHandler getProcessNameReadHandler() {
        return new ProcessNameReadAttributeHandler();
    }

    /**
     * Gets an {@link OperationStepHandler} that can write the {@code name} attribute for a processes root resource
     * @return the handler
     */
    public OperationStepHandler getProcessNameWriteHandler() {
        return new ProcessNameWriteAttributeHandler();
    }

    /**
     * Gets the resolved name of this process; a value previously passed to {@link #setProcessName(String)} or
     * a value derived from the environment.
     *
     * @return the process name. Cannot be {@code null}
     */
    protected abstract String getProcessName();

    /**
     * Sets the process name. This method can only be called by the handler returned by
     * {@link #getProcessNameWriteHandler()}; its visibility is protected only because subclasses need to implement it.
     *
     * @param processName the process name. May be {@code null} in which case a default process name should be used.
     */
    protected abstract void setProcessName(String processName);

    /**
     * Gets whether updating the runtime system properties with the given property is allowed.
     *
     * @param propertyName  the name of the property. Cannot be {@code null}
     * @param propertyValue the value of the property. May be {@code null}
     * @param bootTime {@code true} if the process is currently booting
     *
     * @return {@code true} if the update can be applied to the runtime system properties; {@code} false if it
     *         should just be stored in the persistent configuration and the process should be put into
     *         {@link org.jboss.as.controller.ControlledProcessState.State#RELOAD_REQUIRED reload-required state}.
     *
     * @throws OperationFailedException if a change to the given property is not allowed at all; e.g. changing
     *                                  {@code jboss.server.base.dir} after primordial boot is not allowed; the
     *                                  property can only be set from the command line
     */
    protected abstract boolean isRuntimeSystemPropertyUpdateAllowed(String propertyName,
                                                                    String propertyValue,
                                                                    boolean bootTime) throws OperationFailedException;

    /**
     * Notifies this {@code ProcessEnvironment} that the runtime value of the given system property has been updated,
     * allowing it to update any state that was originally set via the system property during primordial process boot.
     * This method should only be invoked after a call to {@link #isRuntimeSystemPropertyUpdateAllowed(String, String, boolean)}
     * has returned {@code true}.
     *
     * @param propertyName  the name of the property. Cannot be {@code null}
     * @param propertyValue the value of the property. May be {@code null}
     */
    protected abstract void systemPropertyUpdated(String propertyName, String propertyValue);

    /**
     * Get the UUID of this process.
     * @return the UUID of this process.
     */
    public abstract UUID getInstanceUuid();

    /**
     * Get the fully-qualified host name detected at server startup.
     *
     * @return the qualified host name
     */
    public abstract String getQualifiedHostName();

    /**
     * Get the local host name detected at server startup. Note that this is not the same as the
     * {@link #getHostControllerName() host controller name}. Defaults to the portion of
     * {@link #getQualifiedHostName() the qualified host name} following the first '.'.
     *
     * @return the local host name
     */
    public abstract String getHostName();

    /**
     * Get the {@link RunningModeControl} containing the current running mode of the server
     *
     * @return the running mode control
     */
    public abstract RunningModeControl getRunningModeControl();

    /**
     * Get the name of this server's host controller. For domain-mode servers, this is the name given in the domain configuration. For
     * standalone servers, which do not utilize a host controller, the value should be <code>null</code>.
     *
     * @return server's host controller name if the instance is running in domain mode, or <code>null</code> if running in standalone
     *         mode
     */
    public abstract String getHostControllerName();

    /**
     * Obtain the unique management id for this process and persist it for reuse if the process is restarted.
     * The uuid will be obtained in the following manner:
     * <ol>
     *     <li>If the {@code assignedValue} is not {@code null}, it will be used.</li>
     *     <li>Else if a uuid has been persisted to {@code filePath}, the persisted value will be used</li>
     *     <li>Else a random uuid will be generated</li>
     * </ol>
     * @param filePath filesystem location where the uuid is to be persisted and may have already been persisted. Cannot be {@code null}
     * @param assignedValue value to use for the uuid. May be {@code null}
     * @return the uuid. Will not return {@code null}
     * @throws IOException if there is a problem reading from or writing to {@code filePath}
     */
    protected final UUID obtainProcessUUID(final Path filePath, String assignedValue) throws IOException {
        String uuidString = "";
        UUID uuid = null;
        // If we were not provided a uuid via the param, look for one previously persisted
        if (assignedValue == null && Files.exists(filePath)) {
            try (Stream<String> lines = Files.lines(filePath)) {
                uuidString = lines.findFirst().get();
                uuid = UUID.fromString(uuidString);
            } catch (NoSuchElementException e) {
                ControllerLogger.ROOT_LOGGER.uuidIsEmpty(filePath.toString());
            } catch (IllegalArgumentException e) {
                ControllerLogger.ROOT_LOGGER.uuidNotValid(uuidString, filePath.toString());
            }
        }
        if (uuid == null) {
            uuid = assignedValue == null ? UUID.randomUUID() : UUID.fromString(assignedValue);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, Collections.singletonList(uuid.toString()), StandardOpenOption.CREATE);
        }
        return uuid;
    }

    protected static String resolveGUID(final String unresolvedName) {

        String result;

        if (JBOSS_DOMAIN_UUID.equals(unresolvedName)) {
            try {
                InetAddress localhost = InetAddressUtil.getLocalHost();
                result = UUID.nameUUIDFromBytes(localhost.getAddress()).toString();
            } catch (UnknownHostException e) {
                throw ControllerLogger.ROOT_LOGGER.cannotResolveProcessUUID(e);
            }
        } else {
            result = unresolvedName;
        }

        return result;
    }

    protected class ProcessNameWriteAttributeHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            final ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();

            final ModelNode newValue = operation.hasDefined(VALUE) ? operation.get(VALUE) : new ModelNode();
            final ModelNode mockOp = new ModelNode();
            mockOp.get(NAME.getName()).set(newValue);

            NAME.validateAndSet(mockOp, model);

            final boolean booting = context.isBooting();
            String resolved = null;
            if (booting) {
                final ModelNode resolvedNode = NAME.resolveModelAttribute(context, model);
                resolved = resolvedNode.isDefined() ? resolvedNode.asString() : null;
                resolved = resolved == null ? null : resolveGUID(resolved);
            } else {
                context.reloadRequired();
            }

            final String processName = resolved;

            if (booting) {
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        ProcessEnvironment.this.setProcessName(processName);
                    }
                }, Stage.RUNTIME);
            }

            context.completeStep(new OperationContext.RollbackHandler() {
                @Override
                public void handleRollback(OperationContext context, ModelNode operation) {
                    if (!booting) {
                        context.revertReloadRequired();
                    }
                }
            });
        }
    }

    private class ProcessNameReadAttributeHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
            if (model.hasDefined(NAME.getName())) {
                context.getResult().set(model.get(NAME.getName()));
            } else {
                context.getResult().set(ProcessEnvironment.this.getProcessName());
            }
        }
    }
}
