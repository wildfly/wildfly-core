/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.base;

import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.base.AbstractMgmtTestBase;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Class that is extended by management tests that can use resource injection to get the management client
 *
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public abstract class ContainerResourceMgmtTestBase extends AbstractMgmtTestBase {

    @Inject
    private static ManagementClient managementClient;


    public ManagementClient getManagementClient() {
        return managementClient;
    }


    public void setManagementClient(ManagementClient managementClient) {
        this.managementClient = managementClient;
    }


    @Override
    protected ModelControllerClient getModelControllerClient() {
        return managementClient.getControllerClient();
    }
}

