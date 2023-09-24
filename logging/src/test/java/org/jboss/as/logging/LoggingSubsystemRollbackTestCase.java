/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.logging.handlers.AbstractHandlerDefinition;
import org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(BMUnitRunner.class)
public class LoggingSubsystemRollbackTestCase extends AbstractLoggingSubsystemTest {
    private static final String PROFILE_NAME = "test-profile";

    private KernelServices kernelServices;

    @Before
    public void bootKernelServices() throws Exception {
        kernelServices = boot();
    }

    @After
    public void shutdown() {
        if (kernelServices != null) {
            kernelServices.shutdown();
        }
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/rollback-logging.xml");
    }

    @Override
    protected void standardSubsystemTest(final String configId) throws Exception {
        // do nothing as this is not a subsystem parsing test
    }

    @After
    @Override
    public void clearLogContext() throws Exception {
        super.clearLogContext();
        final LoggingProfileContextSelector contextSelector = LoggingProfileContextSelector.getInstance();
        if (contextSelector.exists(PROFILE_NAME)) {
            contextSelector.get(PROFILE_NAME).close();
            contextSelector.remove(PROFILE_NAME);
        }
    }

    @Test
    @BMRule(name = "Test logger rollback handler",
            targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerAddOperationStepHandler",
            targetMethod = "performRuntime",
            targetLocation = "AT EXIT",
            condition = "$1.getCurrentAddressValue().equals(\"org.jboss.as.logging.test\")",
            action = "$1.setRollbackOnly()"
    )
    public void testRollbackLogger() throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        // The logger address
        final PathAddress address = createLoggerAddress("org.jboss.as.logging.test");

        // Operation should fail based on byteman script
        ModelNode op = SubsystemOperations.createAddOperation(address.toModelNode());
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertFalse("The add operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // Verify the loggers are not there - operation should fail on missing resource
        op = SubsystemOperations.createReadResourceOperation(address.toModelNode());
        result = kernelServices.executeOperation(op);
        Assert.assertFalse("The operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        final ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(validSubsystemModel, currentModel);

        final ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        compare(currentModel, config);
    }

    @Test
    @BMRule(name = "Test handler rollback handler",
            targetClass = "org.jboss.as.logging.handlers.HandlerOperations$HandlerAddOperationStepHandler",
            targetMethod = "performRuntime",
            targetLocation = "AT EXIT",
            condition = "$1.getCurrentAddressValue().equals(\"CONSOLE2\")",
            action = "$1.setRollbackOnly()")
    public void testRollbackHandler() throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        // Handler address
        final PathAddress address = createConsoleHandlerAddress("CONSOLE2");
        // Operation should fail based on byteman script
        ModelNode op = SubsystemOperations.createAddOperation(address.toModelNode());
        op.get(CommonAttributes.LEVEL.getName()).set("INFO");
        op.get(AbstractHandlerDefinition.FORMATTER.getName()).set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) CONSOLE2: %s%e%n");
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertFalse("The add operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // Verify the loggers are not there - operation should fail on missing resource
        op = SubsystemOperations.createReadResourceOperation(address.toModelNode());
        result = kernelServices.executeOperation(op);
        Assert.assertFalse("The operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        final ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(validSubsystemModel, currentModel);

        final ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        compare(currentModel, config);
    }

    @Test
    @BMRule(name = "Test handler rollback handler",
            targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerWriteAttributeHandler",
            targetMethod = "applyUpdate",
            targetLocation = "AT EXIT",
            condition = "$1.getCurrentAddressValue().equals(\"org.jboss.as.logging\")",
            action = "$1.setRollbackOnly()")
    public void testRollbackComposite() throws Exception {
        // Add a handler to be removed
        final PathAddress consoleHandler = createConsoleHandlerAddress("CONSOLE2");
        // Create a new handler
        ModelNode op = SubsystemOperations.createAddOperation(consoleHandler.toModelNode());
        op.get(CommonAttributes.LEVEL.getName()).set("INFO");
        op.get(AbstractHandlerDefinition.FORMATTER.getName()).set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) CONSOLE2: %s%e%n");
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertTrue(SubsystemOperations.getFailureDescriptionAsString(result), SubsystemOperations.isSuccessfulOutcome(result));

        // Add the handler to a logger
        final PathAddress loggerAddress = createLoggerAddress("org.jboss.as.logging");
        op = SubsystemOperations.createOperation(CommonAttributes.ADD_HANDLER_OPERATION_NAME, loggerAddress.toModelNode());
        op.get(CommonAttributes.HANDLER_NAME.getName()).set(consoleHandler.getLastElement().getValue());
        result = kernelServices.executeOperation(op);
        Assert.assertTrue(SubsystemOperations.getFailureDescriptionAsString(result), SubsystemOperations.isSuccessfulOutcome(result));

        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);


