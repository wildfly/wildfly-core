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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collection;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainOperationsHandler implements OperationStepHandler {

    private static final PathAddressFilter DEFAULT_FILTER = new PathAddressFilter(true);
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("model-operations", ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .build();

    private final boolean ignoreUnused;
    private final ExtensionRegistry extensionRegistry;
    private final Collection<IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo> serverConfigs;

    static {
        DEFAULT_FILTER.addReject(PathAddress.pathAddress(PathElement.pathElement(HOST)));
    }

    public ReadMasterDomainOperationsHandler(boolean ignoreUnused, Collection<IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo> serverConfigs, ExtensionRegistry extensionRegistry) {
        this.ignoreUnused = ignoreUnused;
        this.serverConfigs = serverConfigs;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();

        final PathAddressFilter filter;
        if (ignoreUnused) {
            final ExcludeFilter excludeFilter = new ExcludeFilter(true);

            // Get the root resource
            final Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false);
            for (final IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig : serverConfigs) {
                ReadOperationsHandlerUtils.processServerConfig(context, root, excludeFilter.context, serverConfig, extensionRegistry);
            }

            filter = excludeFilter;
        } else {
            filter = DEFAULT_FILTER;
        }

        context.attach(PathAddressFilter.KEY, filter);
        context.addStep(operation, GenericModelDescribeOperationHandler.INSTANCE, OperationContext.Stage.MODEL, true);
        context.stepCompleted();
    }


    static class ExcludeFilter extends PathAddressFilter {

        private final ReadOperationsHandlerUtils.ResolutionContext context;

        ExcludeFilter(boolean accept) {
            super(accept);
            this.context = new ReadOperationsHandlerUtils.ResolutionContext();
        }

        @Override
        boolean accepts(PathAddress address) {
            if (address.size() >= 1) {
                final PathElement first = address.getElement(0);
                final String key = first.getKey();
                switch (key) {
                    case EXTENSION:
                        if (!context.getExtensions().contains(first.getValue())) {
                            return false;
                        }
                        break;
                    case PROFILE:
                        if (!context.getProfiles().contains(first.getValue())) {
                            return false;
                        }
                        break;
                    case SERVER_GROUP:
                        if (!context.getServerGroups().contains(first.getValue())) {
                            return false;
                        }
                        break;
                    case SOCKET_BINDING_GROUP:
                        if (!context.getSocketBindings().contains(first.getValue())) {
                            return false;
                        }
                        break;
                }
            }
            return DEFAULT_FILTER.accepts(address);
        }
    }


}
