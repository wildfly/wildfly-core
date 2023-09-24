/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

/**
 * @author Alexey Loubyansky
 *
 */
public interface Builder {

    Patch build();
}