        final CompositeOperationBuilder operationBuilder = CompositeOperationBuilder.create();

        // create a new handler
        final PathAddress fileHandlerAddress = createFileHandlerAddress("fail-fh");
        final ModelNode fileHandlerOp = SubsystemOperations.createAddOperation(fileHandlerAddress.toModelNode());
        fileHandlerOp.get(CommonAttributes.FILE.getName(), PathResourceDefinition.RELATIVE_TO.getName()).set("jboss.server.log.dir");
        fileHandlerOp.get(CommonAttributes.FILE.getName(), PathResourceDefinition.PATH.getName()).set("fail-fh.log");
        fileHandlerOp.get(CommonAttributes.AUTOFLUSH.getName()).set(true);
        operationBuilder.addStep(fileHandlerOp);

        // create a new logger
        final PathAddress testLoggerAddress = createLoggerAddress("test");
        final ModelNode testLoggerOp = SubsystemOperations.createAddOperation(testLoggerAddress.toModelNode());
        operationBuilder.addStep(testLoggerOp);

        // add handler to logger
        operationBuilder.addStep(SubsystemOperations.createWriteAttributeOperation(testLoggerAddress.toModelNode(), LoggerAttributes.HANDLERS, new ModelNode().setEmptyList().add("fail-fh")));

        // remove the console handler
        operationBuilder.addStep(SubsystemOperations.createRemoveOperation(consoleHandler.toModelNode()));

        // add handler to existing logger - should force fail on this one
        operationBuilder.addStep(SubsystemOperations.createWriteAttributeOperation(loggerAddress.toModelNode(), LoggerAttributes.HANDLERS, new ModelNode().setEmptyList().add("fail-fh")));

        // verify the operation failed
        result = kernelServices.executeOperation(operationBuilder.build().getOperation());
        Assert.assertFalse("The add operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        final ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(validSubsystemModel, currentModel);

        final ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        compare(currentModel, config);
    }

    @Test
    @BMRule(name = "Test handler rollback handler",
            targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerWriteAttributeHandler",
            targetMethod = "applyUpdate",
            targetLocation = "AT EXIT",
            condition = "$1.getCurrentAddressValue().equals(\"org.jboss.as.logging\")",
            action = "$1.setRollbackOnly()")
    public void testRollbackCompositeLoggingProfile() throws Exception {
        // Add a handler to be removed
        final PathAddress consoleHandler = createConsoleHandlerAddress("CONSOLE2");
        // Create a new handler
        ModelNode op = SubsystemOperations.createAddOperation(consoleHandler.toModelNode());
        op.get(CommonAttributes.LEVEL.getName()).set("INFO");
        op.get(AbstractHandlerDefinition.FORMATTER.getName()).set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) CONSOLE2: %s%e%n");
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertTrue(SubsystemOperations.getFailureDescriptionAsString(result), SubsystemOperations.isSuccessfulOutcome(result));

        // Add the handler to a logger
        final PathAddress loggerAddress = createLoggerAddress("org.jboss.as.logging");
        op = SubsystemOperations.createOperation(CommonAttributes.ADD_HANDLER_OPERATION_NAME, loggerAddress.toModelNode());
        op.get(CommonAttributes.HANDLER_NAME.getName()).set(consoleHandler.getLastElement().getValue());
        result = kernelServices.executeOperation(op);
        Assert.assertTrue(SubsystemOperations.getFailureDescriptionAsString(result), SubsystemOperations.isSuccessfulOutcome(result));

        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        final CompositeOperationBuilder operationBuilder = CompositeOperationBuilder.create();

        // create a new handler
        final PathAddress fileHandlerAddress = createFileHandlerAddress("fail-fh");
        final ModelNode fileHandlerOp = SubsystemOperations.createAddOperation(fileHandlerAddress.toModelNode());
        fileHandlerOp.get(CommonAttributes.FILE.getName(), PathResourceDefinition.RELATIVE_TO.getName()).set("jboss.server.log.dir");
        fileHandlerOp.get(CommonAttributes.FILE.getName(), PathResourceDefinition.PATH.getName()).set("fail-fh.log");
        fileHandlerOp.get(CommonAttributes.AUTOFLUSH.getName()).set(true);
        operationBuilder.addStep(fileHandlerOp);

        // create a new logger
        final PathAddress testLoggerAddress = createLoggerAddress(PROFILE_NAME, "test");
        final ModelNode testLoggerOp = SubsystemOperations.createAddOperation(testLoggerAddress.toModelNode());
        operationBuilder.addStep(testLoggerOp);

        // remove the console handler
        operationBuilder.addStep(SubsystemOperations.createRemoveOperation(consoleHandler.toModelNode()));

