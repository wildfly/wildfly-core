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

import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
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
        try (final FileInputStream in = new FileInputStream(new File(dir, "logging.properties"))) {
            config.configure(in);
            compare(currentModel, config);
        }
        logContext.close();
        kernelServices.shutdown();
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
}
