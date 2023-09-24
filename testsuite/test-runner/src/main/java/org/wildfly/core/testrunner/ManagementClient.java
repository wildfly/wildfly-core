/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.testrunner;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.RECURSIVE;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.management.remote.JMXServiceURL;

import org.jboss.as.controller.client.ModelControllerClient;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNNING_STATE_NORMAL;
import org.jboss.as.network.NetworkUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;

/**
 * A helper class to join management related operations, like extract sub system ip/port (web/jmx)
 * and deployment introspection.
 *
 * @author <a href="aslak@redhat.com">Aslak Knutsen</a>
 */
public class ManagementClient implements AutoCloseable, Closeable {

    private static final Logger logger = Logger.getLogger(ManagementClient.class);

    private static final String UNDERTOW = "undertow";
    private static final String NAME = "name";

    private final String mgmtAddress;
    private final int mgmtPort;
    private final String mgmtProtocol;
    private final ModelControllerClient client;

    private URI webUri;
    private URI ejbUri;

    // cache static RootNode
    private ModelNode rootNode = null;


    public ManagementClient(ModelControllerClient client, final String mgmtAddress, final int managementPort, final String protocol) {
        this.client = checkNotNullParam("client", client);
        this.mgmtAddress = mgmtAddress;
        this.mgmtPort = managementPort;
        this.mgmtProtocol = protocol;
    }

    //-------------------------------------------------------------------------------------||
    // Public API -------------------------------------------------------------------------||
    //-------------------------------------------------------------------------------------||

    public ModelControllerClient getControllerClient() {
        return client;
    }

    /**
     * @return The base URI or the web subsystem. Usually http://localhost:8080
     * @deprecated check if it is even used anywhere
     */
    @Deprecated
    public URI getWebUri() {
        if (webUri == null) {
            try {
                webUri = new URI("http://localhost:8080");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            try {
                if (rootNode == null) {
                    readRootNode();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ModelNode undertowNode = rootNode.get("subsystem", UNDERTOW);
            if (undertowNode.isDefined()) {
                List<Property> vhosts = undertowNode.get("server").asPropertyList();
                ModelNode socketBinding = new ModelNode();
                if (!vhosts.isEmpty()) {//if empty no virtual hosts defined
                    socketBinding = vhosts.get(0).getValue().get("http-listener", "default").get("socket-binding");
                }
                if (socketBinding.isDefined()) {
                    webUri = getBinding("http", socketBinding.asString());
                }
            }
        }
        return webUri;
    }

    public boolean isServerInRunningState() {
        try {
            ModelNode op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(OP_ADDR).setEmptyList();
            op.get(NAME).set("server-state");

            ModelNode rsp = client.execute(op);
            return SUCCESS.equals(rsp.get(OUTCOME).asString())
                    && !CONTROLLER_PROCESS_STATE_STARTING.equals(rsp.get(RESULT).asString())
                    && !CONTROLLER_PROCESS_STATE_STOPPING.equals(rsp.get(RESULT).asString());
        } catch (RuntimeException rte) {
            throw rte;
        } catch (IOException ex) {
            return false;
        }
    }

    public boolean isServerInNormalMode() {
        try {
            ModelNode op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(OP_ADDR).setEmptyList();
            op.get(NAME).set("running-mode");
            ModelNode rsp = client.execute(op);
            return SUCCESS.equals(rsp.get(OUTCOME).asString())
                    && RUNNING_STATE_NORMAL.toUpperCase().equals(rsp.get(RESULT).asString());
        } catch (RuntimeException rte) {
            throw rte;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            getControllerClient().close();
        } catch (IOException e) {
            throw new RuntimeException("Could not close connection", e);
        }
    }

    private void readRootNode() throws Exception {
        rootNode = readResource(new ModelNode());
    }

    private static ModelNode defined(final ModelNode node, final String message) {
        if (!node.isDefined()) { throw new IllegalStateException(message); }
        return node;
    }

    private URI getBinding(final String protocol, final String socketBinding) {
        try {
            final String socketBindingGroupName = rootNode.get("socket-binding-group").keys().iterator().next();
            final ModelNode operation = new ModelNode();
            operation.get(OP_ADDR).get("socket-binding-group").set(socketBindingGroupName);
            operation.get(OP_ADDR).get("socket-binding").set(socketBinding);
            operation.get(OP).set(READ_RESOURCE_OPERATION);
            operation.get("include-runtime").set(true);
            ModelNode binding = executeForResult(operation);
            String ip = binding.get("bound-address").asString();
            //it appears some system can return a binding with the zone specifier on the end
            if (ip.contains(":") && ip.contains("%")) {
                ip = ip.split("%")[0];
            }

            final int port = defined(binding.get("bound-port"), socketBindingGroupName + " -> " + socketBinding + " -> bound-port is undefined").asInt();

            return URI.create(protocol + "://" + NetworkUtils.formatPossibleIpv6Address(ip) + ":" + port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //-------------------------------------------------------------------------------------||
    // Common Management API Operations ---------------------------------------------------||
    //-------------------------------------------------------------------------------------||

    private ModelNode readResource(ModelNode address) throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(RECURSIVE).set("true");
        operation.get(OP_ADDR).set(address);

        return executeForResult(operation);
    }

    /**
     * Executes an operation and returns the result. If the operation was not successful an
     * {@link UnsuccessfulOperationException}
     * will be thrown.
     *
     * @param operation The operation
     * @return The result
     * @throws UnsuccessfulOperationException if the operation failed
     */
    public ModelNode executeForResult(final ModelNode operation) throws UnsuccessfulOperationException {
        try {
            final ModelNode result = client.execute(operation);
            checkSuccessful(result, operation);
            return result.get(RESULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkSuccessful(final ModelNode result,
                                 final ModelNode operation) throws UnsuccessfulOperationException {
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            logger.error("Operation " + operation + " did not succeed. Result was " + result);
            throw new UnsuccessfulOperationException(result.get(
                    FAILURE_DESCRIPTION).toString());
        }
    }


    public JMXServiceURL getRemoteJMXURL() {
        try {
            switch (mgmtProtocol) {
                case "http-remoting":
                case "remote+http":
                    return new JMXServiceURL("service:jmx:remote+http://" + NetworkUtils.formatPossibleIpv6Address(mgmtAddress) + ":" + mgmtPort);
                case "https-remoting":
                case "remote+https":
                    return new JMXServiceURL("service:jmx:remote+https://" + NetworkUtils.formatPossibleIpv6Address(mgmtAddress) + ":" + mgmtPort);
                default:
                    return new JMXServiceURL("service:jmx:remote://" + NetworkUtils.formatPossibleIpv6Address(mgmtAddress) + ":" + mgmtPort);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not create JMXServiceURL:" + this, e);
        }
    }

    public int getMgmtPort() {
        return mgmtPort;
    }

    public String getMgmtAddress() {
        return NetworkUtils.formatPossibleIpv6Address(mgmtAddress);
    }

    public String getMgmtProtocol() {
        return mgmtProtocol;
    }

    /**
     * @deprecated check if it is even used anywhere
     */
    @Deprecated
    public URI getRemoteEjbURL() {
        if (ejbUri == null) {
            URI webUri = getWebUri();
            try {
                ejbUri = new URI("remote+http", webUri.getUserInfo(), webUri.getHost(), webUri.getPort(),null,null,null);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return ejbUri;
    }
}
