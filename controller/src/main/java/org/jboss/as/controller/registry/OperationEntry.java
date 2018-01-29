/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;

/**
 * Information about a registered {@code OperationStepHandler}.
 *
 * @author Emanuel Muckenhuber
 */
public final class OperationEntry {
    public enum EntryType {
        PUBLIC, PRIVATE
    }

    /** Flags to indicate special characteristics of an operation */
    public enum Flag {
        /** Operation only reads, does not modify */
        READ_ONLY,
        /** The operation modifies the configuration and can be applied to the runtime without requiring a restart */
        RESTART_NONE,
        /** The operation modifies the configuration but can only be applied to the runtime via a full jvm restart */
        RESTART_JVM,
        /** The operation modifies the configuration but can only be applied to the runtime via a restart of all services;
         *  however it does not require a full jvm restart */
        RESTART_ALL_SERVICES,
        /** The operation modifies the configuration but can only be applied to the runtime via a restart of services,
         *  associated with the affected resource, but does not require a restart of all services or a full jvm restart */
        RESTART_RESOURCE_SERVICES,
        /** A domain or host-level operation that should be pushed to the servers even if the default behavior
         *  would indicate otherwise */
        DOMAIN_PUSH_TO_SERVERS,
        /** A host-level operation that should only be executed on the HostController and not on the servers,
         * even if the default behavior would indicate otherwise */
        HOST_CONTROLLER_ONLY,
        /** A domain-level operation that should only be executed on the master HostController and not on the slaves,
         * even if the default behavior would indicate otherwise */
        MASTER_HOST_CONTROLLER_ONLY,
        /** Operations with this flag do not affect the mode or change the installed services. The main intention for
         * this is to only make RUNTIME_ONLY methods on domain mode servers visible to end users. */
        RUNTIME_ONLY,
        /** Operations with this flag do not appear in management API description output but still can be invoked
         *  by external callers.  This is meant for operations that were not meant to be part of the supported external
         *  management API but users may have learned of them. Such ops should be evaluated for inclusion as normally
         *  described ops, or perhaps should be marked with {@link EntryType#PRIVATE} and external use thus disabled.
         *  This can also be used for ops that are invoked internally on one domain process by another domain process
         *  but where it's not possible for the caller to suppress the caller-type=user header from the op, making
         *  use of {@link EntryType#PRIVATE} not workable. */
        HIDDEN;

        private static final Map<EnumSet<Flag>, Set<Flag>> flagSets = new ConcurrentHashMap<>(16);
        public static Set<OperationEntry.Flag> immutableSetOf(EnumSet<OperationEntry.Flag> flags) {
            if (flags == null || flags.isEmpty()) {
                return Collections.emptySet();
            }
            Set<Flag> result = flagSets.get(flags);
            if (result == null) {
                Set<Flag> immutable = Collections.unmodifiableSet(flags);
                Set<Flag> existing = flagSets.putIfAbsent(flags, immutable);
                result = existing == null ? immutable : existing;
            }

            return result;
        }

    }

    private final OperationDefinition operationDefinition;
    private final OperationStepHandler operationHandler;
    private final boolean inherited;

    OperationEntry(final OperationDefinition definition, final OperationStepHandler operationHandler, final boolean inherited) {
        this.operationDefinition = definition;
        this.operationHandler = operationHandler;
        this.inherited = inherited;
    }

    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    public OperationStepHandler getOperationHandler() {
        return operationHandler;
    }

    public DescriptionProvider getDescriptionProvider() {
        return operationDefinition.getDescriptionProvider();
    }

    public boolean isInherited() {
        return inherited;
    }

    public EntryType getType() {
        return operationDefinition.getEntryType();
    }

    public Set<Flag> getFlags() {
        return operationDefinition.getFlags();
    }

    public List<AccessConstraintDefinition> getAccessConstraints() {
        List<AccessConstraintDefinition> accessConstraints = operationDefinition.getAccessConstraints();
        return accessConstraints == null ? Collections.emptyList() : accessConstraints;
    }

}
