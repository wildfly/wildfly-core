/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Registry of {@link FunctionExecutor} objects.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface FunctionExecutorRegistry<V> extends org.wildfly.service.capture.FunctionExecutorRegistry<ServiceDependency<V>, V> {
}
