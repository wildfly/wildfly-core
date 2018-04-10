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

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.operations.MultistepUtil;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.MasterDomainControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceNotFoundException;

/**
 * Generic wrapper for certain slave host operations to determine whether there is missing or not needed configuration
 * and adds steps to automatically sync with the domain controller.
 *
 * This should probably be replaced by something like model references, which can be validated and possibly provision
 * missing configuration internally.
 *
 * @author Emanuel Muckenhuber
 */
public final class SyncModelOperationHandlerWrapper implements OperationStepHandler {

    private final PathElement hostElement;
    private final OperationStepHandler delegate;

    /**
     * Wrap an {@code OperationStepHandler} in case it targets addresses, which may need to fetch missing configuration
     * from the master.
     *
     * @param localHostName    the local host name
     * @param operationName    the operation name
     * @param address          the operation address
     * @param entry            the operation entry
     * @return the operation handler
     */
    public static OperationStepHandler wrapHandler(final String localHostName, final String operationName, final PathAddress address, final OperationEntry entry) {

        // Don't wrap runtime operations
        if (entry.getFlags().contains(OperationEntry.Flag.READ_ONLY) || entry.getFlags().contains(OperationEntry.Flag.RUNTIME_ONLY)) {
            return entry.getOperationHandler();
        }

        final int size = address.size();
        // Wrap all composite operations
        if (size == 0 && COMPOSITE.equals(operationName)) {
            return new WrappedCompositeOperationHandler(localHostName);
        } else if (size == 1) {
            final PathElement element = address.getElement(0);
            if (SERVER_GROUP.equals(element.getKey())) {
                // Wrap all configuration operations targeting the server-group directly
                return new SyncModelOperationHandlerWrapper(localHostName, entry.getOperationHandler());
            } else if (PROFILE.equals(element.getKey()) || SOCKET_BINDING_GROUP.equals(element.getKey())) {
                // This might just need to wrap the write-attribute(name=includes) operation
                if (WRITE_ATTRIBUTE_OPERATION.equals(operationName)) {
                    return new SyncModelOperationHandlerWrapper(localHostName, entry.getOperationHandler());
                }
            }
        } else if (size == 2) {
            // Wrap all configuration operations targeting the server-config directly
            final PathElement element = address.getElement(1);
            if (SERVER_CONFIG.equals(element.getKey())) {
                return new SyncModelOperationHandlerWrapper(localHostName, entry.getOperationHandler());
            }
        }
        return entry.getOperationHandler();
    }

