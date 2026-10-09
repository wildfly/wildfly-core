/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.concurrent.atomic.AtomicReference;

import org.jboss.msc.service.ServiceName;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Registry of {@link org.wildfly.service.capture.FunctionExecutor}s.
 * @author Paul Ferraro
 * @param <V> the registry value type
 */
public interface FunctionExecutorRegistry<V> extends org.wildfly.service.capture.FunctionExecutorRegistry<ServiceName, V> {

    /**
     * @deprecated Superseded by {@link #getExecutor(Object)}.
     */
    @Deprecated
    default FunctionExecutor<V> getExecutor(ServiceDependency<V> key) {
        AtomicReference<ServiceName> reference = new AtomicReference<>();
        key.accept(new ServiceNameCapturingServiceBuilder(reference));
        return this.getExecutor(reference.getPlain());
    }
}
