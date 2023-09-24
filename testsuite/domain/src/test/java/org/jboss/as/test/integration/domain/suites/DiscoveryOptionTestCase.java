/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISCOVERY_OPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.host.controller.discovery.Constants.ACCESS_KEY;
import static org.jboss.as.host.controller.discovery.Constants.LOCATION;
import static org.jboss.as.host.controller.discovery.Constants.SECRET_ACCESS_KEY;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.exists;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests of management operations involving discovery options.
 *
 * @author Farah Juma
 */
public class DiscoveryOptionTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartSupport(DomainTestSupport.Configuration.create(DiscoveryOptionTestCase.class.getName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary-discovery-options.xml"));

        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainTestSuite.stopSupport();
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
    }

    @Test
    public void testAddAndRemoveS3DiscoveryOption() throws Exception {
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode discoveryOptionProperties = new ModelNode();
        discoveryOptionProperties.get(ACCESS_KEY).set("access_key");
        discoveryOptionProperties.get(SECRET_ACCESS_KEY).set("secret_access_key");
        discoveryOptionProperties.get(LOCATION).set("location");

        ModelNode addDiscoveryOption = getS3DiscoveryOptionAddOperation(discoveryOptionProperties);

        // (host=primary),(core-service=discovery-options),(discovery-option=option-one)
        ModelNode newPrimaryDiscoveryOptionAddress = new ModelNode();
        newPrimaryDiscoveryOptionAddress.add("host", "primary");
        newPrimaryDiscoveryOptionAddress.add("core-service", "discovery-options");
        newPrimaryDiscoveryOptionAddress.add("discovery-option", "option-one");
        addAndRemoveDiscoveryOptionTest(primaryClient, newPrimaryDiscoveryOptionAddress, addDiscoveryOption);

        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
        // (host=secondary),(core-service=discovery-options),(discovery-option=option-one)
        ModelNode newSecondaryDiscoveryOptionAddress = new ModelNode();
        newSecondaryDiscoveryOptionAddress.add("host", "secondary");
        newSecondaryDiscoveryOptionAddress.add("core-service", "discovery-options");
        newSecondaryDiscoveryOptionAddress.add("discovery-option", "option-one");
        addAndRemoveDiscoveryOptionTest(secondaryClient, newSecondaryDiscoveryOptionAddress, addDiscoveryOption);
    }

    @Test
    public void testAddAndRemoveStaticDiscoveryOption() throws Exception {
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
        ModelNode addDiscoveryOption = new ModelNode();
        addDiscoveryOption.get(OP).set(ADD);
        addDiscoveryOption.get(HOST).set("127.0.0.2");
        addDiscoveryOption.get(PORT).set("9999");

        // (host=secondary),(core-service=discovery-options),(static-discovery=option-one)
        ModelNode newSecondaryDiscoveryOptionAddress = new ModelNode();
        newSecondaryDiscoveryOptionAddress.add("host", "secondary");
        newSecondaryDiscoveryOptionAddress.add("core-service", "discovery-options");
        newSecondaryDiscoveryOptionAddress.add("static-discovery", "option-one");
        addAndRemoveDiscoveryOptionTest(secondaryClient, newSecondaryDiscoveryOptionAddress, addDiscoveryOption);
    }

    @Test
    public void testDiscoveryOptionsOrdering() throws Exception {
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
        ModelNode discoveryOptionsAddress = new ModelNode();
        discoveryOptionsAddress.add("host", "secondary");
        discoveryOptionsAddress.add("core-service", "discovery-options");
        ModelNode readDiscoveryOptionsOrdering = Util.getReadAttributeOperation(PathAddress.pathAddress(discoveryOptionsAddress), DISCOVERY_OPTIONS);
        ModelNode expectedDiscoveryOptionsOrdering = new ModelNode();
        expectedDiscoveryOptionsOrdering.add("static-discovery", "start-option");
        ModelNode originalOptionsOrdering = new ModelNode();
        originalOptionsOrdering.add("static-discovery", "start-option");

        ModelNode discoveryOptionProperties = new ModelNode();
        discoveryOptionProperties.get(ACCESS_KEY).set("access_key");
        discoveryOptionProperties.get(SECRET_ACCESS_KEY).set("secret_access_key");
        discoveryOptionProperties.get(LOCATION).set("location");
        ModelNode addS3DiscoveryOption = getS3DiscoveryOptionAddOperation(discoveryOptionProperties);

        ModelNode addStaticDiscoveryOption = new ModelNode();
        addStaticDiscoveryOption.get(OP).set(ADD);
        addStaticDiscoveryOption.get(HOST).set("127.0.0.2");
        addStaticDiscoveryOption.get(PORT).set("9999");

        ModelNode result = secondaryClient.execute(readDiscoveryOptionsOrdering);
        ModelNode returnVal = validateResponse(result);
        Assert.assertEquals(originalOptionsOrdering, returnVal);

        // (host=secondary),(core-service=discovery-options),(discovery-option=option-one)
        ModelNode discoveryOptionAddressOne = discoveryOptionsAddress.clone().add("discovery-option", "option-one");
        addDiscoveryOptionTest(secondaryClient, discoveryOptionAddressOne, addS3DiscoveryOption);
        expectedDiscoveryOptionsOrdering.add("discovery-option", "option-one");

        // (host=secondary),(core-service=discovery-options),(static-discovery=option-two)
        ModelNode discoveryOptionAddressTwo = discoveryOptionsAddress.clone().add("static-discovery", "option-two");
        addDiscoveryOptionTest(secondaryClient, discoveryOptionAddressTwo, addStaticDiscoveryOption);
        expectedDiscoveryOptionsOrdering.add("static-discovery", "option-two");

        // (host=secondary),(core-service=discovery-options),(discovery-option=option-three)
        ModelNode discoveryOptionAddressThree = discoveryOptionsAddress.clone().add("discovery-option", "option-three");
        addDiscoveryOptionTest(secondaryClient, discoveryOptionAddressThree, addS3DiscoveryOption);
        expectedDiscoveryOptionsOrdering.add("discovery-option", "option-three");

        result = secondaryClient.execute(readDiscoveryOptionsOrdering);
        returnVal = validateResponse(result);
        Assert.assertEquals(expectedDiscoveryOptionsOrdering, returnVal);

        removeDiscoveryOptionTest(secondaryClient, discoveryOptionAddressOne);
        removeDiscoveryOptionTest(secondaryClient, discoveryOptionAddressTwo);
        removeDiscoveryOptionTest(secondaryClient, discoveryOptionAddressThree);

        result = secondaryClient.execute(readDiscoveryOptionsOrdering);
        returnVal = validateResponse(result);
        Assert.assertEquals(originalOptionsOrdering, returnVal);
    }

    @Test
    public void testOptionsAttribute() throws Exception {
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
        ModelNode discoveryOptionsAddress = new ModelNode();
        discoveryOptionsAddress.add("host", "secondary");
        discoveryOptionsAddress.add("core-service", "discovery-options");
        ModelNode readOptionsAttr = Util.getReadAttributeOperation(PathAddress.pathAddress(discoveryOptionsAddress), ModelDescriptionConstants.OPTIONS);

        ModelNode item = new ModelNode();
        ModelNode value = item.get("static-discovery");
        value.get("name").set("start-option");
        value.get("protocol");
        value.get("host").set(new ValueExpression("${jboss.test.host.primary.address}"));
        value.get("port").set(9999);

        ModelNode expectedOptions = new ModelNode();
        expectedOptions.add(item);
        ModelNode originalOptions = new ModelNode();
        originalOptions.add(item);

        ModelNode discoveryOptionProperties = new ModelNode();
        discoveryOptionProperties.get(ACCESS_KEY).set("access_key");
        discoveryOptionProperties.get(SECRET_ACCESS_KEY).set("secret_access_key");
        discoveryOptionProperties.get(LOCATION).set("location");
        ModelNode addS3DiscoveryOption = getS3DiscoveryOptionAddOperation(discoveryOptionProperties);

        ModelNode addStaticDiscoveryOption = new ModelNode();
        addStaticDiscoveryOption.get(OP).set(ADD);
        addStaticDiscoveryOption.get(HOST).set("127.0.0.2");
        addStaticDiscoveryOption.get(PORT).set("9999");

        ModelNode result = secondaryClient.execute(readOptionsAttr);
        ModelNode returnVal = validateResponse(result);
        Assert.assertEquals(originalOptions, returnVal);

        // (host=secondary),(core-service=discovery-options),(discovery-option=option-one)
        ModelNode discoveryOptionAddressOne = discoveryOptionsAddress.clone().add("discovery-option", "option-one");
        addDiscoveryOptionTest(secondaryClient, discoveryOptionAddressOne, addS3DiscoveryOption);

        item = new ModelNode();
        value = item.get("custom-discovery");
        value.get("name").set("option-one");
        value.get("code").set("org.jboss.as.host.controller.discovery.S3Discovery");
        value.get("module").set("org.jboss.as.host.controller.discovery");
        value.get("properties").set(discoveryOptionProperties);
        expectedOptions.add(item);

        // (host=secondary),(core-service=discovery-options),(static-discovery=option-two)
        ModelNode discoveryOptionAddressTwo = discoveryOptionsAddress.clone().add("static-discovery", "option-two");
        addDiscoveryOptionTest(secondaryClient, discoveryOptionAddressTwo, addStaticDiscoveryOption);
        item = new ModelNode();
        value = item.get("static-discovery");
        value.get("name").set("option-two");
        value.get("protocol");
        value.get("host").set("127.0.0.2");
        value.get("port").set(9999);
        expectedOptions.add(item);

        // (host=secondary),(core-service=discovery-options),(discovery-option=option-three)
        ModelNode discoveryOptionAddressThree = discoveryOptionsAddress.clone().add("discovery-option", "option-three");
        addDiscoveryOptionTest(secondaryClient, discoveryOptionAddressThree, addS3DiscoveryOption);
        item = new ModelNode();
        value = item.get("custom-discovery");
        value.get("name").set("option-three");
        value.get("code").set("org.jboss.as.host.controller.discovery.S3Discovery");
        value.get("module").set("org.jboss.as.host.controller.discovery");
        value.get("properties").set(discoveryOptionProperties);
        expectedOptions.add(item);

        result = secondaryClient.execute(readOptionsAttr);
        returnVal = validateResponse(result);
        Assert.assertEquals(expectedOptions, returnVal);

        removeDiscoveryOptionTest(secondaryClient, discoveryOptionAddressOne);
        removeDiscoveryOptionTest(secondaryClient, discoveryOptionAddressTwo);
        removeDiscoveryOptionTest(secondaryClient, discoveryOptionAddressThree);

        result = secondaryClient.execute(readOptionsAttr);
        returnVal = validateResponse(result);
        Assert.assertEquals(originalOptions, returnVal);
    }

    private void addAndRemoveDiscoveryOptionTest(ModelControllerClient client, ModelNode discoveryOptionAddress, ModelNode addDiscoveryOption) throws Exception {
        addDiscoveryOptionTest(client, discoveryOptionAddress, addDiscoveryOption);
        removeDiscoveryOptionTest(client, discoveryOptionAddress);
    }

    private ModelNode getS3DiscoveryOptionAddOperation(ModelNode discoveryOptionProperties) {
        ModelNode addDiscoveryOption = new ModelNode();
        addDiscoveryOption.get(OP).set(ADD);
        addDiscoveryOption.get(CODE).set("org.jboss.as.host.controller.discovery.S3Discovery");
        addDiscoveryOption.get(MODULE).set("org.jboss.as.host.controller.discovery");
        addDiscoveryOption.get(PROPERTIES).set(discoveryOptionProperties);
        return addDiscoveryOption;
    }

    private void addDiscoveryOptionTest(ModelControllerClient client, ModelNode discoveryOptionAddress, ModelNode addDiscoveryOption) throws Exception {
        addDiscoveryOption.get(OP_ADDR).set(discoveryOptionAddress);

        Assert.assertFalse(exists(discoveryOptionAddress, client));
        ModelNode result = client.execute(addDiscoveryOption);
        validateResponse(result, false);
        Assert.assertTrue(exists(discoveryOptionAddress, client));
    }

    private void removeDiscoveryOptionTest(ModelControllerClient client, ModelNode discoveryOptionAddress) throws Exception {
        final ModelNode removeDiscoveryOption = new ModelNode();
        removeDiscoveryOption.get(OP).set(REMOVE);
        removeDiscoveryOption.get(OP_ADDR).set(discoveryOptionAddress);

        ModelNode result = client.execute(removeDiscoveryOption);
        validateResponse(result);
        Assert.assertFalse(exists(discoveryOptionAddress, client));
    }
}
