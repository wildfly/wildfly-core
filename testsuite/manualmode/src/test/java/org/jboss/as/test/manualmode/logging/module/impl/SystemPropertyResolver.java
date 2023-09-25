/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module.impl;

import org.jboss.as.test.manualmode.logging.module.api.PropertyResolver;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SystemPropertyResolver implements PropertyResolver {

    @Override
    public String resolve(final String name) {
        return System.getProperty(name);
    }
}
