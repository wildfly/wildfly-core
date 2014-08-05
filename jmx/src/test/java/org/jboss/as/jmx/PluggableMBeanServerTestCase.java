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

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;

import org.jboss.as.jmx.model.ConfiguredDomains;
import org.jboss.as.jmx.model.ModelControllerMBeanServerPlugin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PluggableMBeanServerTestCase {

    static final ObjectName NAME = createName("org.jboss:bean=test-null");
    PluggableMBeanServerImpl server;

    @Before
    public void createServer() {
        server = new PluggableMBeanServerImpl(ManagementFactory.getPlatformMBeanServer());
        server.addPlugin(new ModelControllerMBeanServerPlugin(new ConfiguredDomains("jboss.as", "jboss.as.expr"), null, true, true));
    }

    @Test
    public void testNullObjectNameAndMBeanRegistration() throws Exception {
        assertNoMBean(NAME);
        server.registerMBean(new TestBean(), null);
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
            return ObjectName.getInstance("org.jboss:bean=test-null");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface TestBeanMBean {

    }

    public static class TestBean implements TestBeanMBean, MBeanRegistration {

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
            return ObjectName.getInstance("org.jboss:bean=test-null");
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

    }

    public interface TestBean2MBean {

    }

    public static class TestBean2 implements TestBean2MBean {

    }
}