        // add handler to existing logger - should force fail on this one
        operationBuilder.addStep(SubsystemOperations.createWriteAttributeOperation(loggerAddress.toModelNode(), LoggerAttributes.HANDLERS, new ModelNode().setEmptyList().add("fail-fh")));

        // verify the operation failed
        result = kernelServices.executeOperation(operationBuilder.build().getOperation());
        Assert.assertFalse("The add operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        final ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(validSubsystemModel, currentModel);

        final ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        compare(currentModel, config);
    }

    @Test
    @BMRules(rules = {
            @BMRule(name = "Test handler rollback handler",
                    targetClass = "org.jboss.as.logging.handlers.HandlerOperations$HandlerAddOperationStepHandler",
                    targetMethod = "performRuntime",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"CONSOLE2\")",
                    action = "$1.setRollbackOnly()"),
            @BMRule(name = "Test logger rollback handler",
                    targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerAddOperationStepHandler",
                    targetMethod = "performRuntime",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"org.jboss.as.logging.test\")",
                    action = "$1.setRollbackOnly()")
    })
    public void testRollbackAdd() throws Exception {
        rollbackAdd(null);
        rollbackAdd(PROFILE_NAME);
    }

    @Test
    @BMRules(rules = {
            @BMRule(name = "Test handler write-attribute rollback handler",
                    targetClass = "org.jboss.as.logging.handlers.HandlerOperations$LogHandlerWriteAttributeHandler",
                    targetMethod = "applyUpdate",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"CONSOLE\")",
                    action = "$1.setRollbackOnly()"),
            @BMRule(name = "Test logger write-attribute rollback handler",
                    targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerWriteAttributeHandler",
                    targetMethod = "applyUpdate",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"ROOT\")",
                    action = "$1.setRollbackOnly()")
    })
    public void testRollbackWriteAttribute() throws Exception {
        rollbackWriteAttribute(null);
        rollbackWriteAttribute(PROFILE_NAME);
    }

    @Test
    @BMRules(rules = {
            @BMRule(name = "Test handler rollback handler",
                    targetClass = "org.jboss.as.logging.handlers.HandlerOperations$HandlerUpdateOperationStepHandler",
                    targetMethod = "performRuntime",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"CONSOLE\")",
                    action = "$1.setRollbackOnly()"),
            @BMRule(name = "Test logger rollback handler",
                    targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerUpdateOperationStepHandler",
                    targetMethod = "performRuntime",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"ROOT\")",
                    action = "$1.setRollbackOnly()")
    })
    public void testRollbackUpdateAttribute() throws Exception {
        rollbackUpdateAttribute(null);
        rollbackUpdateAttribute(PROFILE_NAME);
    }

    @Test
    @BMRules(rules = {
            @BMRule(name = "Test handler write-attribute rollback handler",
                    targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerWriteAttributeHandler",
                    targetMethod = "applyUpdate",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"ROOT\")",
                    action = "$1.setRollbackOnly()")
    })
    public void testRollbackRemove() throws Exception {
        rollbackRemove(null);
        rollbackRemove(PROFILE_NAME);
    }

    @Test
    @BMRules(rules = {
            @BMRule(name = "Test handler write-attribute rollback handler",
                    targetClass = "org.jboss.as.logging.loggers.LoggerOperations$LoggerWriteAttributeHandler",
                    targetMethod = "applyUpdate",
                    targetLocation = "AT EXIT",
                    condition = "$1.getCurrentAddressValue().equals(\"ROOT\")",
                    action = "$1.setRollbackOnly()")
    })
    public void testRollbackRemoveProfile() throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        final CompositeOperationBuilder compositeOperationBuilder = CompositeOperationBuilder.create();

        // The handler address to remove
        final PathAddress profileAddress = createAddress(CommonAttributes.LOGGING_PROFILE, PROFILE_NAME);
        // Remove the handler
        compositeOperationBuilder.addStep(SubsystemOperations.createRemoveOperation(profileAddress.toModelNode(), true));

        // Add a step to fail
        final ModelNode rootLoggerAddress = createRootLoggerAddress().toModelNode();
        compositeOperationBuilder.addStep(SubsystemOperations.createWriteAttributeOperation(rootLoggerAddress, CommonAttributes.LEVEL, "INFO"));

        ModelNode result = kernelServices.executeOperation(compositeOperationBuilder.build().getOperation());
        Assert.assertFalse("The update operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        final ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(validSubsystemModel, currentModel);

        ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        compare(currentModel, config);
        // Check the profile was rolled back
        config = ConfigurationPersistence.getConfigurationPersistence(LoggingProfileContextSelector.getInstance().get(PROFILE_NAME));
        compare(PROFILE_NAME, currentModel, config);
    }