    private SyncModelOperationHandlerWrapper(String localHostName, OperationStepHandler delegate) {
        this.hostElement = PathElement.pathElement(HOST, localHostName);
        this.delegate = delegate;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // The backup of the original domain model, before we tinker with it
        final Resource original = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

        // Execute the delegate
        delegate.execute(context, operation);

        // Just a safety check
        assert context.isBooting() == false;
        if (context.isBooting()) {
            return;
        }

        // Validate references on the new model
        final Resource domain = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

        if (syncWithMaster(domain, hostElement)) {
            MasterDomainControllerClient masterDomainControllerClient = null;
            try {
                masterDomainControllerClient = (MasterDomainControllerClient) context.getServiceRegistry(false).getRequiredService(MasterDomainControllerClient.SERVICE_NAME).getValue();
            } catch (ServiceNotFoundException e) {
                // running in admin-only we shouldn't fail if the MDCC isn't available
                if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
                    return;
                }
                throw e;
            }
            // This adds an immediate step to synchronize the model configuration before any other step will be executed
            masterDomainControllerClient.fetchAndSyncMissingConfiguration(context, original);
        }
    }

    /**
     * Determine whether all references are available locally.
     *
     * @param domain         the domain model
     * @param hostElement    the host path element
     * @return whether to a sync with the master is required
     */
    private static boolean syncWithMaster(final Resource domain, final PathElement hostElement) {
        final Resource host = domain.getChild(hostElement);
        assert host != null;

        final Set<String> profiles = new HashSet<>();
        final Set<String> serverGroups = new HashSet<>();
        final Set<String> socketBindings = new HashSet<>();

        for (final Resource.ResourceEntry serverConfig : host.getChildren(SERVER_CONFIG)) {
            final ModelNode model = serverConfig.getModel();
            final String group = model.require(GROUP).asString();
            if (!serverGroups.contains(group)) {
                serverGroups.add(group);
            }
            if (model.hasDefined(SOCKET_BINDING_GROUP)) {
                processSocketBindingGroup(domain, model.require(SOCKET_BINDING_GROUP).asString(), socketBindings);
            }

        }

        // process referenced server-groups
        for (final Resource.ResourceEntry serverGroup : domain.getChildren(SERVER_GROUP)) {
            // If we have an unreferenced server-group
            if (!serverGroups.remove(serverGroup.getName())) {
                return true;
            }
            final ModelNode model = serverGroup.getModel();

            final String profile = model.require(PROFILE).asString();
            // Process the profile
            processProfile(domain, profile, profiles);
            // Process the socket-binding-group
            processSocketBindingGroup(domain, model.require(SOCKET_BINDING_GROUP).asString(), socketBindings);
        }
        // If we are missing a server group
        if (!serverGroups.isEmpty()) {
            return true;
        }
        // Process profiles
        for (final Resource.ResourceEntry profile : domain.getChildren(PROFILE)) {
            // We have an unreferenced profile
            if (!profiles.remove(profile.getName())) {
                return true;
            }
        }
        // We are missing a profile
        if (!profiles.isEmpty()) {
            return true;
        }
        // Process socket-binding groups
        for (final Resource.ResourceEntry socketBindingGroup : domain.getChildren(SOCKET_BINDING_GROUP)) {
            // We have an unreferenced socket-binding group
            if (!socketBindings.remove(socketBindingGroup.getName())) {
                return true;
            }
        }
        // We are missing a socket-binding group
        if (!socketBindings.isEmpty()) {
            return true;
        }
        // Looks good!
        return false;
    }

    static void processProfile(final Resource domain, String profile, Set<String> profiles) {
        if (!profiles.contains(profile)) {
            profiles.add(profile);
            final PathElement pathElement = PathElement.pathElement(PROFILE, profile);
            if (domain.hasChild(pathElement)) {
                final Resource resource = domain.getChild(pathElement);
                final ModelNode model = resource.getModel();
                if (model.hasDefined(INCLUDES)) {
                    for (final ModelNode include : model.get(INCLUDES).asList()) {
                        processProfile(domain, include.asString(), profiles);
                    }
                }
            }
        }
    }

    static void processSocketBindingGroup(final Resource domain, final String name, final Set<String> socketBindings) {
        if (!socketBindings.contains(name)) {
            socketBindings.add(name);
            final PathElement pathElement = PathElement.pathElement(SOCKET_BINDING_GROUP, name);
            if (domain.hasChild(pathElement)) {
                final Resource resource = domain.getChild(pathElement);
                final ModelNode model = resource.getModel();
                if (model.hasDefined(INCLUDES)) {
                    for (final ModelNode include : model.get(INCLUDES).asList()) {
                        processSocketBindingGroup(domain, include.asString(), socketBindings);
                    }
                }
            }
        }
    }

    static class WrappedCompositeOperationHandler extends CompositeOperationHandler {

        private final String hostName;

        WrappedCompositeOperationHandler(String hostName) {
            this.hostName = hostName;
        }

        @Override
        protected MultistepUtil.OperationHandlerResolver getOperationHandlerResolver() {
            return new MultistepUtil.OperationHandlerResolver() {
                @Override
                public OperationStepHandler getOperationStepHandler(String operationName, PathAddress address, ModelNode operation, OperationEntry operationEntry) {
                    return wrapHandler(hostName, operationName, address, operationEntry);
                }
            };
        }
    }

}
