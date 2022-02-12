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

/**
 *
 */
package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_UNDEFINED_METRIC_VALUES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link ModelControllerImpl}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class MetricsUndefinedValueUnitTestCase {

    private static final PathElement ELEMENT = PathElement.pathElement("testing", "resource");
    private static final PathAddress ADDRESS = PathAddress.pathAddress(ELEMENT);
    private static final String TEST_METRIC = "test-metric";

    private static final Executor executor = Executors.newCachedThreadPool();

    private static CountDownLatch blockObject;
    private static CountDownLatch latch;
    private ServiceContainer container;
    private ModelController controller;
    private ModelControllerClient client;


    // BES 2015/08/28 The WFCORE-831 spec does not require this assertion. The requirement is that read-attribute
    // not return undefined, but AttributeDefinition.getUndefinedMetricValue() is not the only way to achieve this.
    // The read-attribute handler can simply always work.
//    @Test
//    public void testRegisterNonNillableNoUndefinedMetricsValueMetricRegistration() throws Exception {
//        ManagementResourceRegistration reg = setupController(new TestResourceDefinition());
//        SimpleAttributeDefinition def = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.STRING)
//                .setStorageRuntime()
//                .build();
//        registerFailingMetric(reg, def, new EmptyHandler());
//    }

    @Test
    public void testRegisterNillableAndUndefinedMetricsValueMetricRegistration() throws Exception {
        ManagementResourceRegistration reg = setupController(new TestResourceDefinition());
        SimpleAttributeDefinition def = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.STRING, true)
                .setStorageRuntime()
                .setUndefinedMetricValue(new ModelNode(TEST_METRIC))
                .build();
        registerFailingMetric(reg, def, new EmptyHandler());
    }

    @Test
    public void testRegisterNonNillableAndDefinedMetricsValueMetricRegistration() throws Exception {
        ManagementResourceRegistration reg = setupController(new TestResourceDefinition());
        SimpleAttributeDefinition def = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.STRING)
                .setStorageRuntime()
                .setUndefinedMetricValue(new ModelNode(TEST_METRIC))
                .build();
        reg.registerMetric(def, new EmptyHandler());
    }

    @Test
    public void testReadMetricDefinedByHandler() throws Exception {
        ManagementResourceRegistration reg = setupController(new TestResourceDefinition());
        SimpleAttributeDefinition def = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.STRING)
                .setStorageRuntime()
                .setUndefinedMetricValue(new ModelNode("test"))
                .build();
        reg.registerMetric(def, new HardCodedValueHandler(new ModelNode("test2")));

        checkTestMetric("test2", null);
    }

    @Test
    public void testReadMetricNotDefinedByHandler() throws Exception {
        ManagementResourceRegistration reg = setupController(new TestResourceDefinition());
        SimpleAttributeDefinition def = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.STRING)
                .setStorageRuntime()
                .setUndefinedMetricValue(new ModelNode("test"))
                .build();
        reg.registerMetric(def, new EmptyHandler());

        checkTestMetric("test", null);
    }

    @Test
    public void testReadMetricNotDefinedByHandlerWithIncludeUndefinedMetric() throws Exception {
        ManagementResourceRegistration reg = setupController(new TestResourceDefinition());
        SimpleAttributeDefinition def = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.STRING)
                .setStorageRuntime()
                .setUndefinedMetricValue(new ModelNode("test"))
                .build();
        reg.registerMetric(def, new EmptyHandler());

        checkTestMetric("test", true);
    }

    private void checkTestMetric(String expectedValue, Boolean includeUndefinedMetric) throws Exception {
        ModelNode result = getResult(client.execute(Util.getReadAttributeOperation(ADDRESS, TEST_METRIC)));
        Assert.assertEquals(expectedValue, result.asString());

        ModelNode rr = Util.createEmptyOperation(READ_RESOURCE_OPERATION, ADDRESS);
        rr.get(INCLUDE_RUNTIME).set(true);
        if (includeUndefinedMetric != null) {
            rr.get(INCLUDE_UNDEFINED_METRIC_VALUES).set(includeUndefinedMetric.booleanValue());
        }
        result = getResult(client.execute(rr));
        if (includeUndefinedMetric != null && includeUndefinedMetric.booleanValue()) {
            Assert.assertFalse(result.hasDefined(TEST_METRIC));
        } else {
            Assert.assertTrue(result.hasDefined(TEST_METRIC));
            Assert.assertEquals(expectedValue, result.get(TEST_METRIC).asString());
        }
    }

    private ModelNode getResult(ModelNode result) {
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
        return result.get(RESULT);
    }

    private void registerFailingMetric(ManagementResourceRegistration reg, AttributeDefinition def, OperationStepHandler handler) {
        boolean worked = false;
        try {
            reg.registerMetric(def, new EmptyHandler());
            worked = true;
        } catch (AssertionError expected) {
        }
        Assert.assertFalse("Should not have worked registering a non-nillable metric with no undefined metrics value", worked);
    }

    @After
    public void shutdownServiceContainer() {

        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                container = null;
            }
        }
    }

    private ManagementResourceRegistration setupController(TestResourceDefinition resourceDefinition) throws InterruptedException {

        // restore default
        blockObject = new CountDownLatch(1);
        latch = new CountDownLatch(1);

        System.out.println("=========  New Test \n");
        container = ServiceContainer.Factory.create(TEST_METRIC);
        ServiceTarget target = container.subTarget();
        ModelControllerService svc = new ModelControllerService(resourceDefinition);
        target.addService(ServiceName.of("ModelController")).setInstance(svc).install();
        svc.awaitStartup(30, TimeUnit.SECONDS);
        controller = svc.getValue();
        ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        controller.execute(setup, null, null, null);

        client = controller.createClient(executor);

        return svc.managementControllerResource;
    }

    private static class ModelControllerService extends TestModelControllerService {

        private final TestResourceDefinition resourceDefinition;
        public ManagementResourceRegistration managementControllerResource;

        public ModelControllerService(TestResourceDefinition resourceDefinition) {
            this.resourceDefinition = resourceDefinition;
        }

        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
            GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
            GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);
            managementControllerResource = rootRegistration.registerSubModel(resourceDefinition);

            Resource rootResource = managementModel.getRootResource();
            rootResource.registerChild(resourceDefinition.getPathElement(), Resource.Factory.create());
        }

    }


    private static class TestResourceDefinition extends SimpleResourceDefinition {

        private static final SimpleAttributeDefinitionBuilder builder =
                new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.INT, true);

        public TestResourceDefinition() {
            super(ELEMENT, NonResolvingResourceDescriptionResolver.INSTANCE,
                    new ModelOnlyAddStepHandler(), new ModelOnlyRemoveStepHandler());
        }

//        @Override
//        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
//            resourceRegistration.registerMetric(createAttribute(builder), new OperationStepHandler() {
//                @Override
//                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
//                    //Don't return anything
//                }
//            });
//        }
//
//        protected abstract AttributeDefinition createAttribute(SimpleAttributeDefinitionBuilder builder);
    }

    private class EmptyHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        }
    }

    private class HardCodedValueHandler implements OperationStepHandler {
        final ModelNode result;

        private HardCodedValueHandler(ModelNode result) {
            this.result = result;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.getResult().set(result);
        }
    }

}
