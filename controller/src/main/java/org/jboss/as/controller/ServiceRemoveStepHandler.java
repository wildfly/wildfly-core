package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Abstract remove step handler that simply removes a service. If the operation is rolled
 * back it delegates the rollback to the corresponding add operations
 * {@link AbstractAddStepHandler#performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}
 * method.
 *
 * @author Stuart Douglas
 */
public class ServiceRemoveStepHandler extends AbstractRemoveStepHandler {

    private static final RuntimeCapability[] NO_CAPABILITIES = new RuntimeCapability[0];
    private final ServiceName baseServiceName;
    private final AbstractAddStepHandler addOperation;
    private final RuntimeCapability[] unavailableCapabilities;

    /**
     * Creates a {@code ServiceRemoveStepHandler}.
     * @param baseServiceName base name to remove. Cannot be {@code null} unless {@code unavailableCapabilities} are provided
     * @param addOperation the add operation to use to rollback service removal. Cannot be {@code null}
     * @param unavailableCapabilities capabilities that will no longer be available once the remove occurs. Any services
     *          {@link RuntimeCapability#getCapabilityServiceValueType() exposed by the capabilities} will also be removed
     */
    public ServiceRemoveStepHandler(final ServiceName baseServiceName, final AbstractAddStepHandler addOperation, final RuntimeCapability ... unavailableCapabilities) {
        super(unavailableCapabilities);
        this.baseServiceName = baseServiceName;
        this.addOperation = addOperation;
        this.unavailableCapabilities = unavailableCapabilities;
    }

    /**
     * Creates a {@code ServiceRemoveStepHandler}.
     * @param baseServiceName base name to remove. Cannot be {@code null}
     * @param addOperation the add operation to use to rollback service removal. Cannot be {@code null}
     */
    public ServiceRemoveStepHandler(final ServiceName baseServiceName, final AbstractAddStepHandler addOperation) {
        this(baseServiceName, addOperation, NO_CAPABILITIES);
    }

    /**
     * Creates a {@code ServiceRemoveStepHandler}.
     * @param addOperation the add operation to use to rollback service removal. Cannot be {@code null}
     * @param unavailableCapabilities capabilities that will no longer be available once the remove occurs. Any services
     *          {@link RuntimeCapability#getCapabilityServiceValueType() exposed by the capabilities} will also be removed.
     *          Cannot be {@code null} or empty.
     */
    public ServiceRemoveStepHandler(final AbstractAddStepHandler addOperation, final RuntimeCapability ... unavailableCapabilities) {
        this(null, addOperation, unavailableCapabilities);
    }

    protected ServiceRemoveStepHandler(final AbstractAddStepHandler addOperation) {
        this(null, addOperation);
    }

    /**
     * If the {@link OperationContext#isResourceServiceRestartAllowed() context allows resource removal},
     * removes services; otherwise puts the process in reload-required state. The following services are
     * removed:
     * <ul>
     *     <li>The service named by the value returned from {@link #serviceName(String, PathAddress)}, if there is one</li>
     *     <li>The service names associated with any {@code unavailableCapabilities}
     *         passed to the constructor.</li>
     * </ul>
     *
     * {@inheritDoc}
     */
    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        if (context.isResourceServiceRestartAllowed()) {

            final PathAddress address = context.getCurrentAddress();
            final String name = address.getLastElement().getValue();

            ServiceName nonCapabilityServiceName = serviceName(name, address);
            if (nonCapabilityServiceName != null) {
                context.removeService(serviceName(name, address));
            }

            for (RuntimeCapability<?> capability : unavailableCapabilities) {
                if (capability.getCapabilityServiceValueType() != null) {
                    ServiceName sname;
                    if (capability.isDynamicallyNamed()) {
                        sname = capability.getCapabilityServiceName(name);
                    } else {
                        sname = capability.getCapabilityServiceName();
                    }
                    context.removeService(sname);
                }
            }
        } else {
            context.reloadRequired();
        }
    }

    /**
     * The service name to be removed. Can be overridden for unusual service naming patterns
     * @param name The name of the resource being removed
     * @param address The address of the resource being removed
     * @return The service name to remove. May return {@code null} if only removal based on {@code unavailableCapabilities}
     *         passed to the constructor are to be performed
     */
    protected ServiceName serviceName(String name, PathAddress address) {
        return serviceName(name);
    }

    /**
     * The service name to be removed. Can be overridden for unusual service naming patterns
     * @param name The name of the resource being removed
     * @return The service name to remove. May return {@code null} if only removal based on {@code unavailableCapabilities}
     *         passed to the constructor are to be performed
     */
    protected ServiceName serviceName(final String name) {
        return baseServiceName != null ? baseServiceName.append(name) : null;
    }

    /**
     * If the {@link OperationContext#isResourceServiceRestartAllowed() context allows resource removal},
     * attempts to restore services by invoking the {@code performRuntime} method on the @{code addOperation}
     * handler passed to the constructor; otherwise puts the process in reload-required state.
     *
     * {@inheritDoc}
     */
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        if (context.isResourceServiceRestartAllowed()) {
            addOperation.performRuntime(context, operation, model);
        } else {
            context.revertReloadRequired();
        }
    }
}
