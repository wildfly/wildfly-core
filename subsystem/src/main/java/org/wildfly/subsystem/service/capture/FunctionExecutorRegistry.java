/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Registry of {@link org.wildfly.service.capture.FunctionExecutor}s.
 * @author Paul Ferraro
 * @param <V> the registry value type
 */
public interface FunctionExecutorRegistry<V> extends org.wildfly.service.capture.FunctionExecutorRegistry<ServiceDependency<V>, V> {
}
