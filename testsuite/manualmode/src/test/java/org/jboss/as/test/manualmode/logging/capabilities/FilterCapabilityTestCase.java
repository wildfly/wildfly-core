/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.capabilities;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.module.util.ModuleBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class FilterCapabilityTestCase extends CapabilityTestCase {

    private static final String MODULE_NAME = "org.jboss.as.logging.test";

    private static Runnable MODULE_CLEANUP_TASK = null;

    @BeforeClass
    public static void createModule() {
        MODULE_CLEANUP_TASK = ModuleBuilder.of(MODULE_NAME, "logging-test.jar")
                .addClass(TestFilter.class)
                .addDependencies("java.logging", "org.jboss.logmanager")
                .build();
    }

    @AfterClass
    public static void removeModule() {
        MODULE_CLEANUP_TASK.run();
    }

    @Test
    public void testRemoveAttachedLoggerFilter() throws Exception {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final ModelNode filterAddress = createSubsystemAddress("filter", "testFilter");
        ModelNode op = Operations.createAddOperation(filterAddress);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        // Add the filter to the root logger
        final ModelNode rootLoggerAddress = createSubsystemAddress("root-logger", "ROOT");
        builder.addStep(Operations.createWriteAttributeOperation(rootLoggerAddress, "filter-spec", "testFilter"));
        executeOperation(builder.build());

        // Now attempt to remove the filter which should fail
        executeOperationForFailure(Operations.createRemoveOperation(filterAddress), CANNOT_REMOVE);

        // Now properly tear down the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(rootLoggerAddress, "filter-spec"));
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        executeOperation(builder.build());
    }

    @Test
    public void testMultiAttachedLoggerFilter() throws Exception {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final ModelNode filter1Address = createSubsystemAddress("filter", "testFilter1");
        ModelNode op = Operations.createAddOperation(filter1Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        final ModelNode filter2Address = createSubsystemAddress("filter", "testFilter2");
        op = Operations.createAddOperation(filter2Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        // Add the filter to the root logger
        final ModelNode rootLoggerAddress = createSubsystemAddress("root-logger", "ROOT");
        builder.addStep(Operations.createWriteAttributeOperation(rootLoggerAddress, "filter-spec", "any(testFilter1,testFilter2)"));
        executeOperation(builder.build());

        // Now attempt to remove the filter which should fail
        executeOperationForFailure(Operations.createRemoveOperation(filter2Address),
                PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter2.*"));

        // Use only the second filter which should allow the first filter to be removed, but not the second filter
        executeOperation(Operations.createWriteAttributeOperation(rootLoggerAddress, "filter-spec", "testFilter1"));
        executeOperation(Operations.createRemoveOperation(filter2Address));
        executeOperationForFailure(Operations.createRemoveOperation(filter1Address),
                PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter1.*"));


        // Now properly tear down the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(rootLoggerAddress, "filter-spec"));
        builder.addStep(Operations.createRemoveOperation(filter1Address));
        executeOperation(builder.build());
    }

    @Test
    public void testMultiAttachedLoggerFilterComposite() throws Exception {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final ModelNode filter1Address = createSubsystemAddress("filter", "testFilter1");
        ModelNode op = Operations.createAddOperation(filter1Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        final ModelNode filter2Address = createSubsystemAddress("filter", "testFilter2");
        op = Operations.createAddOperation(filter2Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        // Add the filter to the root logger
        final ModelNode rootLoggerAddress = createSubsystemAddress("root-logger", "ROOT");
        builder.addStep(Operations.createWriteAttributeOperation(rootLoggerAddress, "filter-spec", "any(testFilter1,testFilter2)"));
        executeOperation(builder.build());

        // Now attempt to remove the filter which should fail
        executeOperationForFailure(Operations.createRemoveOperation(filter2Address),
                PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter2.*"));

        // Use only the second filter which should allow the first filter to be removed, but not the second filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createWriteAttributeOperation(rootLoggerAddress, "filter-spec", "testFilter1"));
        builder.addStep(Operations.createRemoveOperation(filter2Address));
        builder.addStep(Operations.createRemoveOperation(filter1Address));
        executeOperationForFailure(builder.build(), PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter1.*", ".*testFilter2.*"));


        // Now properly tear down the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(rootLoggerAddress, "filter-spec"));
        builder.addStep(Operations.createRemoveOperation(filter1Address));
        builder.addStep(Operations.createRemoveOperation(filter2Address));
        executeOperation(builder.build());
    }

    @Test
    public void testRemoveAttachedHandlerFilter() throws Exception {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final ModelNode filterAddress = createSubsystemAddress("filter", "testFilter");
        ModelNode op = Operations.createAddOperation(filterAddress);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        // Add the filter to the console handler
        final ModelNode consoleHandler = createSubsystemAddress("console-handler", HANDLER_NAME);
        builder.addStep(Operations.createWriteAttributeOperation(consoleHandler, "filter-spec", "testFilter"));
        executeOperation(builder.build());

        // Now attempt to remove the filter which should fail
        executeOperationForFailure(Operations.createRemoveOperation(filterAddress), CANNOT_REMOVE);

        // Now properly tear down the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(consoleHandler, "filter-spec"));
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        executeOperation(builder.build());
    }

    @Test
    public void testMultiAttachedHandlerFilter() throws Exception {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final ModelNode filter1Address = createSubsystemAddress("filter", "testFilter1");
        ModelNode op = Operations.createAddOperation(filter1Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        final ModelNode filter2Address = createSubsystemAddress("filter", "testFilter2");
        op = Operations.createAddOperation(filter2Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        // Add the filter to the console handler
        final ModelNode handlerAddress = createSubsystemAddress("console-handler", HANDLER_NAME);
        builder.addStep(Operations.createWriteAttributeOperation(handlerAddress, "filter-spec", "any(testFilter1,testFilter2)"));
        executeOperation(builder.build());

        // Now attempt to remove the filter which should fail
        executeOperationForFailure(Operations.createRemoveOperation(filter2Address),
                PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter2.*"));

        // Use only the second filter which should allow the first filter to be removed, but not the second filter
        executeOperation(Operations.createWriteAttributeOperation(handlerAddress, "filter-spec", "testFilter2"));
        executeOperation(Operations.createRemoveOperation(filter1Address));
        executeOperationForFailure(Operations.createRemoveOperation(filter2Address),
                PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter2.*"));


        // Now properly tear down the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(handlerAddress, "filter-spec"));
        builder.addStep(Operations.createRemoveOperation(filter2Address));
        executeOperation(builder.build());
    }

    @Test
    public void testMultiAttachedHandlerFilterComposite() throws Exception {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final ModelNode filter1Address = createSubsystemAddress("filter", "testFilter1");
        ModelNode op = Operations.createAddOperation(filter1Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        final ModelNode filter2Address = createSubsystemAddress("filter", "testFilter2");
        op = Operations.createAddOperation(filter2Address);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set(MODULE_NAME);
        builder.addStep(op);

        // Add the filter to the console handler
        final ModelNode handlerAddress = createSubsystemAddress("console-handler", HANDLER_NAME);
        builder.addStep(Operations.createWriteAttributeOperation(handlerAddress, "filter-spec", "any(testFilter1,testFilter2)"));
        executeOperation(builder.build());

        // Now attempt to remove the filter which should fail
        executeOperationForFailure(Operations.createRemoveOperation(filter2Address),
                PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter2.*"));

        // Use only the second filter which should allow the first filter to be removed, but not the second filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createWriteAttributeOperation(handlerAddress, "filter-spec", "testFilter1"));
        builder.addStep(Operations.createRemoveOperation(filter2Address));
        builder.addStep(Operations.createRemoveOperation(filter1Address));
        executeOperationForFailure(builder.build(), PatternPredicate.of(".*" + CANNOT_REMOVE + ".*testFilter1.*", ".*testFilter2.*"));


        // Now properly tear down the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(handlerAddress, "filter-spec"));
        builder.addStep(Operations.createRemoveOperation(filter1Address));
        builder.addStep(Operations.createRemoveOperation(filter2Address));
        executeOperation(builder.build());
    }

}
