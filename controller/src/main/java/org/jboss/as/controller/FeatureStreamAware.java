/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.version.FeatureStream;

/**
 * Implemented by objects that should be aware of the configured feature stream.
 * @author Paul Ferraro
 */
public interface FeatureStreamAware {
    /**
     * Returns the feature stream associated with this server.
     * @return a feature stream
     */
    default FeatureStream getFeatureStream() {
        return FeatureStream.DEFAULT;
    }
}
