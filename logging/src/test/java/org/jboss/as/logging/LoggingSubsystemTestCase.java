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

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.FailedOperationTransformationConfig.NewAttributesConfig;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
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
    public void testTransformers720() throws Exception {
        testTransformer1_2_0(ModelTestControllerVersion.V7_2_0_FINAL);
    }

    @Test
    public void testFailedTransformedBootOperations720() throws Exception {
        testFailedTransformedBootOperations1_2_0(ModelTestControllerVersion.V7_2_0_FINAL);
    }

    private void testTransformer1_2_0(final ModelTestControllerVersion controllerVersion) throws Exception {
        final String subsystemXml = readResource("/logging_1_2.xml");
        final ModelVersion modelVersion = ModelVersion.create(1, 2, 0);

        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance())
                .setSubsystemXml(subsystemXml);

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, modelVersion)
                .addMavenResourceURL("org.jboss.as:jboss-as-logging:" + controllerVersion.getMavenGavVersion())
                .dontPersistXml()
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class)
                .configureReverseControllerCheck(LoggingTestEnvironment.getManagementInstance(), null);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());
        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());
        Assert.assertNotNull(legacyServices);
        checkSubsystemModelTransformation(mainServices, modelVersion, AsyncModelFixer.INSTANCE);
    }

    private void testFailedTransformedBootOperations1_2_0(final ModelTestControllerVersion controllerVersion) throws Exception {
        final ModelVersion modelVersion = ModelVersion.create(1, 2, 0);
        final KernelServicesBuilder builder = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance());

        // Create the legacy kernel
        builder.createLegacyKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance(), controllerVersion, modelVersion)
                .addMavenResourceURL("org.jboss.as:jboss-as-logging:" + controllerVersion.getMavenGavVersion())
                .addSingleChildFirstClass(LoggingTestEnvironment.class, LoggingTestEnvironment.LoggingInitializer.class);


        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);

        Assert.assertNotNull(legacyServices);
        Assert.assertTrue("main services did not boot", mainServices.isSuccessfulBoot());
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        final List<ModelNode> ops = builder.parseXmlResource("/expressions_1_2.xml");
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, ops,
                new FailedOperationTransformationConfig()
                        .addFailedAttribute(SUBSYSTEM_ADDRESS,
                                new NewAttributesConfig(LoggingResourceDefinition.ATTRIBUTES))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(FileHandlerResourceDefinition.FILE_HANDLER_PATH),
                                new NewAttributesConfig(FileHandlerResourceDefinition.NAMED_FORMATTER))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_HANDLER_PATH),
                                new NewAttributesConfig(SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PatternFormatterResourceDefinition.PATTERN_FORMATTER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(CommonAttributes.LOGGING_PROFILE).append(FileHandlerResourceDefinition.FILE_HANDLER_PATH),
                                new NewAttributesConfig(FileHandlerResourceDefinition.NAMED_FORMATTER))
                        .addFailedAttribute(SUBSYSTEM_ADDRESS.append(CommonAttributes.LOGGING_PROFILE).append(PatternFormatterResourceDefinition.PATTERN_FORMATTER_PATH),
                                FailedOperationTransformationConfig.REJECTED_RESOURCE)
        );
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
}
