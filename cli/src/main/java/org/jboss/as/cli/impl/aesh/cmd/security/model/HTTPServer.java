/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import static org.jboss.as.cli.Util.isSuccess;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Utility class to interact with undertow subsystem.
 *
 * @author jdenise@redhat.com
 */
public class HTTPServer {

    public static final String DEFAULT_SERVER = "default-server";

    public static void enableSSL(String serverName, boolean noOverride, CommandContext context, SSLSecurityBuilder builder) throws OperationFormatException {
        if (serverName == null) {
            serverName = DefaultResourceNames.getDefaultServerName(context);
        }
        if (serverName == null) {
            throw new OperationFormatException("No default server name found.");
        }
        final String sName = serverName;
        if (!noOverride) {
            builder.addStep(writeServerAttribute(serverName, Util.SECURITY_REALM, null),
                    new SSLSecurityBuilder.FailureDescProvider() {
                        @Override
                        public String stepFailedDescription() {
                            return "Writing "
                                    + Util.SECURITY_REALM
                            + " attribute on http-server " + sName;
                }
            });
        }
        builder.addStep(writeServerAttribute(serverName, Util.SSL_CONTEXT,
                builder.getServerSSLContext().getName()), new SSLSecurityBuilder.FailureDescProvider() {
            @Override
            public String stepFailedDescription() {
                return "Writing "
                        + Util.SSL_CONTEXT
                        + " attribute on http-server " + sName;
            }
        });
    }

    private static ModelNode writeServerAttribute(String serverName, String name, String value) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, Util.HTTPS);
        builder.addProperty(Util.NAME, name);
        if (value != null) {
            builder.addProperty(Util.VALUE, value);
        }
        return builder.buildRequest();
    }

    public static String disableSSL(CommandContext context, String serverName, ModelNode steps) throws OperationFormatException {
        if (serverName == null) {
            serverName = DefaultResourceNames.getDefaultServerName(context);
        }
        steps.add(writeServerAttribute(serverName, Util.SSL_CONTEXT, null));
        steps.add(writeServerAttribute(serverName, Util.SECURITY_REALM, DefaultResourceNames.getDefaultApplicationLegacyRealm()));
        return serverName;
    }

    public static String getSSLContextName(String serverName, CommandContext ctx) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, Util.HTTPS);
        builder.addProperty(Util.NAME, Util.SSL_CONTEXT);
        request = builder.buildRequest();

        final ModelNode outcome = ctx.getModelControllerClient().execute(request);
        if (isSuccess(outcome)) {
            if (outcome.hasDefined(Util.RESULT)) {
                return outcome.get(Util.RESULT).asString();
            }
        }

        return null;
    }

    private static List<String> getNames(ModelControllerClient client, String type) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
            builder.addProperty(Util.CHILD_TYPE, type);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (Util.isSuccess(outcome)) {
                return Util.getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static boolean isUnderowSupported(CommandContext commandContext) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        ModelNode response = commandContext.getModelControllerClient().execute(builder.buildRequest());
        return Util.isSuccess(response);
    }

    public static boolean isReferencedSecurityDomainSupported(CommandContext commandContext) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE_DESCRIPTION);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.APPLICATION_SECURITY_DOMAIN, "?");
        ModelNode response = commandContext.getModelControllerClient().execute(builder.buildRequest());
        if (Util.isSuccess(response)) {
            if (response.get(Util.RESULT).hasDefined(Util.ATTRIBUTES)) {
                return response.get(Util.RESULT).get(Util.ATTRIBUTES).hasDefined(Util.SECURITY_DOMAIN);
            }
        }
        return false;
    }

    public static ApplicationSecurityDomain getSecurityDomain(CommandContext ctx, String name) throws OperationFormatException, IOException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.APPLICATION_SECURITY_DOMAIN, name);
        ModelNode mn = ctx.getModelControllerClient().execute(builder.buildRequest());
        ApplicationSecurityDomain dom = null;
        if (Util.isSuccess(mn)) {
            ModelNode result = mn.get(Util.RESULT);
            String factory = null;
            String secDomain = null;
            if (result.hasDefined(Util.HTTP_AUTHENTICATION_FACTORY)) {
                factory = result.get(Util.HTTP_AUTHENTICATION_FACTORY).asString();
            }
            if (result.hasDefined(Util.SECURITY_DOMAIN)) {
                secDomain = result.get(Util.SECURITY_DOMAIN).asString();
            }
            dom = new ApplicationSecurityDomain(name, factory, secDomain);
        }
        return dom;
    }

    public static void writeReferencedSecurityDomain(AuthSecurityBuilder authBuilder,
            String securityDomain, CommandContext ctx) throws OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.APPLICATION_SECURITY_DOMAIN, securityDomain);
        builder.addProperty(Util.NAME, Util.SECURITY_DOMAIN);
        builder.addProperty(Util.VALUE, authBuilder.getReferencedSecurityDomain());
        authBuilder.getSteps().add(builder.buildRequest());
    }

    public static boolean hasAuthFactory(CommandContext ctx, String securityDomain) throws OperationFormatException, IOException {
        ApplicationSecurityDomain dom = getSecurityDomain(ctx, securityDomain);
        if (dom != null) {
            return dom.getFactory() != null;
        }
        return false;
    }

    public static void enableHTTPAuthentication(AuthSecurityBuilder builder, String securityDomain, CommandContext ctx) throws Exception {
        final DefaultOperationRequestBuilder reqBuilder = new DefaultOperationRequestBuilder();
        reqBuilder.setOperationName(Util.ADD);
        reqBuilder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        reqBuilder.addNode(Util.APPLICATION_SECURITY_DOMAIN, securityDomain);
        if (builder.getReferencedSecurityDomain() == null) {
            reqBuilder.addProperty(Util.HTTP_AUTHENTICATION_FACTORY, builder.getAuthFactory().getName());
        } else {
            reqBuilder.addProperty(Util.SECURITY_DOMAIN, builder.getReferencedSecurityDomain());
        }
        builder.getSteps().add(reqBuilder.buildRequest());
    }

    public static ModelNode disableHTTPAuthentication(String securityDomain, CommandContext ctx) throws Exception {
        final DefaultOperationRequestBuilder reqBuilder = new DefaultOperationRequestBuilder();
        reqBuilder.setOperationName(Util.REMOVE);
        reqBuilder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        reqBuilder.addNode(Util.APPLICATION_SECURITY_DOMAIN, securityDomain);
        return reqBuilder.buildRequest();
    }

    public static String getSecurityDomainFactoryName(String securityDomain, CommandContext ctx) throws IOException, OperationFormatException {
        ApplicationSecurityDomain dom = getSecurityDomain(ctx, securityDomain);
        if (dom != null) {
            return dom.getFactory();
        }
        return null;
    }

    public static String getReferencedSecurityDomainName(String securityDomain, CommandContext ctx) throws IOException, OperationFormatException {
        ApplicationSecurityDomain dom = getSecurityDomain(ctx, securityDomain);
        if (dom != null) {
            return dom.getSecurityDomain();
        }
        return null;
    }

}
