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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.MasterDomainControllerClient;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * @author Emanuel Muckenhuber
 */
class ServerGroupMissingConfigUtils {

    static void pullDownMissingDataFromDc(final OperationContext context, final String serverName, final String serverGroupName, final String socketBindingGroupName) {
        if (context.isBooting()) {
            return;
        }
        context.addStep(new OperationStepHandler() {
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
                if (serviceRegistry == null) {
                    // This is only null in tests
                    context.stepCompleted();
                    return;
                }
                //Get the missing data
                final ServiceController<?> controller = serviceRegistry.getRequiredService(MasterDomainControllerClient.SERVICE_NAME);
                final MasterDomainControllerClient masterDomainControllerClient = (MasterDomainControllerClient) controller.getValue();
                masterDomainControllerClient.pullDownDataForUpdatedServerConfigAndApplyToModel(context, serverName, serverGroupName, socketBindingGroupName);
                context.addStep(new OperationStepHandler() {
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        //An extra sanity check, is this necessary?
                        final Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false);
                        if (!root.hasChild(PathElement.pathElement(SERVER_GROUP, serverGroupName))) {
                            throw HostControllerLogger.ROOT_LOGGER.noServerGroupCalled(serverGroupName);
                        }
                        if (socketBindingGroupName != null) {
                            if (!root.hasChild(PathElement.pathElement(SOCKET_BINDING_GROUP, socketBindingGroupName))) {
                                throw HostControllerLogger.ROOT_LOGGER.noSocketBindingGroupCalled(socketBindingGroupName);
                            }
                        }
                        context.stepCompleted();
                    }
                }, OperationContext.Stage.VERIFY);
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL);
    }

}
