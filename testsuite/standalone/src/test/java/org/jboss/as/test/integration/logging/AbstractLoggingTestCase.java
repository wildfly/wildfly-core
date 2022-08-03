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

package org.jboss.as.test.integration.logging;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper.ServerDeploymentException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractLoggingTestCase {

    public static final String DEPLOYMENT_NAME = "logging-test.jar";

    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "logging");

    public static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(SUBSYSTEM_PATH);

    @Inject
    protected static ManagementClient client;

    public static JavaArchive createDeployment() {
        return createDeployment(LoggingServiceActivator.class, LoggingServiceActivator.DEPENDENCIES);
    }

    public static JavaArchive createDeployment(final Map<String, String> manifestEntries) {
        return createDeployment(LoggingServiceActivator.class, manifestEntries, LoggingServiceActivator.DEPENDENCIES);
    }

    public static JavaArchive createDeployment(final Class<? extends ServiceActivator> serviceActivator, final Class<?>... classes) {
        return createDeployment(serviceActivator, Collections.<String, String>emptyMap(), classes);
    }

    public static JavaArchive createDeployment(final Class<? extends ServiceActivator> serviceActivator,
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
        return addPermissions(archive);
    }

    public static JavaArchive addPermissions(final JavaArchive archive, final Permission... additionalPermissions) {
        final Permission[] permissions = LoggingServiceActivator.appendPermissions(additionalPermissions);
        return archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(permissions), "permissions.xml");
    }

    public static ModelNode executeOperation(final ModelNode op) throws IOException {
        return executeOperation(Operation.Factory.create(op));
    }

    public static ModelNode executeOperation(final Operation op) throws IOException {
        return executeOperation(op, true);
    }

    public static ModelNode executeOperation(final Operation op, final boolean reloadIfRequired) throws IOException {
        ModelNode result = client.getControllerClient().execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).toString());
        }
        // Reload if required
        if (reloadIfRequired && result.hasDefined(ClientConstants.RESPONSE_HEADERS)) {
            final ModelNode responseHeaders = result.get(ClientConstants.RESPONSE_HEADERS);
            if (responseHeaders.hasDefined("process-state")) {
                if (ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED.equals(responseHeaders.get("process-state").asString())) {
                    ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());
                }
            }
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
            result = client.getControllerClient().execute(op);
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

    public static void setEnabled(final ModelNode address, final boolean enabled) throws IOException {
        final ModelNode op = Operations.createWriteAttributeOperation(address, ModelDescriptionConstants.ENABLED, enabled);
        executeOperation(op);
    }

    /**
     * Deploys the archive to the running server.
     *
     * @param archive     the archive to deploy
     * @param runtimeName the runtime name for the deployment
     *
     * @throws IOException if an error occurs deploying the archive
     */
    public static void deploy(final Archive<?> archive, final String runtimeName) throws IOException {
        // Use an operation to allow overriding the runtime name
        final ModelNode address = Operations.createAddress(DEPLOYMENT, archive.getName());
        final ModelNode addOp = createAddOperation(address);
        if (runtimeName != null && !archive.getName().equals(runtimeName)) {
            addOp.get(RUNTIME_NAME).set(runtimeName);
        }
        addOp.get("enabled").set(true);
        // Create the content for the add operation
        final ModelNode contentNode = addOp.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(0);

        // Create an operation and add the input archive
        final OperationBuilder builder = OperationBuilder.create(addOp);
        builder.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());

        // Deploy the content and check the results
        final ModelNode result = client.getControllerClient().execute(builder.build());
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to deploy %s: %s", archive, Operations.getFailureDescription(result).asString()));
        }
    }

    /**
     * Undeploys the application from the running server.
     *
     * @param runtimeNames the runtime names
     *
     * @throws ServerDeploymentException if an error occurs undeploying the application
     */
    public static void undeploy(final String... runtimeNames) throws ServerDeploymentException {
        final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
        final Collection<Throwable> errors = new ArrayList<>();
        for (String runtimeName : runtimeNames) {
            try {
                final ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("deployment", runtimeName));
                final ModelNode result = client.getControllerClient().execute(op);
                if (Operations.isSuccessfulOutcome(result))
                    helper.undeploy(runtimeName);
            } catch (Exception e) {
                errors.add(e);
            }
        }
        if (!errors.isEmpty()) {
            final RuntimeException e = new RuntimeException("Error undeploying: " + Arrays.asList(runtimeNames));
            for (Throwable error : errors) {
                e.addSuppressed(error);
            }
            throw e;
        }
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
     * @throws UnsupportedEncodingException if the UTF-8 encoding is not supported
     * @throws MalformedURLException        if the URL is malformed
     */
    public static URL createUrl(final String msg, final Map<String, String> params) throws UnsupportedEncodingException, MalformedURLException {
        final StringBuilder spec = new StringBuilder()
                .append('?')
                .append(LoggingServiceActivator.MSG_KEY)
                .append('=')
                .append(URLEncoder.encode(msg, "utf-8"));
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
     * @throws IOException if there was an error creating the URL or connecting to he server
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
     * @throws IOException if there was an error creating the URL or connecting to he server
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
     * @throws IOException if an error occurs connecting to the server
     */
    public static int getResponse(final URL url) throws IOException {
        return ((HttpURLConnection) url.openConnection()).getResponseCode();
    }

    /**
     * Reads the deployment resource.
     *
     * @param deploymentName the name of the deployment
     *
     * @return the model for the deployment
     *
     * @throws IOException if an error occurs connecting to the server
     */
    public static ModelNode readDeploymentResource(final String deploymentName) throws IOException {
        // Don't guess on the address, just parse it as it comes back
        final ModelNode address = Operations.createAddress("deployment", deploymentName, "subsystem", "logging", "configuration", "*");
        final ModelNode op = Operations.createReadResourceOperation(address);
        op.get("include-runtime").set(true);
        final ModelNode result = executeOperation(op);
        // Get the resulting model
        final List<ModelNode> loggingConfigurations = Operations.readResult(result).asList();
        Assert.assertEquals("There should only be one logging configuration defined", 1, loggingConfigurations.size());
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfigurations.get(0)).asPropertyList());
        return readDeploymentResource(deploymentName, resultAddress.getLast().getValue().asString());
    }

    /**
     * Reads the deployment resource.
     *
     * @param deploymentName    the name of the deployment
     * @param configurationName the name of the configuration for the address
     *
     * @return the model for the deployment
     *
     * @throws IOException if an error occurs connecting to the server
     */
    public static ModelNode readDeploymentResource(final String deploymentName, final String configurationName) throws IOException {
        ModelNode address = Operations.createAddress("deployment", deploymentName, "subsystem", "logging", "configuration", configurationName);
        ModelNode op = Operations.createReadResourceOperation(address, true);
        op.get("include-runtime").set(true);
        final ModelNode result = Operations.readResult(executeOperation(op));
        // Add the address on the result as the tests might need it
        result.get(ModelDescriptionConstants.OP_ADDR).set(address);
        return result;
    }
}
