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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler validating that all known model references are present.
 *
 * This should probably be replaced with model reference validation at the end of the Stage.MODEL.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainModelReferenceValidator implements OperationStepHandler {

    public static OperationStepHandler INSTANCE = new DomainModelReferenceValidator();

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        if (context.isBooting()) {
            // This does not need to get executed for each operation on boot
            // It also causes issues with the testsuite, which only partially sets up the model
            return;
        }
        // Validate
        validate(context);
    }

    public static void validate(final OperationContext context) throws OperationFailedException {

        final Set<String> profiles = new HashSet<>();
        final Set<String> serverGroups = new HashSet<>();
        final Set<String> socketBindings = new HashSet<>();
        final Set<String> interfaces = new HashSet<String>();

        final Resource domain = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
        final String hostName = determineHostName(domain);
        if (hostName != null) {
            // The testsuite does not always setup the model properly
            final Resource host = domain.getChild(PathElement.pathElement(HOST, hostName));
            for (final Resource.ResourceEntry serverConfig : host.getChildren(SERVER_CONFIG)) {
                final ModelNode model = serverConfig.getModel();
                final String group = model.require(GROUP).asString();
                if (!serverGroups.contains(group)) {
                    serverGroups.add(group);
                }
                if (model.hasDefined(SOCKET_BINDING_DEFAULT_INTERFACE)) {
                    String defaultInterface = model.get(SOCKET_BINDING_DEFAULT_INTERFACE).asString();
                    if (!interfaces.contains(defaultInterface)) {
                        interfaces.add(defaultInterface);
                    }
                }
                processSocketBindingGroup(model, socketBindings);
            }
        }

        // process referenced server-groups
        for (final Resource.ResourceEntry serverGroup : domain.getChildren(SERVER_GROUP)) {
            final ModelNode model = serverGroup.getModel();
            final String profile = model.require(PROFILE).asString();
            // Process the profile
            processProfile(domain, profile, profiles);
            // Process the socket-binding-group
            processSocketBindingGroup(model, socketBindings);

            serverGroups.remove(serverGroup.getName()); // The server-group is present
        }

        // process referenced interfaces
        for (final Resource.ResourceEntry iface : domain.getChildren(INTERFACE)) {
            interfaces.remove(iface.getName());
        }

        // If we are missing a server group
        if (!serverGroups.isEmpty()) {
            throw DomainControllerLogger.ROOT_LOGGER.missingReferences(SERVER_GROUP, serverGroups);
        }
        // Process profiles
        for (final Resource.ResourceEntry profile : domain.getChildren(PROFILE)) {
            profiles.remove(profile.getName());
        }
        // We are missing a profile
        if (!profiles.isEmpty()) {
            throw DomainControllerLogger.ROOT_LOGGER.missingReferences(PROFILE, profiles);
        }
        // Process socket-binding groups
        for (final Resource.ResourceEntry socketBindingGroup : domain.getChildren(SOCKET_BINDING_GROUP)) {
            socketBindings.remove(socketBindingGroup.getName());
        }
        // We are missing a socket-binding group
        if (!socketBindings.isEmpty()) {
            throw DomainControllerLogger.ROOT_LOGGER.missingReferences(SOCKET_BINDING_GROUP, socketBindings);
        }
        //We are missing an interface
        if (!interfaces.isEmpty()) {
            throw DomainControllerLogger.ROOT_LOGGER.missingReferences(INTERFACE, interfaces);
        }

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

    static void processSocketBindingGroup(final ModelNode model, final Set<String> socketBindings) {
        if (model.hasDefined(SOCKET_BINDING_GROUP)) {
            final String socketBinding = model.require(SOCKET_BINDING_GROUP).asString();
            if (!socketBindings.contains(socketBinding)) {
                socketBindings.add(socketBinding);
            }
        }
    }

    static String determineHostName(final Resource domain) {
        // This could use a better way to determine the local host name
        for (final Resource.ResourceEntry entry : domain.getChildren(HOST)) {
            if (entry.isProxy() || entry.isRuntime()) {
                continue;
            }
            return entry.getName();
        }
        return null;
    }

}
