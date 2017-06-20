/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.security.common;

import java.util.Arrays;
import java.util.ListIterator;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.logging.Logger;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.test.security.common.elytron.ConfigurableElement;

/**
 * WildFly TestRunner ServerSetupTask version of AbstractConfigSetupTask.
 *
 * @author Josef Cacek
 */
public abstract class TestRunnerConfigSetupTask implements ServerSetupTask {

    private static final Logger LOGGER = Logger.getLogger(TestRunnerConfigSetupTask.class);

    private ConfigurableElement[] configurableElements;

    @Override
    public void setup(final ManagementClient managementClient) throws Exception {
        setup(managementClient.getControllerClient());
    }

    @Override
    public void tearDown(final ManagementClient managementClient) throws Exception {
        tearDown(managementClient.getControllerClient());
    }

    /**
     * Creates configuration elements (provided by implementation of {@link #getConfigurableElements()} method) and calls
     * {@link ConfigurableElement#create(CLIWrapper)} for them.
     */
    protected void setup(final ModelControllerClient modelControllerClient) throws Exception {
        configurableElements = getConfigurableElements();

        if (configurableElements == null || configurableElements.length == 0) {
            LOGGER.warn("Empty Elytron configuration.");
            return;
        }

        try (CLIWrapper cli = new CLIWrapper(true)) {
            for (final ConfigurableElement configurableElement : configurableElements) {
                LOGGER.infov("Adding element {0} ({1})", configurableElement.getName(),
                        configurableElement.getClass().getSimpleName());
                configurableElement.create(modelControllerClient, cli);
            }
        }
        ServerReload.reloadIfRequired(modelControllerClient);
    }

    /**
     * Reverts configuration changes done by {@link #setup(ModelControllerClient)} method - i.e. calls {@link ConfigurableElement#remove(CLIWrapper)} method
     * on instances provided by {@link #getConfigurableElements()} (in reverse order).
     */
    protected void tearDown(ModelControllerClient modelControllerClient) throws Exception {
        if (configurableElements == null || configurableElements.length == 0) {
            LOGGER.warn("Empty Elytron configuration.");
            return;
        }

        try (CLIWrapper cli = new CLIWrapper(true)) {
            final ListIterator<ConfigurableElement> reverseConfigIt = Arrays.asList(configurableElements)
                    .listIterator(configurableElements.length);
            while (reverseConfigIt.hasPrevious()) {
                final ConfigurableElement configurableElement = reverseConfigIt.previous();
                LOGGER.infov("Removing element {0} ({1})", configurableElement.getName(),
                        configurableElement.getClass().getSimpleName());
                configurableElement.remove(modelControllerClient, cli);
            }
        }
        this.configurableElements = null;
        ServerReload.reloadIfRequired(modelControllerClient);
    }

    /**
     * Returns not-{@code null} array of configurations to be created by this server setup task.
     *
     * @return not-{@code null} array of instances to be created
     */
    protected abstract ConfigurableElement[] getConfigurableElements();
}
