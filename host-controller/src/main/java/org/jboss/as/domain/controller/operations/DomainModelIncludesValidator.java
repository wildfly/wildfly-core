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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
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
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler validating that "including" resources don't involve cycles
 * and that including resources don't involve children that override the included resources.
 *
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class DomainModelIncludesValidator implements OperationStepHandler {

    private static DomainModelIncludesValidator INSTANCE = new DomainModelIncludesValidator();
    private static final AttachmentKey<DomainModelIncludesValidator> KEY = AttachmentKey.create(DomainModelIncludesValidator.class);

    private DomainModelIncludesValidator() {
    }

    public static void addValidationStep(OperationContext context, ModelNode operation) {
        assert !context.getProcessType().isServer() : "Not a host controller";
        if (!context.isBooting()) {
            // This does not need to get executed on boot the domain controller service does that once booted
            // by calling validateAtBoot(). Otherwise we get issues with the testsuite, which only partially sets up the model
            if (context.attachIfAbsent(KEY, DomainModelIncludesValidator.INSTANCE) == null) {
                context.addStep(DomainModelIncludesValidator.INSTANCE, OperationContext.Stage.MODEL);
            }
        }
    }

    public static void validateAtBoot(OperationContext context, ModelNode operation) {
        assert !context.getProcessType().isServer() : "Not a host controller";
        assert context.isBooting() : "Should only be called at boot";
        assert operation.require(OP).asString().equals("validate"); //Should only be called by the domain controller service
        //Only validate once
        if (context.attachIfAbsent(KEY, DomainModelIncludesValidator.INSTANCE) == null) {
            context.addStep(DomainModelIncludesValidator.INSTANCE, OperationContext.Stage.MODEL);
        }
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Validate
        validate(context);
    }

    public void validate(final OperationContext context) throws OperationFailedException {

        final Resource domain = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
        final Set<String> missingProfiles = new HashSet<>();
        final Set<String> missingSocketBindingGroups = new HashSet<>();
        checkProfileIncludes(domain, missingProfiles);
        checkSocketBindingGroupIncludes(domain, missingSocketBindingGroups);

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
                List<String> stack = new ArrayList<>();
                Map<String, List<String>> reachableChildren = new HashMap<>();
                validateChildrenNotOverridden(resourceName, reachableChildren, stack);
            }
        }

        void validateChildrenNotOverridden(String resourceName, Map<String, List<String>> reachableChildren,
                                           List<String> stack) throws OperationFailedException {
            stack.add(resourceName);
            try {
                seen.add(resourceName);
                Set<String> includes = resourceIncludes.get(resourceName);
                Set<String> children = resourceChildren.get(resourceName);
                if (includes.size() == 0 && children.size() == 0) {
                    return;
                }
                for (String child : resourceChildren.get(resourceName)) {
                    List<String> existingChildParentStack = reachableChildren.get(child);
                    if (existingChildParentStack != null) {
                        logError(resourceName, stack, child, existingChildParentStack);
                    }
                    reachableChildren.put(child, new ArrayList<>(stack));
                }
                for (String include : includes) {
                    validateChildrenNotOverridden(include, reachableChildren, stack);
                }
            } finally {
                stack.remove(stack.size() - 1);
            }
        }

        private void logError(String resourceName, List<String> stack, String child, List<String> existingChildParentStack) throws OperationFailedException {
            //Now figure out if this is a direct override, or no override but including two parents
            //with the same child
            for (ListIterator<String> it = stack.listIterator(stack.size()) ; it.hasPrevious() ; ) {
                String commonParent = it.previous();
                if (existingChildParentStack.contains(commonParent)) {
                    if (!getLastElement(existingChildParentStack).equals(commonParent)) {
                        //This is not an override but 'commonParent' includes two parents with the same child
                        throw twoParentsWithSameChild(commonParent, getLastElement(stack), getLastElement(existingChildParentStack), child);
                    }
                }
            }
            //It is a direct override
            //Alternatively, something went wrong when trying to determine the cause, in which case this message
            //will not be 100% correct, but it is better to get an error than not.
            throw attemptingToOverride(getLastElement(existingChildParentStack), child, resourceName);
        }

        private String getLastElement(List<String> list) {
            return list.get(list.size() - 1);
        }

        protected abstract OperationFailedException twoParentsWithSameChild(String commonParent, String include1, String include2, String child);

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
            return HostControllerLogger.ROOT_LOGGER.profileAttemptingToOverrideSubsystem(parentOfExistingChild, child, resourceName);
        }

        @Override
        OperationFailedException involvedInACycle(String include) {
            return HostControllerLogger.ROOT_LOGGER.profileInvolvedInACycle(include);
        }

        @Override
        protected OperationFailedException twoParentsWithSameChild(String commonParent, String include1, String include2, String child) {
            return HostControllerLogger.ROOT_LOGGER.profileIncludesSameSubsystem(commonParent, include1, include2, child);
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
                        throw HostControllerLogger.ROOT_LOGGER.bindingNameNotUnique(name, groupEntry.getName());
                    }
                }
            }
        }

        @Override
        OperationFailedException attemptingToOverride(String parentOfExistingChild, String child, String resourceName) {
            return HostControllerLogger.ROOT_LOGGER.socketBindingGroupAttemptingToOverrideSocketBinding(parentOfExistingChild, child, resourceName);
        }

        @Override
        OperationFailedException involvedInACycle(String include) {
            return HostControllerLogger.ROOT_LOGGER.socketBindingGroupInvolvedInACycle(include);
        }

        @Override
        protected OperationFailedException twoParentsWithSameChild(String commonParent, String include1, String include2, String child) {
            return HostControllerLogger.ROOT_LOGGER.socketBindingGroupIncludesSameSocketBinding(commonParent, include1, include2, child);
        }
    }
}
