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

package org.jboss.as.test.integration.domain.suites;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANNOTATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESTROY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KILL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROXIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USE_CURRENT_DOMAIN_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USE_CURRENT_HOST_CONFIG;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various read operations against the domain controller.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagementReadsTestCase {

    private static final String PATH_SEPARATOR = System.getProperty("file.separator");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ManagementReadsTestCase.class.getSimpleName());

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
    public void testDomainReadResource() throws IOException {
        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode domainOp = new ModelNode();
        domainOp.get(OP).set(READ_RESOURCE_OPERATION);
        domainOp.get(OP_ADDR).setEmptyList();
        domainOp.get(RECURSIVE).set(true);
        domainOp.get(INCLUDE_RUNTIME).set(true);
        domainOp.get(PROXIES).set(false);

        ModelNode response = domainClient.execute(domainOp);
        validateResponse(response);

        // TODO make some more assertions about result content
    }

    @Test
    public void testHostReadResource() throws IOException {
        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode hostOp = new ModelNode();
        hostOp.get(OP).set(READ_RESOURCE_OPERATION);
        hostOp.get(OP_ADDR).setEmptyList().add(HOST, "primary");
        hostOp.get(RECURSIVE).set(true);
        hostOp.get(INCLUDE_RUNTIME).set(true);
        hostOp.get(PROXIES).set(false);

        ModelNode response = domainClient.execute(hostOp);
        validateResponse(response);
        // TODO make some more assertions about result content

        hostOp.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        response = domainClient.execute(hostOp);
        validateResponse(response);
        // TODO make some more assertions about result content
    }

    @Test
    public void testHostReadResourceViaSecondary() throws IOException {
        DomainClient domainClient = domainSecondaryLifecycleUtil.getDomainClient();
        final ModelNode hostOp = new ModelNode();
        hostOp.get(OP).set(READ_RESOURCE_OPERATION);
        hostOp.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        hostOp.get(RECURSIVE).set(true);
        hostOp.get(INCLUDE_RUNTIME).set(true);
        hostOp.get(PROXIES).set(false);

        ModelNode response = domainClient.execute(hostOp);
        validateResponse(response);
        // TODO make some more assertions about result content
    }

    @Test
    public void testServerReadResource() throws IOException {
        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode serverOp = new ModelNode();
        serverOp.get(OP).set(READ_RESOURCE_OPERATION);
        ModelNode address = serverOp.get(OP_ADDR);
        address.add(HOST, "primary");
        address.add(SERVER, "main-one");
        serverOp.get(RECURSIVE).set(true);
        serverOp.get(INCLUDE_RUNTIME).set(true);
        serverOp.get(PROXIES).set(false);

        ModelNode response = domainClient.execute(serverOp);
        validateResponse(response);
        // TODO make some more assertions about result content
        ModelNode result = response.get(RESULT);
        Assert.assertTrue(result.isDefined());
        Assert.assertTrue(result.hasDefined(PROFILE_NAME));

        address.setEmptyList();
        address.add(HOST, "secondary");
        address.add(SERVER, "main-three");
        response = domainClient.execute(serverOp);
        validateResponse(response);
        // TODO make some more assertions about result content
        result = response.get(RESULT);
        Assert.assertTrue(result.isDefined());
        Assert.assertTrue(result.hasDefined(PROFILE_NAME));
    }

    @Test
    public void testServerReadResourceViaSecondary() throws IOException {
        DomainClient domainClient = domainSecondaryLifecycleUtil.getDomainClient();
        final ModelNode serverOp = new ModelNode();
        serverOp.get(OP).set(READ_RESOURCE_OPERATION);
        ModelNode address = serverOp.get(OP_ADDR);
        address.add(HOST, "secondary");
        address.add(SERVER, "main-three");
        serverOp.get(RECURSIVE).set(true);
        serverOp.get(INCLUDE_RUNTIME).set(true);
        serverOp.get(PROXIES).set(false);

        ModelNode response = domainClient.execute(serverOp);
        validateResponse(response);
        // TODO make some more assertions about result content
    }

    @Test
    public void testServerPathOverride() throws IOException {
        final DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        final ModelNode address = new ModelNode();
        address.add(HOST, "primary");
        address.add(SERVER, "main-one");
        address.add(PATH, "domainTestPath");

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);

        final ModelNode response = client.execute(operation);
        validateResponse(response);

        final ModelNode result = response.get(RESULT);
        Assert.assertEquals("main-one", result.get(PATH).asString());
        Assert.assertEquals("jboss.server.temp.dir", result.get(RELATIVE_TO).asString());
    }

    @Test
    public void testHostPathOverride() throws IOException {
        final DomainClient client = domainSecondaryLifecycleUtil.getDomainClient();

        final ModelNode address = new ModelNode();
        address.add(HOST, "secondary");
        address.add(SERVER, "main-three");
        address.add(PATH, "domainTestPath");

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);

        final ModelNode response = client.execute(operation);
        validateResponse(response);

        final ModelNode result = response.get(RESULT);
        Assert.assertEquals("/tmp", result.get(PATH).asString());
        Assert.assertFalse(result.get(RELATIVE_TO).isDefined());
    }

    @Test
    public void testCompositeOperation() throws IOException {
        final DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode request = new ModelNode();
        request.get(OP).set(READ_RESOURCE_OPERATION);
        request.get(OP_ADDR).add("profile", "*");

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(STEPS).add(request);

        ModelNode response = domainClient.execute(composite);
        validateResponse(response);
        System.out.println(response);
    }

    @Test
    public void testDomainReadResourceDescription() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-resource-description");
        request.get(OP_ADDR).setEmptyList();
        request.get(RECURSIVE).set(true);
        request.get(OPERATIONS).set(true);

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        validateDomainLifecycleOps(response);
        // TODO make some more assertions about result content
    }

    @Test
    public void testDomainReadFeatureDescription() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-feature-description");
        request.get(OP_ADDR).setEmptyList();
        request.get(RECURSIVE).set(true);

        ModelNode response = domainClient.execute(request);
        ModelNode result = validateResponse(response);
        int maxDepth = validateBaseFeature(result);
        Assert.assertTrue(result.toString(), maxDepth > 3); // >3 is a good sign we're recursing all the way
        Assert.assertTrue(result.toString(), result.hasDefined(FEATURE, CHILDREN, HOST));
    }

    @Test
    public void testHostReadResourceDescription() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-resource-description");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary");
        request.get(RECURSIVE).set(true);
        request.get(OPERATIONS).set(true);

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        validateHostLifecycleOps(response, true);
        // TODO make some more assertions about result content

        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        response = domainClient.execute(request);
        validateResponse(response);
        validateHostLifecycleOps(response, false);
    }

    @Test
    public void testHostReadFeatureDescription() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-feature-description");
        request.get(OP_ADDR).add(HOST, "primary");
        request.get(RECURSIVE).set(true);

        ModelNode response = domainClient.execute(request);
        ModelNode result = validateResponse(response);
        int maxDepth = validateBaseFeature(result);
        Assert.assertTrue(result.toString(), maxDepth > 3); // >3 is a good sign we're recursing all the way
    }

    @Test
    public void testServerReadResourceDescription() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-resource-description");
        ModelNode address = request.get(OP_ADDR);
        address.add(HOST, "primary");
        address.add(SERVER, "main-one");
        request.get(RECURSIVE).set(true);
        request.get(OPERATIONS).set(true);

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        validateServerLifecycleOps(response, true);
        // TODO make some more assertions about result content

        address.setEmptyList();
        address.add(HOST, "secondary");
        address.add(SERVER, "main-three");
        response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content
    }

    // Check the stopped server has a resource description too
    @Test
    public void testStoppedServerReadResourceDescription() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-resource-description");
        ModelNode address = request.get(OP_ADDR);
        address.add(HOST, "primary");
        address.add(RUNNING_SERVER, "reload-one");
        request.get(RECURSIVE).set(true);
        request.get(OPERATIONS).set(true);

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        validateServerLifecycleOps(response, false);
    }

    @Test
    public void testDomainReadConfigAsXml() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList();

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content
    }

    @Test
    public void testHostReadConfigAsXml() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary");

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content

        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        response = domainClient.execute(request);
        validateResponse(response);
    }

    @Test
    public void testServerReadConfigAsXml() throws IOException {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        ModelNode address = request.get(OP_ADDR);
        address.add(HOST, "primary");
        address.add(SERVER, "main-one");

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content

        address.setEmptyList();
        address.add(HOST, "secondary");
        address.add(SERVER, "main-three");
        response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content
    }

    @Test
    public void testResolveExpressionOnDomain() throws Exception  {
        ModelNode op = testSupport.createOperationNode(null, "resolve-expression-on-domain");
        op.get("expression").set("${file.separator}");

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode response = domainClient.execute(op);
        validateResponse(response);
        validateResolveExpressionOnPrimary(response);
        validateResolveExpressionOnSecondary(response);
    }

    @Test
    public void testResolveExpressionOnPrimaryHost() throws Exception  {
        ModelNode op = testSupport.createOperationNode("host=primary", "resolve-expression-on-domain");
        op.get("expression").set("${file.separator}");

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode response = domainClient.execute(op);
        validateResponse(response);
        validateResolveExpressionOnPrimary(response);
    }

    @Test
    public void testResolveExpressionOnSecondaryHost() throws Exception  {
        resolveExpressionOnSecondaryHostTest(domainPrimaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testResolveExpressionOnSecondaryHostDirect() throws Exception  {
        resolveExpressionOnSecondaryHostTest(domainSecondaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testReadPrimaryHostState() throws Exception {
        readHostState("primary");
    }

    @Test
    public void testReadSecondaryHostState() throws Exception {
        readHostState("secondary");
    }

    private void readHostState(String host) throws Exception {
        ModelNode op = testSupport.createOperationNode("host=" + host, READ_RESOURCE_OPERATION);
        op.get(INCLUDE_RUNTIME).set(true);
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode response = client.execute(op);
        ModelNode result = validateResponse(response);
        Assert.assertTrue(result.hasDefined(HOST_STATE));
        Assert.assertEquals("running", result.get(HOST_STATE).asString());
    }

    private void resolveExpressionOnSecondaryHostTest(ModelControllerClient domainClient) throws Exception {
        ModelNode op = testSupport.createOperationNode("host=secondary", "resolve-expression-on-domain");
        op.get("expression").set("${file.separator}");
        System.out.println(op);
        ModelNode response = domainClient.execute(op);
        validateResponse(response);
        validateResolveExpressionOnSecondary(response);
    }

    private static void validateResolveExpressionOnPrimary(final ModelNode result) {
        System.out.println(result);
        ModelNode serverResult = result.get("server-groups", "main-server-group", "host", "primary", "main-one");
        Assert.assertTrue(serverResult.isDefined());
        validateResolveExpressionOnServer(serverResult);
    }

    private static void validateResolveExpressionOnSecondary(final ModelNode result) {
        System.out.println(result);
        ModelNode serverResult = result.get("server-groups", "main-server-group", "host", "secondary", "main-three");
        Assert.assertTrue(serverResult.isDefined());
        validateResolveExpressionOnServer(serverResult);

        serverResult = result.get("server-groups", "other-server-group", "host", "secondary", "other-two");
        Assert.assertTrue(serverResult.isDefined());
        validateResolveExpressionOnServer(serverResult);
    }

    private static void validateResolveExpressionOnServer(final ModelNode result) {
        ModelNode serverResult = validateResponse(result.get("response"));
        Assert.assertEquals(PATH_SEPARATOR, serverResult.asString());
    }


    private static int validateBaseFeature(ModelNode base) {
        Assert.assertTrue(base.toString(), base.hasDefined(FEATURE));
        Assert.assertEquals(base.toString(), 1, base.asInt());
        return validateFeature(base.get(FEATURE), null, 0);
    }

    private static int validateFeature(ModelNode feature, String expectedName, int featureDepth) {
        int highestDepth = featureDepth;
        for (Property prop : feature.asPropertyList()) {
            switch (prop.getName()) {
                case NAME:
                    if (expectedName != null) {
                        Assert.assertEquals(feature.toString(), expectedName, prop.getValue().asString());
                    }
                    break;
                case CHILDREN:
                    if (prop.getValue().isDefined()) {
                        for (Property child : prop.getValue().asPropertyList()) {
                            int treeDepth = validateFeature(child.getValue(), child.getName(), featureDepth + 1);
                            highestDepth = Math.max(highestDepth, treeDepth);
                        }
                    }
                    break;
                case ANNOTATION:
                case "params":
                case "refs":
                case "provides":
                case "requires":
                case "packages":
                    // all ok; no other validation right now
                    break;
                default:
                    Assert.fail("Unknown key " + prop.getName() + " in " + feature.toString());
            }
        }
        return highestDepth;
    }

    private static void validateServerLifecycleOps(ModelNode response, boolean isRunning) {
        ModelNode operations = response.get(RESULT, OPERATIONS);
        if (isRunning) {
            Assert.assertFalse(operations.toString(), operations.hasDefined(START));
            validateOperation(operations, RESTART, ModelType.STRING, BLOCKING, START_MODE);
            validateOperation(operations, RELOAD, ModelType.STRING, BLOCKING, START_MODE);
            validateOperation(operations, STOP, ModelType.STRING, BLOCKING, TIMEOUT, SUSPEND_TIMEOUT);
            validateOperation(operations, SUSPEND, null, TIMEOUT, SUSPEND_TIMEOUT);
            validateOperation(operations, RESUME, null);
            validateOperation(operations, DESTROY, null);
            validateOperation(operations, KILL, null);
        } else {
            validateOperation(operations, START, ModelType.STRING, BLOCKING, START_MODE);
            Assert.assertFalse(operations.toString(), operations.hasDefined(RESTART));
            Assert.assertFalse(operations.toString(), operations.hasDefined(RELOAD));
            Assert.assertFalse(operations.toString(), operations.hasDefined(STOP));
            Assert.assertFalse(operations.toString(), operations.hasDefined(SUSPEND));
            Assert.assertFalse(operations.toString(), operations.hasDefined(RESUME));
            Assert.assertFalse(operations.toString(), operations.hasDefined(DESTROY));
            Assert.assertFalse(operations.toString(), operations.hasDefined(KILL));
        }
    }

    private static void validateDomainLifecycleOps(ModelNode response) {
        ModelNode operations = response.get(RESULT, OPERATIONS);
        validateOperation(operations, RELOAD_SERVERS, null, BLOCKING, START_MODE);
        validateOperation(operations, RESTART_SERVERS, null, BLOCKING, START_MODE);
        validateOperation(operations, RESUME_SERVERS, null);
        validateOperation(operations, START_SERVERS, null, BLOCKING, START_MODE);
        validateOperation(operations, STOP_SERVERS, null, BLOCKING, TIMEOUT, SUSPEND_TIMEOUT);
        validateOperation(operations, SUSPEND_SERVERS,  null, TIMEOUT, SUSPEND_TIMEOUT);
    }

    private static void validateHostLifecycleOps(ModelNode response, boolean isPrimary) {
        ModelNode operations = response.get(RESULT, OPERATIONS);
        if (isPrimary) {
            validateOperation(operations, RELOAD, null, ADMIN_ONLY, RESTART_SERVERS, USE_CURRENT_DOMAIN_CONFIG, USE_CURRENT_HOST_CONFIG, DOMAIN_CONFIG, HOST_CONFIG);
        } else {
            validateOperation(operations, RELOAD, null, ADMIN_ONLY, RESTART_SERVERS, USE_CURRENT_HOST_CONFIG, HOST_CONFIG);
        }
        validateOperation(operations, SHUTDOWN, null, RESTART, SUSPEND_TIMEOUT);
        validateOperation(operations, RESUME_SERVERS, null);
        validateOperation(operations, SUSPEND_SERVERS,null, SUSPEND_TIMEOUT);
    }

    private static void validateOperation(ModelNode operations, String name, ModelType replyType, String... params) {
        Assert.assertTrue(operations.toString(), operations.hasDefined(name));
        ModelNode op = operations.get(name);
        ModelNode props = op.get(REQUEST_PROPERTIES);
        for (String param : params) {
            Assert.assertTrue(op.toString(), props.hasDefined(param));
        }
        ModelNode reply = op.get(REPLY_PROPERTIES);
        if (replyType != null) {
            Assert.assertTrue(op.toString(), reply.hasDefined(TYPE));
            Assert.assertEquals(op.toString(), replyType, reply.get(TYPE).asType());
        } else {
            Assert.assertFalse(op.toString(), reply.hasDefined(TYPE));
        }
    }
}
