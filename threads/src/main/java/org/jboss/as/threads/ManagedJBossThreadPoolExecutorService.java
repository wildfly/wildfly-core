/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

/**
 *
 * @author Alexey Loubyansky
 *
 * @deprecated Use {@link ManagedQueueExecutorService}
 */
@Deprecated(forRemoval = true, since = "29.0.0")
public interface ManagedJBossThreadPoolExecutorService extends ManagedQueueExecutorService {
}
