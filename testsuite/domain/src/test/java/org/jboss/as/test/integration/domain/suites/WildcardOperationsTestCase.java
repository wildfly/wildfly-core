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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Emanuel Muckenhuber
 */
public class WildcardOperationsTestCase {

    private static final String WILDCARD = "*";
    private static final PathElement SYS_PROP_ELEMENT = PathElement.pathElement(SYSTEM_PROPERTY, "wildcard-composite-op");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(WildcardOperationsTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @After
    public void cleanup() throws IOException {
        ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT));
        domainMasterLifecycleUtil.getDomainClient().execute(op);
    }

    @Test
    public void testBasicWildcardOperations() throws IOException, MgmtOperationException {
        // host=*
        PathAddress hosts = PathAddress.pathAddress(HOST, WILDCARD);
        executeReadResource(hosts.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
        executeReadResourceDescription(hosts.toModelNode(), domainMasterLifecycleUtil.getDomainClient());

        // host=*,server=*
        PathAddress servers = hosts.append(RUNNING_SERVER, WILDCARD);
        executeReadResource(servers.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
        ServerWildcardChecker serverChecker = new ServerWildcardChecker(servers);
        executeReadResourceDescription(servers.toModelNode(), domainMasterLifecycleUtil.getDomainClient(), serverChecker);
        Assert.assertTrue(serverChecker.seenWildcardServer);
        Assert.assertTrue(1 < serverChecker.nonWildcardServers);


        // host=*,server=*,subsystem=*
        PathAddress subsystems = servers.append(SUBSYSTEM, WILDCARD);
        executeReadResource(subsystems.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
        executeReadResourceDescription(subsystems.toModelNode(), domainMasterLifecycleUtil.getDomainClient());

        // host=*,server=*,interface=* (the resource definition here is actually against interface=* unlike the above)
        PathAddress interfaces = servers.append(INTERFACE, WILDCARD);
        executeReadResource(interfaces.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
        WildcardRegistrationChecker interfaceChecker = new WildcardRegistrationChecker(INTERFACE);
        executeReadResourceDescription(interfaces.toModelNode(), domainMasterLifecycleUtil.getDomainClient(), interfaceChecker);
    }

    @Test
    public void testSpecificResourceOperationsUnderServer() throws IOException, MgmtOperationException {
        PathAddress servers = PathAddress.pathAddress(HOST, WILDCARD).append(RUNNING_SERVER, WILDCARD);

        PathAddress publicInterface = servers.append(INTERFACE, "public");
        executeReadResource(publicInterface.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
        executeReadResourceDescription(publicInterface.toModelNode(), domainMasterLifecycleUtil.getDomainClient());

        PathAddress jmxSubsystem = servers.append(SUBSYSTEM, "jmx");
        executeReadResource(jmxSubsystem.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
        executeReadResourceDescription(jmxSubsystem.toModelNode(), domainMasterLifecycleUtil.getDomainClient());
    }

    @Test
    public void testReadOnlyCompositeOperationWithWildCards() throws IOException, MgmtOperationException {

        testCompositeOperationWithWildCards(true);
    }

    @Test
    public void testReadWriteCompositeOperationWithWildCards() throws IOException, MgmtOperationException {

        testCompositeOperationWithWildCards(false);
    }

    void testCompositeOperationWithWildCards(boolean readonly) throws IOException, MgmtOperationException {
        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        final ModelNode steps = composite.get(STEPS);

        if (!readonly) {
            ModelNode syspropAdd = Util.createAddOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT));
            syspropAdd.get(VALUE).set("a");
            steps.add().set(syspropAdd);
        }

        final ModelNode address = new ModelNode();
        address.setEmptyList();

        // Mix in a non-widlcard remote slave op
        address.add(HOST, "slave");
        steps.add().set(createReadResourceOperation(address));

        address.setEmptyList();

        // host=*
        address.add(HOST, WILDCARD);
        steps.add().set(createReadResourceOperation(address));
        steps.add().set(createReadResourceDescriptionOperation(address));

        // host=*,server=*
        address.add(RUNNING_SERVER, WILDCARD);
        steps.add().set(createReadResourceOperation(address));
        steps.add().set(createReadResourceDescriptionOperation(address));

        // host=*,server=*,subsystem=*
        address.add(SUBSYSTEM, WILDCARD);
        steps.add().set(createReadResourceOperation(address));
        steps.add().set(createReadResourceDescriptionOperation(address));

        address.setEmptyList();

        // Mix in a non-wildcard remote server op
        address.add(HOST, "master").add(RUNNING_SERVER, "main-one");
        steps.add().set(createReadResourceOperation(address));

        // Now repeat the whole thing, but nested

        final ModelNode nested = steps.add();
        nested.get(OP).set(COMPOSITE);
        final ModelNode nestedSteps = nested.get(STEPS);

        if (!readonly) {
            ModelNode syspropUpdated = Util.getWriteAttributeOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT), VALUE, "b");
            nestedSteps.add().set(syspropUpdated);
        }

        address.setEmptyList();

        // Mix in a non-wildcard remote slave op
        address.add(HOST, "slave");
        nestedSteps.add().set(createReadResourceOperation(address));

        address.setEmptyList();

        // host=*
        address.add(HOST, WILDCARD);
        nestedSteps.add().set(createReadResourceOperation(address));
        nestedSteps.add().set(createReadResourceDescriptionOperation(address));

        // host=*,server=*
        address.add(RUNNING_SERVER, WILDCARD);
        nestedSteps.add().set(createReadResourceOperation(address));
        nestedSteps.add().set(createReadResourceDescriptionOperation(address));

        // host=*,server=*,subsystem=*
        address.add(SUBSYSTEM, WILDCARD);
        nestedSteps.add().set(createReadResourceOperation(address));
        nestedSteps.add().set(createReadResourceDescriptionOperation(address));

        address.setEmptyList();

        // Mix in a non-wildcard remote server op
        address.add(HOST, "master").add(RUNNING_SERVER, "main-one");
        nestedSteps.add().set(createReadResourceOperation(address));

        final ModelNode response = domainMasterLifecycleUtil.getDomainClient().execute(composite);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(RESULT));
        if (readonly) {
            assertFalse(response.toString(), response.has(SERVER_GROUPS));
        } else {
            assertTrue(response.toString(), response.has(SERVER_GROUPS));
        }

        validateWildCardCompositeResponse(response, readonly, true);

    }

    private void validateWildCardCompositeResponse(ModelNode response, boolean readOnly, boolean allowNested) {

        int i = 0;
        for (Property property : response.get(RESULT).asPropertyList()) {
            assertEquals(property.getName() + " from " + response, "step-" + (++i), property.getName());
            ModelNode item = property.getValue();
            assertEquals(property.getName() + " from " + response, ModelType.OBJECT, item.getType());
            assertEquals(property.getName() + " from " + response, SUCCESS, item.get(OUTCOME).asString());

            if (!readOnly && i == 1) {
                // skip remaining validation of this one
                continue;
            }

            assertTrue(property.getName() + " from " + response, item.hasDefined(RESULT));
            ModelNode itemResult = item.get(RESULT);
            PathAddress pa = null;
            int validationIndex = readOnly ? i : i - 1;
            switch (validationIndex) {
                case 1:
                    assertEquals(property.getName() + " result " + itemResult, ModelType.OBJECT, itemResult.getType());
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(RUNNING_SERVER));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(DIRECTORY_GROUPING));
                    assertEquals(property.getName() + " result " + itemResult, "slave", itemResult.get(NAME).asString());
                    break;
                case 2:
                case 3:
                    pa = PathAddress.pathAddress(PathElement.pathElement(HOST));
                case 4:
                case 5:
                    if (pa == null) {
                        pa = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(RUNNING_SERVER));
                    }
                case 6:
                case 7:
                    if (pa == null) {
                        pa = PathAddress.pathAddress(PathElement.pathElement(HOST),
                                PathElement.pathElement(RUNNING_SERVER), PathElement.pathElement(SUBSYSTEM));
                    }
                    //The issue with /host=*/server=*:read-resource-definition is that there will be an entry for each server,
                    //along with an entry for /host=xxx/server=*
                    boolean isReadResourceDescription = validationIndex % 2 != 0;
                    if (isReadResourceDescription && pa.getLastElement().getKey().equals(RUNNING_SERVER)) {
                        ServerWildcardChecker checker = new ServerWildcardChecker(pa);
                        validateWildcardResponseList(pa, itemResult, checker);
                        Assert.assertTrue(checker.seenWildcardServer);
                        Assert.assertTrue(1 < checker.nonWildcardServers);

                    } else {
                        validateWildcardResponseList(pa, itemResult, new StandardWildcardChecker());
                    }
                    break;
                case 8:
                    assertEquals(property.getName() + " result " + itemResult, ModelType.OBJECT, itemResult.getType());
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-one", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "master", itemResult.get(HOST).asString());
                    break;
                case 9:
                    if (allowNested) {
                        // recurse
                        validateWildCardCompositeResponse(item, readOnly, false);
                        break;
                    } // else fall through
                default:
                    throw new IllegalStateException();
            }
        }


    }

    @Test
    public void testReadOperationDescription() throws Exception {
        // Domain level resource
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(PATH));
        readOperationDescriptionTest(pa);

        // Host level resource
        pa = PathAddress.pathAddress(PathElement.pathElement(HOST));
        readOperationDescriptionTest(pa);

        // Host level child resource
        pa = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(SERVER_CONFIG));
        readOperationDescriptionTest(pa);

        // Server level resource
        pa = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(RUNNING_SERVER));
        readOperationDescriptionTest(pa);

        // Server level child resource
        pa = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(RUNNING_SERVER), PathElement.pathElement(SYSTEM_PROPERTY));
        readOperationDescriptionTest(pa);
    }

    private void readOperationDescriptionTest(PathAddress pa) throws IOException, MgmtOperationException {
        readOperationDescriptionTest(READ_RESOURCE_OPERATION, pa);
        readOperationDescriptionTest(READ_ATTRIBUTE_OPERATION, pa);
        readOperationDescriptionTest(READ_ATTRIBUTE_GROUP_OPERATION, pa);
        readOperationDescriptionTest(WRITE_ATTRIBUTE_OPERATION, pa);
    }

    private void readOperationDescriptionTest(String opName, PathAddress pa) throws IOException, MgmtOperationException {
        ModelNode op = Util.createEmptyOperation(READ_OPERATION_DESCRIPTION_OPERATION, pa);
        op.get(NAME).set(opName);
        DomainTestUtils.executeForResult(op, domainMasterLifecycleUtil.getDomainClient());
    }

    static ModelNode executeReadResource(final ModelNode address, final ModelControllerClient client) throws IOException, MgmtOperationException {
        final ModelNode operation = createReadResourceOperation(address);
        final ModelNode result = DomainTestUtils.executeForResult(operation, client);

        assertEquals(ModelType.LIST, result.getType());

        final PathAddress toMatch = PathAddress.pathAddress(operation.get(OP_ADDR));
        validateWildcardResponseList(toMatch, result, new StandardWildcardChecker());

        return result;
    }

    static ModelNode createReadResourceOperation(final ModelNode address) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(OP_ADDR).set(address);
        return operation;
    }

    static ModelNode executeReadResourceDescription(final ModelNode address, final ModelControllerClient client) throws IOException, MgmtOperationException {
        return executeReadResourceDescription(address, client, new StandardWildcardChecker());
    }

    static ModelNode executeReadResourceDescription(final ModelNode address, final ModelControllerClient client, final WildcardChecker wildcardChecker) throws IOException, MgmtOperationException {
        final ModelNode operation = createReadResourceDescriptionOperation(address);
        final ModelNode result = DomainTestUtils.executeForResult(operation, client);

        assertEquals(ModelType.LIST, result.getType());

        final PathAddress toMatch = PathAddress.pathAddress(operation.get(OP_ADDR));
        WildcardChecker checker = wildcardChecker == null ? new StandardWildcardChecker() : wildcardChecker;
        validateWildcardResponseList(toMatch, result, checker);

        return result;
    }

    static ModelNode createReadResourceDescriptionOperation(final ModelNode address) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("locale").set("en");
        return operation;
    }


    private static void validateWildcardResponseList(PathAddress toMatch, ModelNode responseList, WildcardChecker wildcardChecker) {
        assertEquals(responseList.toString(), ModelType.LIST, responseList.getType());
        for (final ModelNode responseItem : responseList.asList()) {
            assertMatchingAddress(toMatch, responseItem, wildcardChecker);
            assertTrue(responseItem.toString(), responseItem.hasDefined(OUTCOME));
            assertEquals(responseItem.toString(), SUCCESS, responseItem.get(OUTCOME).asString());
            assertTrue(responseItem.toString(), responseItem.hasDefined(RESULT));
        }
    }

    static void assertMatchingAddress(PathAddress toMatch, ModelNode node, WildcardChecker wildcardChecker) {
        assertTrue(node.hasDefined(OP_ADDR));
        PathAddress testee = PathAddress.pathAddress(node.get(OP_ADDR));

        assertEquals(testee + " length matches " + toMatch, toMatch.size(), testee.size());
        for (int i = 0; i < toMatch.size(); i++) {
            PathElement matchElement = toMatch.getElement(i);
            PathElement testeeElement = testee.getElement(i);
            assertEquals(testee.toString(), matchElement.getKey(), testeeElement.getKey());
            // the /host=* registration can be returned as part of RRD, so we skip it.
            if (! (node.get(RESULT).has(DESCRIPTION) &&
                node.get(RESULT).get(DESCRIPTION).asString().contains("The root node of the host-level management model"))) {
                wildcardChecker.checkPathElement(testeeElement);
            }

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

    private interface WildcardChecker {
        void checkPathElement(PathElement actualElement);
    }

    // The standard behavior is that nothing should be resolved
    private static class StandardWildcardChecker implements WildcardChecker {
        @Override
        public void checkPathElement(PathElement actualElement) {
            assertFalse(actualElement + " is multi-target", actualElement.isMultiTarget());
        }
    }

    // When doing /host=*/server=*:read-resource-definition there will be the expected resolved /host=x/server=y elements,
    // but in addition there will be /host=x/server=*.
    private static class ServerWildcardChecker extends StandardWildcardChecker {
        private final PathAddress toMatch;
        private boolean seenWildcardServer;
        private int nonWildcardServers;

        private ServerWildcardChecker(PathAddress toMatch) {
            this.toMatch = toMatch;
        }

        @Override
        public void checkPathElement(PathElement actualElement) {
            if (!actualElement.getKey().equals(RUNNING_SERVER)) {
                super.checkPathElement(actualElement);
            } else {
                if (actualElement.isWildcard()) {
                    seenWildcardServer = true;
                } else {
                    nonWildcardServers++;
                }
            }
        }
    }

    private static class WildcardRegistrationChecker extends StandardWildcardChecker {
        private final Set<String> wildcardRegistrations;

        public WildcardRegistrationChecker(String...wildcardRegistrations) {
            this.wildcardRegistrations = new HashSet<>(Arrays.asList(wildcardRegistrations));
        }

        @Override
        public void checkPathElement(PathElement actualElement) {
            if (!wildcardRegistrations.contains(actualElement.getKey())) {
                super.checkPathElement(actualElement);
            } else {
                Assert.assertTrue(actualElement.isWildcard());
            }
        }
    }
}
