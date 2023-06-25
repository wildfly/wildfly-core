/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.wildfly.common.Assert;

/**
 * Information about handling an attribute in a sub-model.
 *
 * @author Brian Stansberry
 */
public class AttributeAccess {

    /**
     * Indicates how an attributed is accessed.
     */
    public enum AccessType {
        /** A read-only attribute, which can be either {@code Storage.CONFIGURATION} or {@code Storage.RUNTIME} */
        READ_ONLY("read-only", false),
        /** A read-write attribute, which can be either {@code Storage.CONFIGURATION} or {@code Storage.RUNTIME} */
        READ_WRITE("read-write", true),
        /** A read-only {@code Storage.RUNTIME} attribute */
        METRIC("metric", false);

        private final String label;
        private final boolean writable;

        AccessType(final String label, final boolean writable) {
            this.label = label;
            this.writable = writable;
        }

        @Override
        public String toString() {
            return label;
        }

        private static final Map<String, AccessType> MAP;

        static {
            final Map<String, AccessType> map = new HashMap<String, AccessType>();
            for (AccessType element : values()) {
                final String name = element.getLocalName();
                if (name != null) map.put(name, element);
            }
            MAP = map;
        }

        public static AccessType forName(String localName) {
            return MAP.get(localName);
        }

        private String getLocalName() {
            return label;
        }

        public boolean isWritable() {
            return writable;
        }
    }

    /**
     * Indicates whether an attribute is derived from the persistent configuration or is a purely runtime attribute.
     */
    public enum Storage implements Predicate<AttributeAccess> {
        /**
         * An attribute whose value is stored in the persistent configuration.
         * The value may also be stored in runtime services.
         */
        CONFIGURATION("configuration"),
        /**
         * An attribute whose value is only stored in runtime services, and
         * isn't stored in the persistent configuration.
         */
        RUNTIME("runtime");

        private final String label;

        Storage(final String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean test(AttributeAccess attribute) {
            return attribute.getStorageType() == this;
        }
    }

    /** Flags to indicate special characteristics of an attribute */
    public enum Flag implements Predicate<AttributeAccess> {
        /** A modification to the attribute can be applied to the runtime without requiring a restart */
        RESTART_NONE,
        /** A modification to the attribute can only be applied to the runtime via a full jvm restart */
        RESTART_JVM,
        /** A modification to the attribute can only be applied to the runtime via a restart of all services,
         *  but does not require a full jvm restart */
        RESTART_ALL_SERVICES,
        /** A modification to the attribute can only be applied to the runtime via a restart of services,
         *  associated with the attribute's resource, but does not require a restart of all services or a full jvm restart */
        RESTART_RESOURCE_SERVICES,
        /**
         * An attribute whose value is stored in the persistent configuration.
         * The value may also be stored in runtime services.
         */
        STORAGE_CONFIGURATION,
        /**
         * An attribute whose value is only stored in runtime services, and
         * isn't stored in the persistent configuration.
         */
        STORAGE_RUNTIME,
        /**
         * The attribute is an alias to something else
         */
        ALIAS,
        /**
         * An attribute which does not require runtime MSC services to be read or written.
         * This flag can be used in conjunction with STORAGE_RUNTIME to specify that a runtime
         * attribute can work in the absence of runtime services.
         */
        RUNTIME_SERVICE_NOT_REQUIRED,
        /**
         * Support for use of an expression for the value of this attribute is deprecated
         * and may be removed in a future release.
         */
        EXPRESSIONS_DEPRECATED,
        /**
         * The attribute represents a Gauge metric, a single numerical value that can arbitrarily go up and down.
         *
         * If the attribute is registered as a metric without specifying the {@link #GAUGE_METRIC} or {@link #COUNTER_METRIC} flag,
         * it is considered as a gauge.
         *
         * {@link #GAUGE_METRIC} and {@link #COUNTER_METRIC} are mutually exclusive.
         */
        GAUGE_METRIC,
        /**
         * The attribute represents a Counter metric, a cumulative metric that represents a single monotonically
         * increasing counter whose value can only increase or be reset to zero on restart.
         *
         * {@link #GAUGE_METRIC} and {@link #COUNTER_METRIC} are mutually exclusive.
         */
        COUNTER_METRIC;

        private static final Map<EnumSet<AttributeAccess.Flag>, Set<AttributeAccess.Flag>> flagSets = new ConcurrentHashMap<>(16);
        public static Set<AttributeAccess.Flag> immutableSetOf(AttributeAccess.Flag... flags) {
            if (flags == null || flags.length == 0) {
                return Collections.emptySet();
            }
            EnumSet<AttributeAccess.Flag> baseSet;
            if (flags.length == 1) {
                baseSet = EnumSet.of(flags[0]);
            } else {
                baseSet = EnumSet.of(flags[0], flags);
            }
            Set<AttributeAccess.Flag> result = flagSets.get(baseSet);
            if (result == null) {
                Set<AttributeAccess.Flag> immutable = Collections.unmodifiableSet(baseSet);
                Set<AttributeAccess.Flag> existing = flagSets.putIfAbsent(baseSet, immutable);
                result = existing == null ? immutable : existing;
            }

            return result;
        }

        @Override
        public boolean test(AttributeAccess attribute) {
            return attribute.getFlags().contains(this);
        }
    }

    private final AccessType access;
    private final Storage storage;
    private final OperationStepHandler readHandler;
    private final OperationStepHandler writeHandler;
    private final AttributeDefinition definition;

    AttributeAccess(final AccessType access, final Storage storage, final OperationStepHandler readHandler,
                    final OperationStepHandler writeHandler, AttributeDefinition definition) {
        Assert.assertNotNull(access);
        Assert.assertNotNull(storage);
        this.access = access;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.storage = storage;
        this.definition = definition;
        if (definition.getFlags().contains(Flag.ALIAS)) {
            Assert.checkNotNullParam("readHandler", readHandler);
        }
        if (access == AccessType.READ_WRITE) {
            Assert.checkNotNullParam("writeHandler", writeHandler);
        }
    }

    /**
     * Get the access type.
     *
     * @return the access type
     */
    public AccessType getAccessType() {
        return access;
    }

    /**
     * Get the storage type.
     *
     * @return the storage type
     */
    public Storage getStorageType() {
        return storage;
    }

    /**
     * Get the read handler.
     *
     * @return the read handler, <code>null</code> if not defined
     */
    public OperationStepHandler getReadHandler() {
        return readHandler;
    }

    /**
     * Get the write handler.
     *
     * @return the write handler, <code>null</code> if not defined.
     */
    public OperationStepHandler getWriteHandler() {
        return writeHandler;
    }

    public AttributeDefinition getAttributeDefinition() {
        return definition;
    }

    /**
     * Gets the flags associated with this attribute.
     * @return the flags. Will not return {@code null}
     */
    public Set<Flag> getFlags() {
        return definition.getFlags();
    }

}
