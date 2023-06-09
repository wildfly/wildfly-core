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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extension.elytron.common.AbstractElytronSubsystemBaseTest;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Roles;

/**
 * @author <a href="mailto:mmazanek@redhat.com">Martin Mazanek</a>
 */
public class RoleMappersTestCase extends AbstractElytronSubsystemBaseTest {
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
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain1");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain2");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain3");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain4");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain5");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain6");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain7");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain8");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain9");
        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "TestDomain10");
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

    @Test
    public void testRegexRoleMapper() throws Exception {
        init("TestDomain5");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain5");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user2");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("application-user"));
        Assert.assertFalse(roles.contains("123-user"));
        Assert.assertFalse(roles.contains("joe"));
        Assert.assertEquals("user2", identity.getPrincipal().getName());
    }

    @Test
    public void testRegexRoleMapper2() throws Exception {
        init("TestDomain6");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain6");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user3");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("admin"));
        Assert.assertTrue(roles.contains("user"));
        Assert.assertFalse(roles.contains("joe"));
        Assert.assertFalse(roles.contains("application-user"));
        Assert.assertFalse(roles.contains("123-admin-123"));
        Assert.assertFalse(roles.contains("aa-user-aa"));
        Assert.assertEquals("user3", identity.getPrincipal().getName());
    }

    @Test
    public void testRegexRoleMapper3() throws Exception {
        init("TestDomain7");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain7");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user3");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("admin"));
        Assert.assertTrue(roles.contains("user"));
        Assert.assertTrue(roles.contains("joe"));
        Assert.assertFalse(roles.contains("application-user"));
        Assert.assertFalse(roles.contains("123-admin-123"));
        Assert.assertFalse(roles.contains("aa-user-aa"));
        Assert.assertEquals("user3", identity.getPrincipal().getName());
    }

    @Test
    public void testAddRegexRoleMapperWillFailWithInvalidRegexAttribute() throws Exception {
        init();
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR)
                .add("subsystem", "elytron").add("regex-role-mapper", "my-regex-role-mapper");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATTERN).set("*-admin");
        operation.get(ElytronDescriptionConstants.REPLACEMENT).set("$1");
        ModelNode response = services.executeOperation(operation);
        // operation will fail because regex is not valid (starts with asterisk)
        if (! response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
    }

    @Test
    public void testAddRegexRoleMapperReplaceAll() throws Exception {
        init("TestDomain8");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain8");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user4");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user4", identity.getPrincipal().getName());

        Roles roles = identity.getRoles();
        Assert.assertFalse(roles.contains("app-user"));
        Assert.assertFalse(roles.contains("app-user-first-time-user"));
        Assert.assertFalse(roles.contains("app-admin-first-time-user"));
        Assert.assertFalse(roles.contains("app-user-first-time-admin"));
        Assert.assertFalse(roles.contains("joe"));

        Assert.assertTrue(roles.contains("app-admin"));
        Assert.assertTrue(roles.contains("app-admin-first-time-admin"));

        context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user7");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user7", identity.getPrincipal().getName());
        roles = identity.getRoles();
        Assert.assertTrue(roles.contains("admin"));
        Assert.assertFalse(roles.contains("user"));
    }

    @Test
    public void testAddRegexRoleMapperWithRegexBoundaries() throws Exception {
        init("TestDomain9");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain9");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user4");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user4", identity.getPrincipal().getName());

        Roles roles = identity.getRoles();
        Assert.assertFalse(roles.contains("app-user"));
        Assert.assertFalse(roles.contains("app-user-first-time-user"));
        Assert.assertFalse(roles.contains("app-admin-first-time-user"));
        Assert.assertFalse(roles.contains("app-user-first-time-admin"));
        Assert.assertFalse(roles.contains("joe"));
        Assert.assertFalse(roles.contains("app-admin"));
        Assert.assertFalse(roles.contains("app-admin-first-time-admin"));

        context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user7");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user7", identity.getPrincipal().getName());

        roles = identity.getRoles();
        Assert.assertTrue(roles.contains("admin"));
        Assert.assertFalse(roles.contains("user"));
    }

    @Test
    public void testAddRegexRoleMapperAggregate() throws Exception {
        init("TestDomain10");

        ServiceName serviceName = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("TestDomain10");
        Assert.assertNotNull(services.getContainer());
        Assert.assertNotNull(services.getContainer().getService(serviceName));
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(domain);

        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user5");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        SecurityIdentity identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user5", identity.getPrincipal().getName());

        Roles roles = identity.getRoles();
        Assert.assertTrue(roles.contains("admin"));
        Assert.assertTrue(roles.contains("guest"));
        Assert.assertFalse(roles.contains("1-user"));
        Assert.assertFalse(roles.contains("user"));

        context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("user6");
        Assert.assertTrue(context.exists());
        Assert.assertTrue(context.authorize());
        context.succeed();
        identity = context.getAuthorizedIdentity();
        Assert.assertEquals("user6", identity.getPrincipal().getName());

        roles = identity.getRoles();
        Assert.assertFalse(roles.contains("admin"));
        Assert.assertFalse(roles.contains("random"));
    }
}
