/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.FailedOperationTransformationConfig.NewAttributesConfig;
import org.jboss.as.model.test.FailedOperationTransformationConfig.RejectExpressionsConfig;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logmanager.LogContext;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingSubsystemTestCase extends AbstractLoggingSubsystemTest {


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/logging.xml");
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/jboss-as-logging_3_0.xsd";
    }

    @Test
    public void testExpressions() throws Exception {
        standardSubsystemTest("/expressions.xml");
    }

    @Test
    public void testConfiguration() throws Exception {
        final KernelServices kernelServices = boot();
        final ModelNode currentModel = getSubsystemModel(kernelServices);
        compare(currentModel, ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext()));

        // Compare properties written out to current model
        final String dir = resolveRelativePath(kernelServices, "jboss.server.config.dir");
        Assert.assertNotNull("jboss.server.config.dir could not be resolved", dir);
        final LogContext logContext = LogContext.create();
        final ConfigurationPersistence config = ConfigurationPersistence.getOrCreateConfigurationPersistence(logContext);
        final FileInputStream in = new FileInputStream(new File(dir, "logging.properties"));
        config.configure(in);
        compare(currentModel, config);
    }

    @Test
    public void testLegacyConfigurations() throws Exception {
        // Get a list of all the logging_x_x.xml files
        final Pattern pattern = Pattern.compile("(logging|expressions)_\\d+_\\d+\\.xml");
        // Using the CP as that's the standardSubsystemTest will use to find the config file
        final String cp = WildFlySecurityManager.getPropertyPrivileged("java.class.path", ".");
        final String[] entries = cp.split(Pattern.quote(File.pathSeparator));
        final List<String> configs = new ArrayList<>();
        for (String entry : entries) {
            final Path path = Paths.get(entry);
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final String name = file.getFileName().toString();
                        if (pattern.matcher(name).matches()) {
                            configs.add("/" + name);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }

        // The paths shouldn't be empty
        Assert.assertFalse("No configs were found", configs.isEmpty());

        for (String configId : configs) {
            // Run the standard subsystem test, but don't compare the XML as it should never match
            standardSubsystemTest(configId, false);
        }
    }

    @Test
    public void testTransformersEAP620() throws Exception {
        testEapTransformer(ModelTestControllerVersion.EAP_6_2_0, ModelVersion.create(1, 3, 0), readResource("/logging_1_3.xml"),
                AsyncModelFixer.INSTANCE,
                // Using a ModelFixer to remove an attribute from the legacy model that the transformer removes seems odd here.
                // However, the category attribute is a read-only attribute resolved at runtime by the name of the resource.
                // WildFly does not require the ModelFixer as read-only attributes can be left off. If a change is made in EAP
                // to do the same thing, this ModelFixer can and should be removed.
                new AttributeRemovalModelFixer(LoggerResourceDefinition.CATEGORY));
    }

    @Test
    public void testFailedTransformersEAP620() throws Exception {
        final ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.EAP_6_2_0;
        final ModelVersion modelVersion = ModelVersion.create(1, 3, 0);
        final PathAddress loggingProfileAddress = SUBSYSTEM_ADDRESS.append(CommonAttributes.LOGGING_PROFILE);

        // Test against current
        testEapFailedTransformers(controllerVersion, modelVersion, readResource("/expressions.xml"),
                new FailedOperationTransformationConfig()
                        .addFailedAttribute(SUBSYSTEM_ADDRESS, new NewAttributesConfig(LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES, LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(FileHandlerResourceDefinition.FILE_HANDLER_PATH),
                                new NewAttributesConfig(FileHandlerResourceDefinition.NAMED_FORMATTER))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PatternFormatterResourceDefinition.PATTERN_FORMATTER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_HANDLER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new FailedOperationTransformationConfig.NewAttributesConfig(SizeRotatingHandlerResourceDefinition.SUFFIX))
                        .addFailedAttribute(loggingProfileAddress.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(loggingProfileAddress.append(FileHandlerResourceDefinition.FILE_HANDLER_PATH),
                                new NewAttributesConfig(FileHandlerResourceDefinition.NAMED_FORMATTER))
                        .addFailedAttribute(loggingProfileAddress.append(PatternFormatterResourceDefinition.PATTERN_FORMATTER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(loggingProfileAddress.append(PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_HANDLER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(loggingProfileAddress.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new FailedOperationTransformationConfig.NewAttributesConfig(SizeRotatingHandlerResourceDefinition.SUFFIX)));
    }

    @Test
    public void testTransformersEAP630() throws Exception {
        testEapTransformer(ModelTestControllerVersion.EAP_6_3_0, ModelVersion.create(1, 4, 0), readResource("/logging_1_4.xml"),
                AsyncModelFixer.INSTANCE,
                // In WildFly Core the default formatter changed from %E (extended exceptions) to %e. When the legacy
                // model is compared to the new model and the formatter is not defined or a named-formatter is used the
                // check fails due to the formatter difference. This is not ideal, but will work as the difference isn't
                // important.
                new AttributeValueChangerModelFixer(AbstractHandlerDefinition.FORMATTER, "%E", "%e")
        );
    }

    @Test
    public void testFailedTransformersEAP630() throws Exception {
        final ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.EAP_6_3_0;
        final ModelVersion modelVersion = ModelVersion.create(1, 4, 0);
        final PathAddress loggingProfileAddress = SUBSYSTEM_ADDRESS.append(CommonAttributes.LOGGING_PROFILE);

        // Test against current
        testEapFailedTransformers(controllerVersion, modelVersion, readResource("/expressions.xml"),
                new FailedOperationTransformationConfig()
                        .addFailedAttribute(SUBSYSTEM_ADDRESS, new NewAttributesConfig(LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_HANDLER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new FailedOperationTransformationConfig.NewAttributesConfig(SizeRotatingHandlerResourceDefinition.SUFFIX))
                        .addFailedAttribute(loggingProfileAddress.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(loggingProfileAddress.append(PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_HANDLER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(loggingProfileAddress.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new FailedOperationTransformationConfig.NewAttributesConfig(SizeRotatingHandlerResourceDefinition.SUFFIX)));
    }

    @Test
    public void testTransformersEAP640() throws Exception {
        testEapTransformer(ModelTestControllerVersion.EAP_6_4_0, ModelVersion.create(1, 5, 0), readResource("/logging_1_5.xml"),
                AsyncModelFixer.INSTANCE,
                // In WildFly Core the default formatter changed from %E (extended exceptions) to %e. When the legacy
                // model is compared to the new model and the formatter is not defined or a named-formatter is used the
                // check fails due to the formatter difference. This is not ideal, but will work as the difference isn't
                // important.
                new AttributeValueChangerModelFixer(AbstractHandlerDefinition.FORMATTER, "%E", "%e")
        );
    }

    @Test
    public void testFailedTransformersEAP640() throws Exception {
        final ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.EAP_6_4_0;
        final ModelVersion modelVersion = ModelVersion.create(1, 5, 0);
        final PathAddress loggingProfileAddress = SUBSYSTEM_ADDRESS.append(CommonAttributes.LOGGING_PROFILE);

        // Test against current
        testEapFailedTransformers(controllerVersion, modelVersion, readResource("/expressions.xml"),
                new FailedOperationTransformationConfig()
                        .addFailedAttribute(SUBSYSTEM_ADDRESS, new NewAttributesConfig(LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(loggingProfileAddress.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET)));
    }

    @Test
    public void testTransformersWildFly800() throws Exception {
        testWildFlyTransformer(ModelTestControllerVersion.WILDFLY_8_0_0_FINAL, ModelVersion.create(2, 0, 0), readResource("/logging_2_0.xml"),
                // In WildFly Core the default formatter changed from %E (extended exceptions) to %e. When the legacy
                // model is compared to the new model and the formatter is not defined or a named-formatter is used the
                // check fails due to the formatter difference. This is not ideal, but will work as the difference isn't
                // important.
                new AttributeValueChangerModelFixer(AbstractHandlerDefinition.FORMATTER, "%E", "%e")
        );
    }

    @Test
    public void testFailedTransformersWildFly800() throws Exception {
        final ModelTestControllerVersion controllerVersion = ModelTestControllerVersion.WILDFLY_8_0_0_FINAL;
        final ModelVersion modelVersion = ModelVersion.create(2, 0, 0);
        final PathAddress loggingProfileAddress = SUBSYSTEM_ADDRESS.append(CommonAttributes.LOGGING_PROFILE);

        // Test against current
        testWildFlyFailedTransformers(controllerVersion, modelVersion, readResource("/expressions.xml"),
                new FailedOperationTransformationConfig()
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_HANDLER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new FailedOperationTransformationConfig.NewAttributesConfig(SizeRotatingHandlerResourceDefinition.SUFFIX))
                        .addFailedAttribute(loggingProfileAddress.append(ConsoleHandlerResourceDefinition.CONSOLE_HANDLER_PATH),
                                new RejectExpressionsConfig(ConsoleHandlerResourceDefinition.TARGET))
                        .addFailedAttribute(loggingProfileAddress.append(PeriodicSizeRotatingHandlerResourceDefinition.PERIODIC_SIZE_ROTATING_HANDLER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(loggingProfileAddress.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new FailedOperationTransformationConfig.NewAttributesConfig(SizeRotatingHandlerResourceDefinition.SUFFIX)));
    }

    @Test
    public void testTransformersEAP700() throws Exception {
        testEap7Transformer(ModelTestControllerVersion.EAP_7_0_0, ModelVersion.create(3, 0, 0), readResource("/logging_3_0.xml") );
    }

    private void testEap7Transformer(final ModelTestControllerVersion controllerVersion, final ModelVersion legacyModelVersion, final String subsystemXml, final ModelFixer... modelFixers) throws Exception {
        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance())
                .setSubsystemXml(subsystemXml);

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, legacyModelVersion)
                .addMavenResourceURL(controllerVersion.getCoreMavenGroupId() + ":wildfly-logging:" + controllerVersion.getCoreVersion())
                .dontPersistXml()
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class)
                .configureReverseControllerCheck(LoggingTestEnvironment.getManagementInstance(), null);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());
        KernelServices legacyServices = mainServices.getLegacyServices(legacyModelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());
        Assert.assertNotNull(legacyServices);
        checkSubsystemModelTransformation(mainServices, legacyModelVersion, new ChainedModelFixer(modelFixers));
    }

    private void testEapTransformer(final ModelTestControllerVersion controllerVersion, final ModelVersion legacyModelVersion, final String subsystemXml, final ModelFixer... modelFixers) throws Exception {

        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance())
                .setSubsystemXml(subsystemXml);

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, legacyModelVersion)
                .addMavenResourceURL("org.jboss.as:jboss-as-logging:" + controllerVersion.getMavenGavVersion())
                .dontPersistXml()
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class)
                .configureReverseControllerCheck(LoggingTestEnvironment.getManagementInstance(), null);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());
        KernelServices legacyServices = mainServices.getLegacyServices(legacyModelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());
        Assert.assertNotNull(legacyServices);
        checkSubsystemModelTransformation(mainServices, legacyModelVersion, new ChainedModelFixer(modelFixers));
    }

    private void testEapFailedTransformers(final ModelTestControllerVersion controllerVersion, final ModelVersion legacyModelVersion, final String subsystemXml, final FailedOperationTransformationConfig config) throws Exception {
        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance());

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, legacyModelVersion)
                .addMavenResourceURL("org.jboss.as:jboss-as-logging:" + controllerVersion.getMavenGavVersion())
                .dontPersistXml()
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class)
                .configureReverseControllerCheck(LoggingTestEnvironment.getManagementInstance(), null);


        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(legacyModelVersion);

        Assert.assertNotNull(legacyServices);
        Assert.assertTrue("main services did not boot", mainServices.isSuccessfulBoot());
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        final List<ModelNode> ops = builder.parseXml(subsystemXml);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, legacyModelVersion, ops, config);
    }

    private void testWildFlyTransformer(final ModelTestControllerVersion controllerVersion, final ModelVersion legacyModelVersion, final String subsystemXml, final ModelFixer... modelFixers) throws Exception {

        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance())
                .setSubsystemXml(subsystemXml);

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, legacyModelVersion)
                .addMavenResourceURL("org.wildfly:wildfly-logging:" + controllerVersion.getMavenGavVersion())
                .dontPersistXml()
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class)
                .configureReverseControllerCheck(LoggingTestEnvironment.getManagementInstance(), null);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());
        KernelServices legacyServices = mainServices.getLegacyServices(legacyModelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());
        Assert.assertNotNull(legacyServices);
        checkSubsystemModelTransformation(mainServices, legacyModelVersion, new ChainedModelFixer(modelFixers));
    }

    private void testWildFlyFailedTransformers(final ModelTestControllerVersion controllerVersion, final ModelVersion legacyModelVersion, final String subsystemXml, final FailedOperationTransformationConfig config) throws Exception {
        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance());

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, legacyModelVersion)
                .addMavenResourceURL("org.wildfly:wildfly-logging:" + controllerVersion.getMavenGavVersion())
                .dontPersistXml()
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class)
                .configureReverseControllerCheck(LoggingTestEnvironment.getManagementInstance(), null);


        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(legacyModelVersion);

        Assert.assertNotNull(legacyServices);
        Assert.assertTrue("main services did not boot", mainServices.isSuccessfulBoot());
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        final List<ModelNode> ops = builder.parseXml(subsystemXml);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, legacyModelVersion, ops, config);
    }

    private static class ChainedModelFixer implements ModelFixer {
        private final ModelFixer[] modelFixers;

        private ChainedModelFixer(final ModelFixer... modelFixers) {
            this.modelFixers = modelFixers;
        }

        @Override
        public ModelNode fixModel(final ModelNode modelNode) {
            ModelNode result = modelNode;
            for (ModelFixer modelFixer : modelFixers) {
                result = modelFixer.fixModel(result);
            }
            return result;
        }
    }

    private static class AsyncModelFixer implements ModelFixer {

        static final AsyncModelFixer INSTANCE = new AsyncModelFixer();

        @Override
        public ModelNode fixModel(final ModelNode modelNode) {
            // Find the async-handler
            if (modelNode.hasDefined(AsyncHandlerResourceDefinition.ASYNC_HANDLER)) {
                final ModelNode asyncHandlers = modelNode.get(AsyncHandlerResourceDefinition.ASYNC_HANDLER);
                for (Property asyncHandler : asyncHandlers.asPropertyList()) {
                    final ModelNode async = asyncHandler.getValue();
                    async.remove(CommonAttributes.ENCODING.getName());
                    async.remove(AbstractHandlerDefinition.FORMATTER.getName());
                    asyncHandlers.get(asyncHandler.getName()).set(async);
                }
            }
            return modelNode;
        }
    }

    private static class AttributeRemovalModelFixer implements ModelFixer {
        private final AttributeDefinition[] attributes;

        AttributeRemovalModelFixer(final AttributeDefinition... attributes) {
            this.attributes = attributes;
        }

        @Override
        public ModelNode fixModel(final ModelNode modelNode) {
            // Recursively remove the attributes
            if (modelNode.getType() == ModelType.OBJECT) {
                for (Property property : modelNode.asPropertyList()) {
                    final String name = property.getName();
                    final ModelNode value = property.getValue();
                    if (value.isDefined()) {
                        if (value.getType() == ModelType.OBJECT) {
                            modelNode.get(name).set(fixModel(value));
                        } else {
                            for (AttributeDefinition attribute : attributes) {
                                if (name.equals(attribute.getName())) {
                                    modelNode.remove(name);
                                }
                            }
                        }
                    }
                }
            }
            return modelNode;
        }
    }

    private static class AttributeValueChangerModelFixer implements ModelFixer {
        private final AttributeDefinition attribute;
        private final String from;
        private final String to;

        private AttributeValueChangerModelFixer(final AttributeDefinition attribute, final String from, final String to) {
            this.attribute = attribute;
            this.from = from;
            this.to = to;
        }

        @Override
        public ModelNode fixModel(final ModelNode modelNode) {
            // Recursively remove the attributes
            if (modelNode.getType() == ModelType.OBJECT) {
                for (Property property : modelNode.asPropertyList()) {
                    final String name = property.getName();
                    final ModelNode value = property.getValue();
                    if (value.isDefined()) {
                        if (value.getType() == ModelType.OBJECT) {
                            modelNode.get(name).set(fixModel(value));
                        } else if (value.getType() == ModelType.STRING) {
                            if (name.equals(attribute.getName())) {
                                modelNode.get(name).set(value.asString().replace(from, to));
                            }
                        }
                    }
                }
            }
            return modelNode;
        }
    }
}
