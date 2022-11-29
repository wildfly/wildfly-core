/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.jmx;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.ServerEnvironmentResourceDescription;
import org.jboss.as.server.jmx.PluggableMBeanServer;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PluggableMBeanServerTestCase extends AbstractSubsystemTest {

    private static final String TYPE_STANDALONE = "STANDALONE";

    private static final ObjectName NAME = createName("test.domain:bean=test-null");
    private PluggableMBeanServer server;

    public PluggableMBeanServerTestCase() {
        super(JMXExtension.SUBSYSTEM_NAME, new JMXExtension());
    }

    @Before
    public void createServer() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(new BaseAdditionalInitialization());
        String subsystemXml =
                "<subsystem xmlns=\"" + Namespace.CURRENT.getUriString() + "\">" +
                "   <expose-resolved-model domain-name=\"jboss.as\" proper-property-format=\"false\"/>" +
                "   <expose-expression-model domain-name=\"jboss.as.expr\"/>" +
                "   <sensitivity non-core-mbeans=\"true\"/>" +
                "</subsystem>";
        builder.setSubsystemXml(subsystemXml);
        KernelServices services = builder.build();

        ServiceController<?> controller = services.getContainer().getRequiredService(MBeanServerService.SERVICE_NAME);
        server = (PluggableMBeanServer)controller.awaitValue(5, TimeUnit.MINUTES);
    }

    @Test
    public void testNullObjectNameAndMBeanRegistration() throws Exception {
        assertNoMBean(NAME);
        server.registerMBean(new TestBean(NAME), null);
        Assert.assertNotNull(server.getMBeanInfo(NAME));
        server.unregisterMBean(NAME);
        assertNoMBean(NAME);
    }

    @Test
    public void testNullObjectNameAndNoMBeanRegistrationFails() throws Exception {
        assertNoMBean(NAME);
        try {
            server.registerMBean(new TestBean2(), null);
            Assert.fail("Should not have been able to register with a null object name");
        } catch (RuntimeOperationsException expected) {
        }
        assertNoMBean(NAME);
    }

    @Test
    public void testReservedDomainMBeanRegistrationFails() throws Exception {
        reservedDomainTest("jboss.as:bean=test-null", null);
        reservedDomainTest("jboss.as.expr:bean=test-null", null);
        reservedDomainTest("jboss.as:bean=test-null", NAME);
        reservedDomainTest("jboss.as.expr:bean=test-null", NAME);
    }

    @Test
    public void testNotificationTccl() throws Exception {
        ObjectName objName = createName("test.domain:bean=test-tccl");
        server.registerMBean(new TestBean(objName), objName);
        ClassLoader oldTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        ClassLoader newOutTccl = new URLClassLoader(new URL[]{}, oldTccl); // creating a new class loader here
        WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(newOutTccl);
        try {
            final AtomicBoolean differ = new AtomicBoolean(false);
            NotificationFilterSupport filter = new NotificationFilterSupport();
            filter.enableType("testtccl");
            server.addNotificationListener(objName, new NotificationListener() {
                @Override
                public void handleNotification(Notification notification, Object handback) {
                    Assert.assertEquals("callback", handback.toString());
                    ClassLoader newInTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
                    Assert.assertNotEquals(newInTccl, newOutTccl);
                    differ.set(true);
                }
            }, filter, "callback");
            server.invoke(objName, "proceed", null, null);
            Assert.assertTrue(differ.get());
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
        }
    }

    private void reservedDomainTest(String name, ObjectName originalObjectName) throws Exception {
        ObjectName objName = createName(name);
        assertNoMBean(objName);
        try {
            server.registerMBean(new TestBean(objName), originalObjectName);
            Assert.fail("Should not have been able to register with name " + name);
        } catch (RuntimeOperationsException expected) {
        }
        assertNoMBean(objName);
    }

    private void assertNoMBean(ObjectName name) {
        try {
            server.getMBeanInfo(name);
            Assert.fail("Should not have been able to find " + name);
        } catch (InstanceNotFoundException expected) {
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        } catch (ReflectionException e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectName createName(String s) {
        try {
            return ObjectName.getInstance(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface TestBeanMBean {
        void proceed();
    }

    public static class TestBean extends NotificationBroadcasterSupport implements TestBeanMBean, MBeanRegistration {

        private final ObjectName objName;

        public TestBean(ObjectName objName) {
            this.objName = objName;
        }

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
            return objName;
        }

        @Override
        public void postRegister(Boolean registrationDone) {
        }

        @Override
        public void preDeregister() throws Exception {
        }

        @Override
        public void postDeregister() {
        }

        @Override
        public void proceed() {
            sendNotification(new Notification("testtccl", 1, 1));
        }
    }

    public interface TestBean2MBean {

    }

    public static class TestBean2 implements TestBean2MBean {

    }

    private static class BaseAdditionalInitialization extends AdditionalInitialization {

        @Override
        protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource,
                                        ManagementResourceRegistration rootRegistration) {
            rootRegistration.registerReadOnlyAttribute(ServerEnvironmentResourceDescription.LAUNCH_TYPE, new OperationStepHandler() {

                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.getResult().set(TYPE_STANDALONE);
                }
            });
        }
    }

}
