/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests of running domain hosts in admin-only move.
 */
@Ignore("https://issues.redhat.com/browse/WFCORE-6032")
public class AdminOnlyPolicyTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final long initTime = System.currentTimeMillis();
    private static int secondaryCount;

    private static int exitCodeTimeout = TimeoutUtil.adjust(30);

    @BeforeClass
    public static void setupDomain() throws Exception {

        testSupport = DomainTestSupport.createAndStartSupport(DomainTestSupport.Configuration.create(AdminOnlyPolicyTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", null));
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();

        testSupport = null;
    }

    @After
    public void stopSecondSecondary() throws Exception {
        if (domainSecondaryLifecycleUtil != null) {
            domainSecondaryLifecycleUtil.stop();
            domainSecondaryLifecycleUtil = null;
        }
    }

    @Test
    public void testAllowNoConfigWithDiscovery() throws URISyntaxException, IOException {
        createSecondSecondary(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG, true, false);
        validateProfiles();
    }

    @Test
    public void testAllowNoConfigWithoutDiscovery() throws URISyntaxException, IOException {
        createSecondSecondary(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG, false, false);
        validateProfiles();
    }

    @Test
    public void testAllowNoConfigWithCachedDC() throws URISyntaxException, IOException {
        createSecondSecondary(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG, false, true);
        validateProfiles("cached-remote-test");
    }

    @Test
    public void testFetchFromPrimaryWithDiscovery() throws URISyntaxException, IOException {
        String hostName = createSecondSecondary(AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER, true, false);
        validateProfiles("default");

        // Now we validate that we can pull down further data if needed
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, hostName), PathElement.pathElement(SERVER_CONFIG, "other1"));
        ModelNode op = Util.createAddOperation(pa);
        op.get(GROUP).set("other-server-group");
        executeForResult(domainSecondaryLifecycleUtil.getDomainClient(), op);
        // This should have pulled down the 'other' profile
        validateProfiles("default", "other");
    }

    @Test
    public void testChangeProfile() throws URISyntaxException, IOException {
        String hostName = createSecondSecondary(AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER, true, false);
        validateProfiles("default");

        // Now we validate that we can pull down further data if needed
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, hostName), PathElement.pathElement(SERVER_CONFIG, "other1"));
        ModelNode op = Util.createAddOperation(pa);
        op.get(GROUP).set("other-server-group");
        executeForResult(domainSecondaryLifecycleUtil.getDomainClient(), op);
        // This should have pulled down the 'other' profile
        validateProfiles("default", "other");
    }

    @Test
    public void testFetchFromPrimaryWithoutDiscovery() throws URISyntaxException, InterruptedException, TimeoutException {
        try {
            createSecondSecondary(AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER, false, false);
            Assert.fail("secondSecondaryLifecyleUtil should not have started");
        } catch (RuntimeException e) {
            Assert.assertTrue(domainSecondaryLifecycleUtil.awaitForProcessExitCode(exitCodeTimeout) >= 0);
        }
    }

    @Test
    public void testFetchFromPrimaryWithCachedDC() throws URISyntaxException, IOException {
        createSecondSecondary(AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER, false, true);
        validateProfiles("cached-remote-test");
    }

    @Test
    public void testRequireLocalConfigWithDiscovery() throws URISyntaxException, InterruptedException, TimeoutException {
        try {
            createSecondSecondary(AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG, true, false);
            Assert.fail("secondSecondaryLifecyleUtil should not have started");
        } catch (RuntimeException e) {
            Assert.assertTrue(domainSecondaryLifecycleUtil.awaitForProcessExitCode(exitCodeTimeout) >= 0);
        }
    }

    @Test
    public void testRequireLocalConfigWithoutDiscovery() throws URISyntaxException, InterruptedException, TimeoutException {
        try {
            createSecondSecondary(AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG, false, false);
            Assert.fail("secondSecondaryLifecyleUtil should not have started");
        } catch (RuntimeException e) {
            Assert.assertTrue(domainSecondaryLifecycleUtil.awaitForProcessExitCode(exitCodeTimeout) >= 0);
        }
    }

    @Test
    public void testRequireLocalConfigWithCachedDC() throws URISyntaxException, IOException {
        createSecondSecondary(AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG, false, true);
        validateProfiles("cached-remote-test");
    }

    private String createSecondSecondary(AdminOnlyDomainConfigPolicy policy, boolean discovery, boolean cachedDC) throws URISyntaxException {

        String hostName = "secondary-" + initTime + "-" + (secondaryCount++);

        String hostConfigPath = "host-configs/" + (discovery ? "admin-only-discovery.xml" : "admin-only-no-discovery.xml");

        WildFlyManagedConfiguration secondaryConfig = DomainTestSupport.getSecondaryConfiguration(hostName, hostConfigPath,
                getClass().getSimpleName(), false);
        secondaryConfig.setHostControllerManagementPort(29999);
        secondaryConfig.setAdminOnly(true);
        secondaryConfig.addHostCommandLineProperty("-Djboss.test.admin-only-policy=" + policy.toString());
        secondaryConfig.addHostCommandLineProperty("-Djboss.host.name=" + hostName);
        if (cachedDC) {
            secondaryConfig.setCachedDC(true);
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            URL url = tccl.getResource("domain-configs/domain.cached-remote.xml");
            assert url != null;
            secondaryConfig.setDomainConfigFile(new File(url.toURI()).getAbsolutePath());
        }
        domainSecondaryLifecycleUtil = new DomainLifecycleUtil(secondaryConfig, testSupport.getSharedClientConfiguration());
        domainSecondaryLifecycleUtil.start();

        return hostName;
    }

    private void validateProfiles(String... expectedNames) throws IOException {
        Set<String> set = new HashSet<String>(Arrays.asList(expectedNames));
        ModelNode op = Util.createEmptyOperation(ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(ModelDescriptionConstants.CHILD_TYPE).set(ModelDescriptionConstants.PROFILE);
        ModelNode result = executeForResult(domainSecondaryLifecycleUtil.getDomainClient(), op);
        Assert.assertEquals(result.toString(), ModelType.LIST, result.getType());
        Assert.assertEquals(result.toString(), set.size(), result.asInt());
        for (ModelNode profile : result.asList()) {
            String name = profile.asString();
            Assert.assertTrue(name, set.remove(name));
        }
        Assert.assertTrue(set.toString(), set.isEmpty());
    }

    private ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws IOException {
        final ModelNode result = client.execute(operation);
        return validateResponse(result);
    }

    private ModelNode validateResponse(ModelNode response) {
        return validateResponse(response, true);
    }

    private ModelNode validateResponse(ModelNode response, boolean validateResult) {

        if(! SUCCESS.equals(response.get(OUTCOME).asString())) {
            System.out.println("Failed response:");
            System.out.println(response);
            Assert.fail(response.get(FAILURE_DESCRIPTION).toString());
        }

        if (validateResult) {
            Assert.assertTrue("result exists", response.has(RESULT));
        }
        return response.get(RESULT);
    }
}
