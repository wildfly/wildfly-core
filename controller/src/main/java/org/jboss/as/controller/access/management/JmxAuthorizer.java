/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import org.jboss.as.controller.access.Authorizer;

/**
 * Hook to expose JMX-related access control configuration to the JMX subsystem without
 * exposing unrelated capabilities.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface JmxAuthorizer extends Authorizer {

    /**
     * Sets whether JMX calls to non-facade mbeans (i.e. those that result in invocations to
     * {@link Authorizer#authorizeJmxOperation(org.jboss.as.controller.access.Caller, org.jboss.as.controller.access.Environment,
     * org.jboss.as.controller.access.JmxAction, org.jboss.as.controller.access.JmxTarget)}) should be treated as 'sensitive'.
     *
     * @param sensitive {@code true} if non-facade mbean calls are sensitive; {@code false} otherwise
     */
    void setNonFacadeMBeansSensitive(boolean sensitive);

    /**
     * Gets whether JMX calls to non-facade mbeans (i.e. those that result in invocations to
     * {@link Authorizer#authorizeJmxOperation(org.jboss.as.controller.access.Caller, org.jboss.as.controller.access.Environment,
     * org.jboss.as.controller.access.JmxAction, org.jboss.as.controller.access.JmxTarget)}) should be treated as 'sensitive'.
     *
     * @return {@code true} if non-facade mbean calls are sensitive; {@code false} otherwise
     */
    boolean isNonFacadeMBeansSensitive();
}
