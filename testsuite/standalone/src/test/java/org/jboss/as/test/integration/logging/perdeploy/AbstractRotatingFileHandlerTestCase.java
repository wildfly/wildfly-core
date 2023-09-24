/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.perdeploy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.logmanager.handlers.FileHandler;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractRotatingFileHandlerTestCase extends AbstractLoggingTestCase {

    static JavaArchive createDeployment(final Asset loggingConfig) {
        return createDeployment(LoggingServiceActivator.class, LoggingServiceActivator.DEPENDENCIES)
                .addAsManifestResource(loggingConfig, "logging.properties");
    }

    static void executeRequest(final String fileName, final String msg, final Map<String, String> params) throws IOException {
        final int statusCode = getResponse(msg, params);
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);
        final Path logDir = Paths.get(resolveRelativePath("jboss.server.log.dir"));
        // Walk the path and we should have the file name plus one ending in .1 as we should have logged enough to cause
        // at least one rotation.
        final Pattern pattern = Pattern.compile(parseFileName(fileName) + "(\\.log|\\.log\\.1|\\.log[0-9]{2}\\.1)");
        final Set<String> foundFiles = Files.list(logDir)
                .map(path -> path.getFileName().toString())
                .filter((name) -> pattern.matcher(name).matches())
                .collect(Collectors.toSet());
        // We should have at least two files
        Assert.assertEquals("Expected to have at least 2 files found " + foundFiles.size(), 2, foundFiles.size());
    }

    static Asset createLoggingConfiguration(final Class<? extends FileHandler> handlerType, final String fileName, final Map<String, String> additionalProperties) throws IOException {
        final Properties properties = new Properties();

        // Configure the root logger
        properties.setProperty("logger.level", "INFO");
        properties.setProperty("logger.handlers", fileName);

        // Configure the handler
        properties.setProperty("handler." + fileName, handlerType.getName());
        properties.setProperty("handler." + fileName + ".level", "ALL");
        properties.setProperty("handler." + fileName + ".formatter", "json");
        final StringBuilder configProperties = new StringBuilder("append,autoFlush,fileName");
        for (String key : additionalProperties.keySet()) {
            configProperties.append(',').append(key);
        }
        properties.setProperty("handler." + fileName + ".properties", configProperties.toString());
        properties.setProperty("handler." + fileName + ".append", "false");
        properties.setProperty("handler." + fileName + ".autoFlush", "true");
        properties.setProperty("handler." + fileName + ".fileName", "${jboss.server.log.dir}" + File.separatorChar + fileName);
        // Add the additional properties
        for (Map.Entry<String, String> entry : additionalProperties.entrySet()) {
            properties.setProperty("handler." + fileName + "." + entry.getKey(), entry.getValue());
        }

        // Add the JSON formatter
        properties.setProperty("formatter.json", "org.jboss.logmanager.formatters.JsonFormatter");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        properties.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), null);
        return new ByteArrayAsset(out.toByteArray());
    }

    private static String parseFileName(final String fileName) {
        final int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
}
