/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.management;

import jakarta.inject.Inject;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests the configuration of Server Service Thread Pool CorePoolSize and MaxPoolSize by using system properties.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ServerServiceThreadPoolTestCase {
    // There are the current default values, must be in sync with ServerService.ServerExecutorService field values
    private static String DEFAULT_CORE_POOL_SIZE = "1";
    private static String DEFAULT_MAX_POOL_SIZE = "1024";

    @Inject
    private ServerController container;

    private ManagementClient managementClient;
    private String jbossArgs;
    private JMXConnector connector;
    private MBeanServerConnection connection;

    @After
    public void stopContainer() throws Exception {
        if (jbossArgs == null) {
            System.clearProperty("jboss.args");
        } else {
            System.setProperty("jboss.args", jbossArgs);
        }
        Assert.assertNotNull(connector);
        connector.close();
        Assert.assertNotNull(container);
        container.stop();
    }

    @Test
    public void testServerServicePoolConfiguration() throws Exception {
        final String corePoolSize = "0";
        final String maxPoolSize = "1030";

        starContainer(corePoolSize, maxPoolSize);
        ObjectName objectName = new ObjectName("jboss.threads:name=\"ServerService\",type=thread-pool");
        Integer currentCorePoolSize = (Integer) connection.getAttribute(objectName, "CorePoolSize");
        Integer currentMaxPoolSize = (Integer) connection.getAttribute(objectName, "MaximumPoolSize");

        Assert.assertEquals(corePoolSize, String.valueOf(currentCorePoolSize));
        Assert.assertEquals(maxPoolSize, String.valueOf(currentMaxPoolSize));
    }

    @Test
    public void testServerServicePoolConfigurationInvalidValues() throws Exception {
        final String corePoolSize = "invalid_value";
        final String maxPoolSize = "";

        starContainer(corePoolSize, maxPoolSize);
        ObjectName objectName = new ObjectName("jboss.threads:name=\"ServerService\",type=thread-pool");
        Integer currentCorePoolSize = (Integer) connection.getAttribute(objectName, "CorePoolSize");
        Integer currentMaxPoolSize = (Integer) connection.getAttribute(objectName, "MaximumPoolSize");

        Assert.assertEquals(DEFAULT_CORE_POOL_SIZE, String.valueOf(currentCorePoolSize));
        Assert.assertEquals(DEFAULT_MAX_POOL_SIZE, String.valueOf(currentMaxPoolSize));
    }

    public void starContainer(String corePoolSize, String maxPoolSize) throws Exception {
        jbossArgs = System.getProperty("jboss.args");
        String serverArgs = jbossArgs == null ? "" : jbossArgs;

        if (corePoolSize != null) {
            serverArgs += " -Dorg.jboss.as.server-service.core.threads=" + corePoolSize;
        }

        if (maxPoolSize != null) {
            serverArgs += " -Dorg.jboss.as.server-service.max.threads=" + maxPoolSize;
        }

        System.setProperty("jboss.args", serverArgs);
        Assert.assertNotNull(container);
        container.start();
        managementClient = container.getClient();
        setupAndGetConnection();
    }

    private void setupAndGetConnection() throws Exception {
        String urlString = System.getProperty("jmx.service.url", "service:jmx:remote+http://" + managementClient.getMgmtAddress() + ":" + managementClient.getMgmtPort());
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);
        connector = JMXConnectorFactory.connect(serviceURL, null);
        connection = connector.getMBeanServerConnection();
    }
}
