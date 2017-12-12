/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.test.integration.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jboss.as.jmx.model.ModelControllerMBeanHelper;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.jmx.Dynamic;
import org.wildfly.test.jmx.ServiceActivatorDeploymentUtil;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildflyTestRunner.class)
public class ModelControllerMBeanTestCase {

    private static final String RESOLVED_DOMAIN = "jboss.as";
    private static final String DEPLOYMENT = "test-jmx.jar";

    static final ObjectName RESOLVED_MODEL_FILTER = createObjectName(RESOLVED_DOMAIN  + ":*");
    static final ObjectName RESOLVED_ROOT_MODEL_NAME = ModelControllerMBeanHelper.createRootObjectName(RESOLVED_DOMAIN);

    static JMXConnector connector;
    static MBeanServerConnection connection;

    @Inject
    private org.wildfly.core.testrunner.ManagementClient managementClient;


    @Before
    public void initialize() throws Exception {
        connection = setupAndGetConnection();
    }

    @After
    public void closeConnection() throws Exception {
        IoUtils.safeClose(connector);
    }

    /**
     * Test that all the MBean infos can be read properly
     */
    @Test
    public void testAllMBeanInfos() throws Exception {
        Set<ObjectName> names = connection.queryNames(RESOLVED_MODEL_FILTER, null);
        Map<ObjectName, Exception> failedInfos = new HashMap<ObjectName, Exception>();

        for (ObjectName name : names) {
            try {
                Assert.assertNotNull(connection.getMBeanInfo(name));
            } catch (Exception e) {
                //System.out.println("Error getting info for " + name);
                failedInfos.put(name, e);
            }
        }
        Assert.assertTrue(failedInfos.toString(), failedInfos.isEmpty());
    }

    @Test
    public void testSystemProperties() throws Exception {
        String[] initialNames = getSystemPropertyNames();

        ObjectName testName = new ObjectName(RESOLVED_DOMAIN + ":system-property=mbeantest");
        assertNoMBean(testName);

        connection.invoke(RESOLVED_ROOT_MODEL_NAME, "addSystemProperty", new Object[] {"mbeantest", "800"}, new String[] {String.class.getName(), String.class.getName()});
        try {
            String[] newNames = getSystemPropertyNames();
            Assert.assertEquals(initialNames.length + 1, newNames.length);
            boolean found = false;
            for (String s : newNames) {
                if (s.equals("mbeantest")) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue(found);
            Assert.assertNotNull(connection.getMBeanInfo(new ObjectName(RESOLVED_DOMAIN + ":system-property=mbeantest")));
        } finally {
            connection.invoke(new ObjectName(RESOLVED_DOMAIN + ":system-property=mbeantest"), "remove", new Object[0], new String[0]);
        }

        assertNoMBean(testName);

        Assert.assertEquals(initialNames.length, getSystemPropertyNames().length);
    }

    @Test
    public void testDeploymentViaJmx() throws Exception {
        ObjectName testDeploymentModelName = new ObjectName("" + RESOLVED_DOMAIN + ":deployment=" + DEPLOYMENT);
        assertNoMBean(testDeploymentModelName);
        File jarFile = new File(DEPLOYMENT);
        Files.deleteIfExists(jarFile.toPath());
        ServiceActivatorDeploymentUtil.createServiceActivatorDeployment(jarFile, "jboss.test:service="+DEPLOYMENT, Dynamic.class);
        try (InputStream in = Files.newInputStream(jarFile.toPath())) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            int i = in.read();
            while (i != -1) {
                bout.write(i);
                i = in.read();
            }

            byte[] bytes = bout.toByteArray();

            //Upload the content
            byte[] hash = (byte[]) connection.invoke(RESOLVED_ROOT_MODEL_NAME, "uploadDeploymentBytes", new Object[]{bytes}, new String[]{byte.class.getName()});

            String[] names = {"hash"};
            String[] descriptions = { "the content hash" };
            OpenType<?>[] types = { new ArrayType<>(SimpleType.BYTE, true)};
            CompositeType contentType = new CompositeType("contents", "the contents", names, descriptions, types);
            Map<String, Object> values = Collections.singletonMap("hash", hash);
            CompositeData contents = new CompositeDataSupport(contentType, values);

            //Deploy it
            connection.invoke(RESOLVED_ROOT_MODEL_NAME,
                    "addDeployment",
                    new Object[]{DEPLOYMENT, DEPLOYMENT, new CompositeData[]{contents}, Boolean.TRUE},
                    new String[]{String.class.getName(), String.class.getName(), CompositeData.class.getName(), Boolean.class.getName()});

            //Make sure the test deployment mbean and the management model mbean for the deployment are there
            Assert.assertTrue((Boolean) connection.getAttribute(testDeploymentModelName, "enabled"));

            //Undeploy
            connection.invoke(testDeploymentModelName, "undeploy", new Object[0], new String[0]);

            //Check the app was undeployed
            Assert.assertFalse((Boolean) connection.getAttribute(testDeploymentModelName, "enabled"));

            //Remove
            connection.invoke(testDeploymentModelName, "remove", new Object[0], new String[0]);
            assertNoMBean(testDeploymentModelName);
        } finally {
            Files.deleteIfExists(jarFile.toPath());
        }
    }

    private void assertNoMBean(ObjectName name) throws Exception {
        try {
            connection.getMBeanInfo(name);
            Assert.fail("Should not have found mbean with nane " + name);
        } catch (InstanceNotFoundException expected) {
        }
    }

    private String[] getSystemPropertyNames() throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        op.get(OP_ADDR).setEmptyList();
        op.get(CHILD_TYPE).set("system-property");

        ModelNode result = managementClient.getControllerClient().execute(op);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
        List<ModelNode> propertyNames = result.get(RESULT).asList();
        List<String> list = new ArrayList<>();
        for (ModelNode propertyName : propertyNames) {
            String asString = propertyName.asString();
            list.add(asString);
        }
        return list.toArray(new String[propertyNames.size()]);
    }



    private static ObjectName createObjectName(String name) {
        try {
            return ObjectName.getInstance(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MBeanServerConnection setupAndGetConnection() throws Exception {
        // Make sure that we can connect to the MBean server
        String urlString = System
                .getProperty("jmx.service.url", "service:jmx:remote+http://" + managementClient.getMgmtAddress() + ":" + managementClient.getMgmtPort());
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);
        connector = JMXConnectorFactory.connect(serviceURL, null);
        return connector.getMBeanServerConnection();
    }

}
