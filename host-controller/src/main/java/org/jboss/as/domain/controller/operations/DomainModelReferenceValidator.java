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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.dmr.ModelNode;

/**
 * Handler validating that all known model references are present.
 *
 * This should probably be replaced with model reference validation at the end of the Stage.MODEL.
 *
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class DomainModelReferenceValidator implements OperationStepHandler {

    private static DomainModelReferenceValidator INSTANCE = new DomainModelReferenceValidator();
    private static final AttachmentKey<DomainModelReferenceValidator> KEY = AttachmentKey.create(DomainModelReferenceValidator.class);

    private DomainModelReferenceValidator() {
    }

    public static void addValidationStep(OperationContext context, ModelNode operation) {
        assert context.getProcessType() == ProcessType.HOST_CONTROLLER : "Not a host controller";
        if (!context.isBooting()) {
            // This does not need to get executed on boot the domain controller service does that once booted
            // by calling validateAtBoot(). Otherwise we get issues with the testsuite, which only partially sets up the model
            if (context.attachIfAbsent(KEY, DomainModelReferenceValidator.INSTANCE) == null) {
                context.addStep(DomainModelReferenceValidator.INSTANCE, OperationContext.Stage.MODEL);
            }
        }
    }

    public static void validateAtBoot(OperationContext context, ModelNode operation) {
        assert context.getProcessType() == ProcessType.HOST_CONTROLLER : "Not a host controller";
        assert context.isBooting() : "Should only be called at boot";
        assert operation.require(OP).asString().equals("validate"); //Should only be called by the domain controller service
        //Only validate once
        if (context.attachIfAbsent(KEY, DomainModelReferenceValidator.INSTANCE) == null) {
            context.addStep(DomainModelReferenceValidator.INSTANCE, OperationContext.Stage.MODEL);
        }
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Validate
        validate(context);
    }

    public void validate(final OperationContext context) throws OperationFailedException {

        final Set<String> serverGroups = new HashSet<>();
        final Set<String> interfaces = new HashSet<String>();

        final Resource domain = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
        final Set<String> missingProfiles = new HashSet<>();
        final Set<String> missingSocketBindingGroups = new HashSet<>();
        final Set<String> allProfiles = checkProfileIncludes(domain, missingProfiles);
        final Set<String> allSocketBindingGroups = checkSocketBindingGroupIncludes(domain, missingSocketBindingGroups);
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
                processSocketBindingGroup(model, allSocketBindingGroups, missingSocketBindingGroups);
            }
        }

        // process referenced server-groups
        for (final Resource.ResourceEntry serverGroup : domain.getChildren(SERVER_GROUP)) {
            final ModelNode model = serverGroup.getModel();
            final String profile = model.require(PROFILE).asString();
            if (!allProfiles.contains(profile)) {
                missingProfiles.add(profile);
            }
            // Process the socket-binding-group
            processSocketBindingGroup(model, allSocketBindingGroups, missingSocketBindingGroups);

            serverGroups.remove(serverGroup.getName()); // The server-group is present
        }

        // process referenced interfaces
        for (final Resource.ResourceEntry iface : domain.getChildren(INTERFACE)) {
            interfaces.remove(iface.getName());
        }
        // If we are missing a server group
        if (!serverGroups.isEmpty()) {
            throw ControllerLogger.ROOT_LOGGER.missingReferences(SERVER_GROUP, serverGroups);
        }
        // We are missing a profile
        if (!missingProfiles.isEmpty()) {
            throw ControllerLogger.ROOT_LOGGER.missingReferences(PROFILE, missingProfiles);
        }
        // Process socket-binding groups
        if (!missingSocketBindingGroups.isEmpty()) {
            throw ControllerLogger.ROOT_LOGGER.missingReferences(SOCKET_BINDING_GROUP, missingSocketBindingGroups);
        }
        //We are missing an interface
        if (!interfaces.isEmpty()) {
            throw ControllerLogger.ROOT_LOGGER.missingReferences(INTERFACE, interfaces);
        }

    }

    private void processSocketBindingGroup(final ModelNode model, final Set<String> socketBindings, final Set<String> missingSocketBindings) {
        if (model.hasDefined(SOCKET_BINDING_GROUP)) {
            final String socketBinding = model.require(SOCKET_BINDING_GROUP).asString();
            if (!socketBindings.contains(socketBinding)) {
                missingSocketBindings.add(socketBinding);
            }
        }
    }

    private String determineHostName(final Resource domain) {
        // This could use a better way to determine the local host name
        for (final Resource.ResourceEntry entry : domain.getChildren(HOST)) {
            if (entry.isProxy() || entry.isRuntime()) {
                continue;
            }
            return entry.getName();
        }
        return null;
    }

    private Set<String> checkProfileIncludes(Resource domain, Set<String> missingProfiles) throws OperationFailedException {
        ProfileIncludeValidator validator = new ProfileIncludeValidator();
        for (ResourceEntry entry : domain.getChildren(PROFILE)) {
            validator.processResource(entry);
        }

        validator.validate(missingProfiles);
        return validator.resourceIncludes.keySet();
    }

    private Set<String> checkSocketBindingGroupIncludes(Resource domain, Set<String> missingSocketBindingGroups) throws OperationFailedException {
        SocketBindingGroupIncludeValidator validator = new SocketBindingGroupIncludeValidator();
        for (ResourceEntry entry : domain.getChildren(SOCKET_BINDING_GROUP)) {
            validator.processResource(entry);
        }

        validator.validate(missingSocketBindingGroups);
        return new HashSet<>(validator.resourceIncludes.keySet());
    }

    private abstract static class AbstractIncludeValidator {
        protected final Set<String> seen = new HashSet<>();
        protected final Set<String> onStack = new HashSet<>();
        protected final Map<String, String> linkTo = new HashMap<>();
        protected final Map<String, Set<String>> resourceIncludes = new HashMap<>();
        protected final Map<String, Set<String>> resourceChildren = new HashMap<>();
        protected final List<String> post = new ArrayList<>();

        void processResource(ResourceEntry resourceEntry) throws OperationFailedException{
            ModelNode model = resourceEntry.getModel();
            final Set<String> includes;
            if (model.hasDefined(INCLUDES)) {
                includes = new HashSet<>();
                for (ModelNode include : model.get(INCLUDES).asList()) {
                    includes.add(include.asString());
                }
            } else {
                includes = Collections.emptySet();
            }
            resourceIncludes.put(resourceEntry.getName(), includes);
        }

        void validate(Set<String> missingEntries) throws OperationFailedException {
            //Look for cycles
            for (String resourceName : resourceIncludes.keySet()) {
                if (!seen.contains(resourceName)) {
                    dfsForMissingOrCyclicIncludes(resourceName, missingEntries);
                }
            }

            if (missingEntries.size() > 0) {
                //We are missing some entries, don't continue with the validation since it has failed
                return;
            }

            //Check that children are not overridden, by traversing them in the order child->parent
            //using the reverse post-order of the dfs
            seen.clear();
            for (ListIterator<String> it = post.listIterator(post.size()) ; it.hasPrevious() ; ) {
                String resourceName = it.previous();
                if (seen.contains(resourceName)) {
                    continue;
                }
                Map<String, String> reachableChildren = new HashMap<>();
                validateChildrenNotOverridden(resourceName, reachableChildren);
            }
        }

        void validateChildrenNotOverridden(String resourceName, Map<String, String> reachableChildren) throws OperationFailedException {
            seen.add(resourceName);
            Set<String> includes = resourceIncludes.get(resourceName);

            if (includes.size() == 0 && reachableChildren.size() == 0) {
                return;
            }
            for (String child : resourceChildren.get(resourceName)) {
                String existingChildParent = reachableChildren.get(child);
                if (existingChildParent != null) {
                    throw attemptingToOverride(existingChildParent, child, resourceName);
                }
                reachableChildren.put(child, resourceName);
            }
            for (String include : includes) {
                validateChildrenNotOverridden(include, reachableChildren);
            }
        }

        void dfsForMissingOrCyclicIncludes(String resourceName, Set<String> missingEntries) throws OperationFailedException {
            onStack.add(resourceName);
            try {
                seen.add(resourceName);
                Set<String> includes = resourceIncludes.get(resourceName);
                if (includes == null) {
                    missingEntries.add(resourceName);
                    return;
                }
                for (String include : includes) {
                    if (!seen.contains(include)) {
                        linkTo.put(include, resourceName);
                        dfsForMissingOrCyclicIncludes(include, missingEntries);
                    } else if (onStack.contains(include)) {
                        throw involvedInACycle(include);
                    }
                }
            } finally {
                onStack.remove(resourceName);
            }
            post.add(resourceName);
        }

        abstract OperationFailedException attemptingToOverride(String parentOfExistingChild, String child, String resourceName);
        abstract OperationFailedException involvedInACycle(String profile);
    }


    private static class ProfileIncludeValidator extends AbstractIncludeValidator {

        void processResource(ResourceEntry profileEntry) throws OperationFailedException {
            super.processResource(profileEntry);

            final Set<String> subsystems;
            if (profileEntry.hasChildren(SUBSYSTEM)) {
                subsystems = new HashSet<>();
                subsystems.addAll(profileEntry.getChildrenNames(SUBSYSTEM));
            } else {
                subsystems = Collections.emptySet();
            }
            resourceChildren.put(profileEntry.getName(), subsystems);
        }

        @Override
        OperationFailedException attemptingToOverride(String parentOfExistingChild, String child, String resourceName) {
            return ControllerLogger.ROOT_LOGGER.profileAttemptingToOverrideSubsystem(parentOfExistingChild, child, resourceName);
        }

        @Override
        OperationFailedException involvedInACycle(String include) {
            return ControllerLogger.ROOT_LOGGER.profileInvolvedInACycle(include);
        }
    }

    private static class SocketBindingGroupIncludeValidator extends AbstractIncludeValidator {

        void processResource(ResourceEntry groupEntry) throws OperationFailedException{
            //Remote and local outbound socket binding names must be unique or we get a DuplicateServiceException
            //Tighten this up to also make the 'normal' ones unique, to make the validation a bit easier.

            super.processResource(groupEntry);

            final Set<String> bindings;
            if (groupEntry.hasChildren(SOCKET_BINDING)
                    || groupEntry.hasChildren(LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING)
                    || groupEntry.hasChildren(REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING)) {
                bindings = new HashSet<>();
                addBindings(groupEntry, bindings, SOCKET_BINDING);
                addBindings(groupEntry, bindings, LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING);
                addBindings(groupEntry, bindings, REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING);
                bindings.addAll(groupEntry.getChildrenNames(SUBSYSTEM));
            } else {
                bindings = Collections.emptySet();
            }
            resourceChildren.put(groupEntry.getName(), bindings);
        }

        private void addBindings(ResourceEntry groupEntry, Set<String> bindings, String bindingType) throws OperationFailedException{
            if (groupEntry.hasChildren(bindingType)) {
                for (String name : groupEntry.getChildrenNames(bindingType)) {
                    if (!bindings.add(name)) {
                        throw ControllerLogger.ROOT_LOGGER.bindingNameNotUnique(name, groupEntry.getName());
                    }
                }
            }
        }

        @Override
        OperationFailedException attemptingToOverride(String parentOfExistingChild, String child, String resourceName) {
            return ControllerLogger.ROOT_LOGGER.socketBindingGroupAttemptingToOverrideSocketBinding(parentOfExistingChild, child, resourceName);
        }

        @Override
        OperationFailedException involvedInACycle(String include) {
            return ControllerLogger.ROOT_LOGGER.socketBindingGroupInvolvedInACycle(include);
        }

    }
}
