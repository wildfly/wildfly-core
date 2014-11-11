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

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Emanuel Muckenhuber
 */
public class WildcardOperationsTestCase {

    private static final String WILDCARD = "*";

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(WildcardOperationsTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testBasicWildcardOperations() throws IOException, MgmtOperationException {

        final ModelNode address = new ModelNode();
        address.setEmptyList();

        // host=*
        address.add(HOST, WILDCARD);
        executeReadResource(address, domainMasterLifecycleUtil.getDomainClient());

        // host=*,server=*
        address.add(RUNNING_SERVER, WILDCARD);
        executeReadResource(address, domainMasterLifecycleUtil.getDomainClient());

        // host=*,server=*,subsystem=*
        address.add(SUBSYSTEM, WILDCARD);
        ModelNode result = executeReadResource(address, domainMasterLifecycleUtil.getDomainClient());

        assertTrue(result.toString(), false);
        address.setEmptyList();

    }

    @Test
    public void testNormalCompositeOperation() throws IOException, MgmtOperationException {

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        final ModelNode steps = composite.get(STEPS);

        final ModelNode address = new ModelNode();
        address.setEmptyList();

        // host=slave
        address.add(HOST, "slave");
        steps.add().set(createReadResourceOperation(address));

        // host=slave,server=main-three
        address.add(RUNNING_SERVER, "main-three");
        steps.add().set(createReadResourceOperation(address));

        // host=slave,server=main-three,subsystem=io
        address.add(SUBSYSTEM, "io");
        steps.add().set(createReadResourceOperation(address));

        // add steps involving a different host
        address.setEmptyList();

        // host=master
        address.add(HOST, "master");
        steps.add().set(createReadResourceOperation(address));

        // host=master,server=main-one
        address.add(RUNNING_SERVER, "main-one");
        steps.add().set(createReadResourceOperation(address));

        // host=master,server=main-one,subsystem=io
        address.add(SUBSYSTEM, "io");
        steps.add().set(createReadResourceOperation(address));

        final ModelNode response = domainMasterLifecycleUtil.getDomainClient().execute(composite);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(RESULT));

        int i = 0;
        for (Property property : response.get(RESULT).asPropertyList()) {
            assertEquals(property.getName() + " from " + response, "step-" + (++i), property.getName());
            ModelNode item = property.getValue();
            assertEquals(property.getName() + " from " + response, ModelType.OBJECT, item.getType());
            assertEquals(property.getName() + " from " + response, SUCCESS, item.get(OUTCOME).asString());
            assertTrue(property.getName() + " from " + response, item.hasDefined(RESULT));
            ModelNode itemResult = item.get(RESULT);
            assertEquals(property.getName() + " result " + itemResult, ModelType.OBJECT, itemResult.getType());
            switch (i) {
                case 1:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(RUNNING_SERVER));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(DIRECTORY_GROUPING));
                    assertEquals(property.getName() + " result " + itemResult, "slave", itemResult.get(NAME).asString());
                    break;
                case 2:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-three", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "slave", itemResult.get(HOST).asString());
                    break;
                case 3:
                case 6:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined("buffer-pool"));
                    break;
                case 4:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(RUNNING_SERVER));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(DIRECTORY_GROUPING));
                    assertEquals(property.getName() + " result " + itemResult, "master", itemResult.get(NAME).asString());
                    break;
                case 5:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-one", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "master", itemResult.get(HOST).asString());
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        address.setEmptyList();
        steps.setEmptyList();

    }

    @Test
    public void testCompositeOperationWithWildCards() throws IOException, MgmtOperationException {

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        final ModelNode steps = composite.get(STEPS);

        final ModelNode address = new ModelNode();
        address.setEmptyList();

        // host=*
        address.add(HOST, WILDCARD);
        steps.add().set(createReadResourceOperation(address));

        // host=*,server=*
        address.add(RUNNING_SERVER, WILDCARD);
        steps.add().set(createReadResourceOperation(address));

        // host=*,server=*,subsystem=*
        address.add(SUBSYSTEM, WILDCARD);
        steps.add().set(createReadResourceOperation(address));

        final ModelNode response = domainMasterLifecycleUtil.getDomainClient().execute(composite);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(RESULT));

        for (Property property : response.get(RESULT).asPropertyList()) {
            assertEquals(property.getName() + " from " + response.toString(), ModelType.LIST, property.getValue().get(RESULT).getType());
        }

        address.setEmptyList();
        steps.setEmptyList();

    }

    static ModelNode executeReadResource(final ModelNode address, final ModelControllerClient client) throws IOException, MgmtOperationException {
        final ModelNode operation = createReadResourceOperation(address);
        return assertWildcardResult(operation, client);
    }

    static ModelNode createReadResourceOperation(final ModelNode address) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);
        return operation;
    }

    static ModelNode assertWildcardResult(final ModelNode operation, final ModelControllerClient client) throws IOException, MgmtOperationException {
        final ModelNode result = DomainTestUtils.executeForResult(operation, client);

        assertEquals(ModelType.LIST, result.getType());

        final PathAddress toMatch = PathAddress.pathAddress(operation.get(OP_ADDR));
        for (final ModelNode r : result.asList()) {
            assertMatchingAddress(toMatch, r);
            assertTrue(r.hasDefined(RESULT));
        }
        return result;
    }

    static void assertMatchingAddress(PathAddress toMatch, ModelNode node) {
        assertTrue(node.hasDefined(OP_ADDR));
        PathAddress testee = PathAddress.pathAddress(node.get(OP_ADDR));
        assertEquals(testee + " length matches " + toMatch, toMatch.size(), testee.size());
        for (int i = 0; i < toMatch.size(); i++) {
            PathElement matchElement = toMatch.getElement(i);
            PathElement testeeElement = testee.getElement(i);
            assertEquals(testee.toString(), matchElement.getKey(), testeeElement.getKey());
            assertFalse(testeeElement + " is multi-target", testeeElement.isMultiTarget());
            if (!matchElement.isWildcard()) {
                if (matchElement.isMultiTarget()) {
                    boolean matched = false;
                    for (String option : matchElement.getSegments()) {
                        if (option.equals(testeeElement.getValue())) {
                            matched = true;
                            break;
                        }
                    }
                    assertTrue(testeeElement + " value in " + matchElement.getValue(), matched);
                } else {
                    assertEquals(matchElement.getValue(), testeeElement.getValue());
                }
            }
        }
    }

}
