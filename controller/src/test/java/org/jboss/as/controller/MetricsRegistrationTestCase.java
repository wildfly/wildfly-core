/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
 * Tests cases for attributes registration.
 *
 * Depending on the server environment (process type and running mode), resource attributes
 * may not be actually registered on the MMR.
 */
public class MetricsRegistrationTestCase {

    private static final PathElement ELEMENT = PathElement.pathElement("testing", "resource");
    private static final PathAddress ADDRESS = PathAddress.pathAddress(ELEMENT);
    private static final String TEST_METRIC = "test-metric";
    private static final String TEST_METRIC_2 = "test-metric-2";
    private static final String TEST_RUNTIME_ATTRIBUTE = "test-runtime-attribute";
    private static final String TEST_RUNTIME_ATTRIBUTE_2 = "test-runtime-attribute-2";

    private static final Executor executor = Executors.newCachedThreadPool();

    private ServiceContainer container;
    private ModelController controller;
    private ModelControllerClient client;

    private void checkMetricRegistration(ProcessType processType, boolean metricIsRegistered) throws Exception {
        setupController(processType, new TestResourceDefinition());
        ModelNode description = getResult(client.execute(Util.getReadResourceDescriptionOperation(ADDRESS)));
        assertEquals(description.toJSONString(false), metricIsRegistered, description.hasDefined(ATTRIBUTES, TEST_METRIC));
        if (metricIsRegistered) {
            checkAttribute(TEST_METRIC, 1000);
        }
        // metric that does not require runtime service is always registered
        assertTrue(description.hasDefined(ATTRIBUTES, TEST_METRIC_2));
        checkAttribute(TEST_METRIC_2, 2000);
    }

    private void checkRuntimeAttributeRegistration(ProcessType processType, boolean attributeIsRegistered) throws Exception {
        setupController(processType, new TestResourceDefinition());
        ModelNode rrd = Util.getReadResourceDescriptionOperation(ADDRESS);
        ModelNode description = getResult(client.execute(rrd));
        assertEquals(description.toJSONString(false), attributeIsRegistered, description.hasDefined(ATTRIBUTES, TEST_RUNTIME_ATTRIBUTE));
        if (attributeIsRegistered) {
            checkAttribute(TEST_RUNTIME_ATTRIBUTE, 3000);
        }
        // runtime attribute that does not required runtime service is always registered
        assertTrue(description.hasDefined(ATTRIBUTES, TEST_RUNTIME_ATTRIBUTE_2));
        checkAttribute(TEST_RUNTIME_ATTRIBUTE_2, 4000);
    }

    @Test
    public void registerMetricOnEmbeddedServerRegistersIt() throws Exception {
        checkMetricRegistration(ProcessType.EMBEDDED_SERVER, true);
    }

    @Test
    public void registerMetricOnStandaloneServerRegistersIt() throws Exception {
        checkMetricRegistration(ProcessType.STANDALONE_SERVER, true);
    }

    @Test
    public void registerMetricOnDomainServerRegistersIt() throws Exception {
        checkMetricRegistration(ProcessType.DOMAIN_SERVER, true);
    }

    @Test
    public void registerMetricOnHostControllerDoesNotRegisterIt() throws Exception {
        checkMetricRegistration(ProcessType.HOST_CONTROLLER, false);
    }

    @Test
    public void registerMetricOnEmbeddedHostControllerDoesNotRegisterIt() throws Exception {
        checkMetricRegistration(ProcessType.EMBEDDED_HOST_CONTROLLER, false);
    }

    @Test
    public void registerRuntimeAttributeOnEmbeddedServerRegistersIt() throws Exception {
        checkRuntimeAttributeRegistration(ProcessType.EMBEDDED_SERVER, true);
    }

    @Test
    public void registerRuntimeAttributeOnStandaloneServerRegistersIt() throws Exception {
        checkRuntimeAttributeRegistration(ProcessType.STANDALONE_SERVER, true);
    }

