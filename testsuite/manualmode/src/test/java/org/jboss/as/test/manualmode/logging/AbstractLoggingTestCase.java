/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketPermission;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.Collections;
import java.util.Map;
import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper.ServerDeploymentException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.wildfly.core.testrunner.ServerController;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractLoggingTestCase {

    public static final String DEPLOYMENT_NAME = "logging-test.jar";

    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "logging");

    public static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(SUBSYSTEM_PATH);

    @Inject
    protected static ServerController container;

    protected static ModelControllerClient client;

    @BeforeClass
    public static void configureClient() {
        client = TestSuiteEnvironment.getModelControllerClient();
    }

    static JavaArchive createDeployment() {
        return createDeployment(LoggingServiceActivator.class, LoggingServiceActivator.DEPENDENCIES);
    }

    static JavaArchive createDeployment(final Map<String, String> manifestEntries) {
        return createDeployment(LoggingServiceActivator.class, manifestEntries, LoggingServiceActivator.DEPENDENCIES);
    }

    static JavaArchive createDeployment(final Class<? extends ServiceActivator> serviceActivator, final Class<?>... classes) {
        return createDeployment(serviceActivator, Collections.<String, String>emptyMap(), classes);
    }

    static JavaArchive createDeployment(final Class<? extends ServiceActivator> serviceActivator,
                                        final Map<String, String> manifestEntries, final Class<?>... classes) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, DEPLOYMENT_NAME);
        archive.addClasses(classes);
        archive.addAsServiceProviderAndClasses(ServiceActivator.class, serviceActivator);
        boolean addDeps = true;
        final StringBuilder manifest = new StringBuilder();
        for (String key : manifestEntries.keySet()) {
            if ("Dependencies".equals(key)) {
                addDeps = false;
                manifest.append(key)
                        .append(": ")
                        .append("io.undertow.core,")
                        .append(manifestEntries.get(key))
                        .append('\n');
            } else {
                manifest.append(key)
                        .append(": ")
                        .append(manifestEntries.get(key))
                        .append('\n');
            }
        }
        if (addDeps) {
            manifest.append("Dependencies: io.undertow.core");
        }
        archive.addAsResource(new StringAsset(manifest.toString()), "META-INF/MANIFEST.MF");
        return addPermissions(archive,
                new SocketPermission(TestSuiteEnvironment.getHttpAddress()+ ":0", "listen,resolve"));
    }

    public static JavaArchive addPermissions(final JavaArchive archive, final Permission... additionalPermissions) {
        final Permission[] permissions = LoggingServiceActivator.appendPermissions(additionalPermissions);
        return archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(permissions), "permissions.xml");
    }

    public static ModelNode executeOperation(final ModelNode op) throws IOException {
        ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.assertTrue(Operations.getFailureDescription(result).toString(), false);
        }
        return result;
    }

    public static ModelNode executeOperation(final Operation op) throws IOException {
        ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.assertTrue(Operations.getFailureDescription(result).toString(), false);
        }
        return result;
    }

    public static String resolveRelativePath(final String relativePath) {
        final ModelNode address = PathAddress.pathAddress(
                PathElement.pathElement(ModelDescriptionConstants.PATH, relativePath)
        ).toModelNode();
        final ModelNode result;
        try {
            final ModelNode op = Operations.createReadAttributeOperation(address, ModelDescriptionConstants.PATH);
            result = client.execute(op);
            if (Operations.isSuccessfulOutcome(result)) {
                return Operations.readResult(result).asString();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException(Operations.getFailureDescription(result).asString());
    }

    public static Path getAbsoluteLogFilePath(final String filename) {
        return Paths.get(resolveRelativePath("jboss.server.log.dir"), filename);
    }

    /**
     * Deploys the {@link #createDeployment() default} archive to the running server.
     *
     * @throws ServerDeploymentException if an error occurs deploying the archive
     */
    public void deploy() throws ServerDeploymentException {
        deploy(createDeployment(), DEPLOYMENT_NAME);
    }

    /**
     * Deploys the {@link #createDeployment() default} archive to the running server.
     *
     * @param runtimeName the runtime name for the deployment
     *
     * @throws ServerDeploymentException if an error occurs deploying the archive
     */
    public void deploy(final String runtimeName) throws ServerDeploymentException {
        deploy(createDeployment(), runtimeName);
    }

    /**
     * Deploys the archive to the running server.
     *
     * @param archive the archive to deploy
     *
     * @throws ServerDeploymentException if an error occurs deploying the archive
     */
    public void deploy(final Archive<?> archive) throws ServerDeploymentException {
        deploy(archive, DEPLOYMENT_NAME);
    }

    /**
     * Deploys the archive to the running server.
     *
     * @param archive     the archive to deploy
     * @param runtimeName the runtime name for the deployment
     *
     * @throws ServerDeploymentException if an error occurs deploying the archive
     */
    public void deploy(final Archive<?> archive, final String runtimeName) throws ServerDeploymentException {
        container.deploy(archive, runtimeName);
    }

    /**
     * Undeploys the default application from the running server.
     *
     * @throws ServerDeploymentException if an error occurs undeploying the application
     */
    public void undeploy() throws ServerDeploymentException {
        undeploy(DEPLOYMENT_NAME);
    }

    /**
     * Undeploys the application from the running server.
     *
     * @param runtimeName the runtime name
     *
     * @throws ServerDeploymentException if an error occurs undeploying the application
     */
    public void undeploy(final String runtimeName) throws ServerDeploymentException {
        container.undeploy(runtimeName);
    }

    public static ModelNode createAddress(final String resourceKey, final String resourceName) {
        return PathAddress.pathAddress(
                SUBSYSTEM_PATH,
                PathElement.pathElement(resourceKey, resourceName)
        ).toModelNode();
    }

    public static ModelNode createAddress(final String... paths) {
        PathAddress address = SUBSYSTEM_ADDRESS;
        for (int i = 0; i < paths.length; i++) {
            final String key = paths[i];
            if (++i < paths.length) {
                address = address.append(PathElement.pathElement(key, paths[i]));
            } else {
                address = address.append(PathElement.pathElement(key));
            }
        }
        return address.toModelNode();
    }

    /**
     * Creates a new URL with the default context from {@link org.jboss.as.test.shared.TestSuiteEnvironment#getHttpUrl()}
     * and a query parameter of {@code msg} with a value of the argument. The argument will be encoded in UTF-8.
     *
     * @param msg    the non-encoded message to send
     * @param params an optional list of extra parameters to add to the URL.
     *
     * @return a URL to send to the server
     *
     * @throws java.io.UnsupportedEncodingException if the UTF-8 encoding is not supported
     * @throws java.net.MalformedURLException       if the URL is malformed
     */
    public static URL createUrl(final String msg, final Map<String, String> params) throws UnsupportedEncodingException, MalformedURLException {
        final StringBuilder spec = new StringBuilder("?msg=").append(URLEncoder.encode(msg, "utf-8"));
        for (String key : params.keySet()) {
            spec.append('&').append(key);
            final String value = params.get(URLEncoder.encode(key, "utf-8"));
            if (value != null) {
                spec.append('=').append(URLEncoder.encode(value, "utf-8"));
            }
        }
        return new URL(TestSuiteEnvironment.getHttpUrl(), spec.toString());
    }

    /**
     * {@link AbstractLoggingTestCase#createUrl(String, java.util.Map) Creates} a URL with the message provided and
     * opens a connection
     * to the sever returning the response code.
     *
     * @param msg the non-encoded message to send
     *
     * @return the response code from the server
     *
     * @throws java.io.IOException if there was an error creating the URL or connecting to he server
     * @see #createUrl(String, java.util.Map)
     * @see #getResponse(java.net.URL)
     */
    public static int getResponse(final String msg) throws IOException {
        return getResponse(createUrl(msg, Collections.<String, String>emptyMap()));
    }

    /**
     * {@link AbstractLoggingTestCase#createUrl(String, java.util.Map) Creates} a URL with the message provided and
     * opens a connection
     * to the sever returning the response code.
     *
     * @param msg the non-encoded message to send
     *
     * @return the response code from the server
     *
     * @throws java.io.IOException if there was an error creating the URL or connecting to he server
     * @see #createUrl(String, java.util.Map)
     * @see #getResponse(java.net.URL)
     */
    public static int getResponse(final String msg, final Map<String, String> params) throws IOException {
        return getResponse(createUrl(msg, params));
    }

    /**
     * Opens a connection to the server and returns the response code.
     *
     * @param url the URL to connect to
     *
     * @return the response code from the server
     *
     * @throws java.io.IOException if an error occurs connecting to the server
     */
    public static int getResponse(final URL url) throws IOException {
        return ((HttpURLConnection) url.openConnection()).getResponseCode();
    }

    protected static void checkLogs(final String msg, final Path file, final boolean expected) throws Exception {
        boolean logFound = false;
        if (Files.exists(file)) {
            try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(msg)) {
                        logFound = true;
                        break;
                    }
                }
            }
        }
        Assert.assertEquals(String.format("Message '%s' found in %s", msg, file), expected, logFound);
    }
}
