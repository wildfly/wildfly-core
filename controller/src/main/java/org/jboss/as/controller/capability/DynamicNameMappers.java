/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.capability;

import java.util.function.Function;
import org.jboss.as.controller.PathAddress;

/**
 * Utility class defining name mappers.
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
@Deprecated(forRemoval = true)
public class DynamicNameMappers {
    /**
     * @deprecated Use {@link UnaryCapabilityNameResolver#DEFAULT} instead.
     */
    @Deprecated(forRemoval = true)
    public static final Function<PathAddress, String[]> SIMPLE = UnaryCapabilityNameResolver.DEFAULT;
    /**
     * @deprecated Use {@link UnaryCapabilityNameResolver#PARENT} instead.
     */
    @Deprecated(forRemoval = true)
    public static final Function<PathAddress, String[]> PARENT = BinaryCapabilityNameResolver.PARENT_CHILD;
    /**
     * @deprecated Use {@link UnaryCapabilityNameResolver#GRANDPARENT} instead.
     */
    @Deprecated(forRemoval = true)
    public static final Function<PathAddress, String[]> GRAND_PARENT = TernaryCapabilityNameResolver.GRANDPARENT_PARENT_CHILD;
}