    @Test
    public void registerRuntimeAttributeOnDomainServerRegistersIt() throws Exception {
        checkRuntimeAttributeRegistration(ProcessType.DOMAIN_SERVER, true);
    }

    @Test
    public void registerRuntimeAttributeOnHostControllerDoesNotRegisterIt() throws Exception {
        checkRuntimeAttributeRegistration(ProcessType.HOST_CONTROLLER, false);
    }

    @Test
    public void registerRuntimeAttributeOnEmbeddedHostControllerDoesNotRegisterIt() throws Exception {
        checkRuntimeAttributeRegistration(ProcessType.EMBEDDED_HOST_CONTROLLER, false);
    }

    private void checkAttribute(String attributeName, int expectedValue) throws Exception {
        ModelNode result = getResult(client.execute(Util.getReadAttributeOperation(ADDRESS, attributeName)));
        assertEquals(expectedValue, result.asInt());

        ModelNode rr = Util.createEmptyOperation(READ_RESOURCE_OPERATION, ADDRESS);
        rr.get(INCLUDE_RUNTIME).set(true);
        result = getResult(client.execute(rr));
        Assert.assertTrue(result.hasDefined(attributeName));
        assertEquals(expectedValue, result.get(attributeName).asInt());
    }

    private ModelNode getResult(ModelNode result) {
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        return result.get(RESULT);
    }

    private void checkOperation(String operationName, int expectedValue) throws Exception {
        ModelNode op = Util.getEmptyOperation(operationName, ADDRESS.toModelNode());
        ModelNode result = getResult(client.execute(op));
        assertEquals(expectedValue, result.asInt());
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

    private ManagementResourceRegistration setupController(ProcessType processType, TestResourceDefinition resourceDefinition) throws InterruptedException {

        System.out.println("=========  New Test \n");
        container = ServiceContainer.Factory.create(TEST_METRIC);
        ServiceTarget target = container.subTarget();
        ModelControllerService svc = new ModelControllerService(processType, resourceDefinition);
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

        public ModelControllerService(ProcessType processType, TestResourceDefinition resourceDefinition) {
            super(processType);
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

        // metric is registered depending on the type of server for the MMR
        private static final SimpleAttributeDefinition METRIC = new SimpleAttributeDefinitionBuilder(TEST_METRIC, ModelType.INT)
                .setStorageRuntime()
                .setUndefinedMetricValue(ModelNode.ZERO)
                .build();
        // metric 2 does not require runtime service
        private static final SimpleAttributeDefinition METRIC_2 = new SimpleAttributeDefinitionBuilder(TEST_METRIC_2, ModelType.INT)
                .setRuntimeServiceNotRequired()
                .setUndefinedMetricValue(ModelNode.ZERO)
                .build();
        private static final SimpleAttributeDefinition RUNTIME_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(TEST_RUNTIME_ATTRIBUTE, ModelType.INT)
                .setStorageRuntime()
                .build();
        // runtime attribute 2 does not require runtime service
        private static final SimpleAttributeDefinition RUNTIME_ATTRIBUTE_2 = new SimpleAttributeDefinitionBuilder(TEST_RUNTIME_ATTRIBUTE_2, ModelType.INT)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();

        public TestResourceDefinition() {
            super(ELEMENT, new NonResolvingResourceDescriptionResolver(),
                    new ModelOnlyAddStepHandler(), new ModelOnlyRemoveStepHandler());
        }


        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerMetric(METRIC, (context, operation) -> context.getResult().set(1000));
            resourceRegistration.registerMetric(METRIC_2, (context, operation) -> context.getResult().set(2000));
            resourceRegistration.registerReadOnlyAttribute(RUNTIME_ATTRIBUTE, ((context, operation) -> context.getResult().set(3000)));
            resourceRegistration.registerReadOnlyAttribute(RUNTIME_ATTRIBUTE_2, ((context, operation) -> context.getResult().set(4000)));
        }
    }
}
