/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.util.Arrays;
import java.util.Set;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.wildfly.core.logmanager.WildFlyLogContextSelector;

/**
 * A set of utilities for the logging subsystem.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @since 7.1.0
 */
public final class Logging {

    private Logging() {
    }

    // TODO (jrp) we should handle this better
    public static LogContext getLogContext(final String profileName) {
        if (profileName != null) {
            return WildFlyLogContextSelector.getContextSelector().getOrCreateProfile(profileName);
        }
        return WildFlyLogContextSelector.getContextSelector().getLogContext();
    }

    /**
     * Checks to see within the flags if a restart of any kind is required.
     *
     * @param flags the flags to check
     *
     * @return {@code true} if a restart is required, otherwise {@code false}
     */
    public static boolean requiresRestart(final Set<Flag> flags) {
        return flags.contains(Flag.RESTART_JVM);
    }

    /**
     * Checks to see within the flags if a reload, i.e. not a full restart, is required.
     *
     * @param flags the flags to check
     *
     * @return {@code true} if a reload is required, otherwise {@code false}
     */
    public static boolean requiresReload(final Set<Flag> flags) {
        return flags.contains(Flag.RESTART_ALL_SERVICES) || flags.contains(Flag.RESTART_RESOURCE_SERVICES);
    }

    /**
     * Creates a new {@link OperationFailedException} with the message as a {@link ModelNode model node}.
     *
     * @param message the message to initialize the {@link ModelNode model node} with
     *
     * @return a new {@link OperationFailedException}
     */
    public static OperationFailedException createOperationFailure(final String message) {
        return new OperationFailedException(message);
    }

    /**
     * Creates a new {@link OperationFailedException} with the message as a {@link ModelNode model node} and the cause.
     *
     * @param cause   the cause of the error
     * @param message the message to initialize the {@link ModelNode model node} with
     *
     * @return a new {@link OperationFailedException}
     */
    public static OperationFailedException createOperationFailure(final Throwable cause, final String message) {
        return new OperationFailedException(cause, new ModelNode(message));
    }

    /**
     * Joins two arrays.
     * <p/>
     * If the base array is null, the {@code add} parameter is returned. If the add array is null, the {@code base}
     * array is returned.
     *
     * @param base the base array to add to
     * @param add  the array to add
     * @param <T>  the type of the array
     *
     * @return the joined array
     */
    public static <T> T[] join(final T[] base, final T... add) {
        if (add == null) {
            return base;
        } else if (base == null) {
            return add;
        }
        if (add.length == 0) {
            return base;
        }
        final T[] result = Arrays.copyOf(base, base.length + add.length);
        System.arraycopy(add, 0, result, base.length, add.length);
        return result;
    }

}