    public void rollbackAdd(final String profileName) throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        // Add a handler to be removed
        final PathAddress consoleHandler = createConsoleHandlerAddress(profileName, "CONSOLE2");
        // Create a new handler
        ModelNode op = SubsystemOperations.createAddOperation(consoleHandler.toModelNode());
        op.get(CommonAttributes.LEVEL.getName()).set("INFO");
        op.get(AbstractHandlerDefinition.FORMATTER.getName()).set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) CONSOLE2: %s%e%n");
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertFalse("The add operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        final LogContext logContext = (profileName == null ? LogContext.getLogContext() : LoggingProfileContextSelector.getInstance().get(profileName));
        ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);

        // Fail on a logger write attribute
        final PathAddress loggerAddress = createLoggerAddress(profileName, "org.jboss.as.logging.test");
        op = SubsystemOperations.createAddOperation(loggerAddress.toModelNode());
        result = kernelServices.executeOperation(op);
        Assert.assertFalse("The add operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);
    }

    public void rollbackWriteAttribute(final String profileName) throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        // Add a handler to be removed
        final PathAddress consoleHandler = createConsoleHandlerAddress(profileName, "CONSOLE");
        // Create a new handler
        ModelNode op = SubsystemOperations.createWriteAttributeOperation(consoleHandler.toModelNode(), ConsoleHandlerResourceDefinition.TARGET, "System.err");
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertFalse("The write operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        final LogContext logContext = (profileName == null ? LogContext.getLogContext() : LoggingProfileContextSelector.getInstance().get(profileName));
        ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);

        // Fail on a logger write attribute
        final PathAddress rootLoggerAddress = createRootLoggerAddress(profileName);
        op = SubsystemOperations.createWriteAttributeOperation(rootLoggerAddress.toModelNode(), CommonAttributes.LEVEL, "TRACE");
        result = kernelServices.executeOperation(op);
        Assert.assertFalse("The write operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);

    }

    public void rollbackUpdateAttribute(final String profileName) throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        // Add a handler to be removed
        final PathAddress consoleHandler = createConsoleHandlerAddress(profileName, "CONSOLE");
        // Create a new handler
        ModelNode op = SubsystemOperations.createOperation(AbstractHandlerDefinition.CHANGE_LEVEL_OPERATION_NAME, consoleHandler.toModelNode());
        op.get(CommonAttributes.LEVEL.getName()).set("DEBUG");
        ModelNode result = kernelServices.executeOperation(op);
        Assert.assertFalse("The update operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        final LogContext logContext = (profileName == null ? LogContext.getLogContext() : LoggingProfileContextSelector.getInstance().get(profileName));
        ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);

        // Fail on a logger write attribute
        final PathAddress rootLoggerAddress = createRootLoggerAddress(profileName);
        op = SubsystemOperations.createOperation("change-root-log-level", rootLoggerAddress.toModelNode());
        op.get(CommonAttributes.LEVEL.getName()).set("TRACE");
        result = kernelServices.executeOperation(op);
        Assert.assertFalse("The update operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);

    }

    public void rollbackRemove(final String profileName) throws Exception {
        // Save the current model
        final ModelNode validSubsystemModel = getSubsystemModel(kernelServices);

        final CompositeOperationBuilder compositeOperationBuilder = CompositeOperationBuilder.create();

        // The handler address to remove
        final PathAddress consoleHandler = createConsoleHandlerAddress(profileName, "CONSOLE");
        // Remove the handler
        compositeOperationBuilder.addStep(SubsystemOperations.createRemoveOperation(consoleHandler.toModelNode()));

        // The logger to remove
        final PathAddress loggerAddress = createLoggerAddress(profileName, "org.jboss.as.logging");
        compositeOperationBuilder.addStep(SubsystemOperations.createRemoveOperation(loggerAddress.toModelNode()));

        // Add a step to fail
        final ModelNode rootLoggerAddress = createRootLoggerAddress(profileName).toModelNode();
        compositeOperationBuilder.addStep(SubsystemOperations.createWriteAttributeOperation(rootLoggerAddress, CommonAttributes.LEVEL, "INFO"));

        ModelNode result = kernelServices.executeOperation(compositeOperationBuilder.build().getOperation());
        Assert.assertFalse("The update operation should have failed, but was successful: " + result, SubsystemOperations.isSuccessfulOutcome(result));

        // verify the subsystem model matches the old model
        ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(profileName, validSubsystemModel, currentModel);

        final LogContext logContext = (profileName == null ? LogContext.getLogContext() : LoggingProfileContextSelector.getInstance().get(profileName));
        ConfigurationPersistence config = ConfigurationPersistence.getConfigurationPersistence(logContext);
        compare(profileName, currentModel, config);

    }
}
