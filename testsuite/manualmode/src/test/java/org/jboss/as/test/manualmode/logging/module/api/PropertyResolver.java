/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module.api;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@FunctionalInterface
public interface PropertyResolver {

    String resolve(String name);
}
