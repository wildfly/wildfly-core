/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.testrunner;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public interface ServerSetupTask {

    void setup(final ManagementClient managementClient) throws Exception;

    void tearDown(final ManagementClient managementClient) throws Exception;
}
