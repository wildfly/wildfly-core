/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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

package org.wildfly.extension.elytron;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Roles;

import java.io.IOException;

/**
 * @author <a href="mailto:mmazanek@redhat.com">Martin Mazanek</a>
 */
public class RoleMappersTestCase extends AbstractSubsystemBaseTest {
    private KernelServices services = null;

    public RoleMappersTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("role-mappers-test.xml");
    }

    private void init(String... domainsToActivate) throws Exception {
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("role-mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain1");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain2");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain3");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain4");
    }

    @Test
    public void testMappedRoleMapper() throws Exception {
        init("TestDomain1");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain1");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user1");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("mappedGroup"));
        Assert.assertFalse(roles.contains("firstGroup"));
        Assert.assertFalse(roles.contains("secondGroup"));
        Assert.assertFalse(roles.contains("notInThisGroup"));
        Assert.assertEquals("user1", identity.getPrincipal().getName());
    }

    @Test
    public void testKeepMappedRoleMapper() throws Exception {
        init("TestDomain2");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain2");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user1");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("mappedGroup"));
        Assert.assertTrue(roles.contains("firstGroup"));
        Assert.assertFalse(roles.contains("secondGroup"));
        Assert.assertFalse(roles.contains("notInThisGroup"));
        Assert.assertEquals("user1", identity.getPrincipal().getName());
    }

    @Test
    public void testKeepNonMappedRoleMapper() throws Exception {
        init("TestDomain3");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain3");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user1");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("mappedGroup"));
        Assert.assertFalse(roles.contains("firstGroup"));
        Assert.assertTrue(roles.contains("secondGroup"));
        Assert.assertFalse(roles.contains("notInThisGroup"));
        Assert.assertEquals("user1", identity.getPrincipal().getName());
    }

    @Test
    public void testKeepBothMappedRoleMapper() throws Exception {
        init("TestDomain4");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain4");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user1");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("mappedGroup"));
        Assert.assertTrue(roles.contains("firstGroup"));
        Assert.assertTrue(roles.contains("secondGroup"));
        Assert.assertFalse(roles.contains("notInThisGroup"));
        Assert.assertEquals("user1", identity.getPrincipal().getName());
    }
}
