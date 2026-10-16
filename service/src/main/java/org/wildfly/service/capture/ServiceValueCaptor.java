/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import org.jboss.msc.service.ServiceTarget;
import org.wildfly.service.Installer;

/**
 * Provides a installer for capturing values provided by an MSC service.
 * @author Paul Ferraro
 */
public interface ServiceValueCaptor<K, ST extends ServiceTarget> {
    /**
     * Creates an installer to capture and release the value provided by the service with the specified key.
     * @param key the service key
     * @return an installer
     */
    Installer<ST> capture(K key);
}
