/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata.impl;

/**
 * @author Alexey Loubyansky
 *
 */
public interface RequiresCallback {

    RequiresCallback require(String id);
}
