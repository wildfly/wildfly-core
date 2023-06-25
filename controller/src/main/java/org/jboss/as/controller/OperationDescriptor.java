/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collection;

/**
 * Describes the parameters of operation..
 * @author Paul Ferraro
 * @deprecated To be removed without replacement.
 */
@Deprecated(forRemoval = true)
public interface OperationDescriptor {
    Collection<? extends AttributeDefinition> getAttributes();
}
