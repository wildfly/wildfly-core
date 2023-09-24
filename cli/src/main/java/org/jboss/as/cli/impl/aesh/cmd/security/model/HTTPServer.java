/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.IOException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import static org.jboss.as.cli.Util.isSuccess;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.dmr.ModelNode;

/**
 * Utility class to interact with undertow subsystem.
 *
 * @author jdenise@redhat.com
 */
public class HTTPServer {

    public static final String DEFAULT_SERVER = "default-server";

    public static void enableSSL(String serverName, boolean addHttpsListener, String listenerName,
            String socketBinding, boolean noOverride, CommandContext context, SSLSecurityBuilder builder) throws OperationFormatException, IOException {
        if (serverName == null) {
            serverName = DefaultResourceNames.getDefaultServerName(context);
        }
        if (serverName == null) {
            throw new OperationFormatException("No default server name found.");
        }
        final String sName = serverName;
        if (addHttpsListener) {
            if (!HTTPServer.hasHttpsListener(context, serverName, listenerName)) {
                builder.addStep(addHttpsListener(serverName, listenerName, socketBinding, builder.getServerSSLContext().getName()), new SSLSecurityBuilder.FailureDescProvider() {
                    @Override
                    public String stepFailedDescription() {
                        return "Adding https-listener " + listenerName + " to server " + sName;
                    }
                });
            } else {
                builder.addStep(writeServerAttribute(serverName, listenerName, Util.SSL_CONTEXT,
                        builder.getServerSSLContext().getName()), new SSLSecurityBuilder.FailureDescProvider() {
                    @Override
                    public String stepFailedDescription() {
                        return "Writing "
                                + Util.SSL_CONTEXT
                                + " attribute on https-listener " + sName;
                    }
                });
            }
        } else {
            builder.addStep(writeServerAttribute(serverName, listenerName, Util.SSL_CONTEXT,
                    builder.getServerSSLContext().getName()), new SSLSecurityBuilder.FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Writing "
                            + Util.SSL_CONTEXT
                            + " attribute on https-listener " + sName;
                }
            });
        }
        if (!noOverride && isLegacySecurityRealmSupported(context)) {
            builder.addStep(writeServerAttribute(serverName, listenerName, Util.SECURITY_REALM, null),
                    new SSLSecurityBuilder.FailureDescProvider() {
                        @Override
                        public String stepFailedDescription() {
                            return "Writing "
                                    + Util.SECURITY_REALM
                            + " attribute on http-server " + sName;
                }
            });
        }
    }

    private static ModelNode writeServerAttribute(String serverName, String httpsListener, String name, String value) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, httpsListener);
        builder.addProperty(Util.NAME, name);
        if (value != null) {
            builder.addProperty(Util.VALUE, value);
        }
        return builder.buildRequest();
    }

    public static String disableSSL(CommandContext context, String serverName, boolean removeHttpsListener,
            String httpsListener, String defaultAppSSLContext, ModelNode steps) throws OperationFormatException, IOException {
        if (serverName == null) {
            serverName = DefaultResourceNames.getDefaultServerName(context);
        }
        if (removeHttpsListener) {
            // New behavior, remove the https listener.
            steps.add(removeHttpsListener(context, serverName, httpsListener));
        } else {
            if (isLegacySecurityRealmSupported(context)) {
                steps.add(writeServerAttribute(serverName, httpsListener, Util.SSL_CONTEXT, null));
                steps.add(writeServerAttribute(serverName, httpsListener, Util.SECURITY_REALM, DefaultResourceNames.getDefaultApplicationLegacyRealm()));
            } else {
               if (ElytronUtil.hasServerSSLContext(context, defaultAppSSLContext)) {
                   steps.add(writeServerAttribute(serverName, httpsListener, Util.SSL_CONTEXT,
                    defaultAppSSLContext));
               } else {
                   throw new OperationFormatException("No "+ defaultAppSSLContext + " default SSL Context to use.");
               }
            }
        }
        return serverName;
    }

    public static String getSSLContextName(String serverName, String httpsListener, CommandContext ctx) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, httpsListener);
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

    public static boolean isLegacySecurityRealmSupported(CommandContext commandContext) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE_DESCRIPTION);
        builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
        builder.addNode(Util.SECURITY_REALM, "?");
        ModelNode response = commandContext.getModelControllerClient().execute(builder.buildRequest());
        return Util.isSuccess(response);
    }

    public static boolean hasHttpsListener(CommandContext commandContext, String serverName, String httpsListener) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, httpsListener);
        ModelNode response = commandContext.getModelControllerClient().execute(builder.buildRequest());
        return Util.isSuccess(response);
    }

    public static ModelNode addHttpsListener(String serverName, String httpsListenerName, String socketBindingName, String sslContext) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.ADD);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, httpsListenerName);
        builder.addProperty(Util.SOCKET_BINDING, socketBindingName);
        builder.addProperty(Util.SSL_CONTEXT, sslContext);
        return builder.buildRequest();
    }

    public static ModelNode removeHttpsListener(CommandContext commandContext, String serverName, String httpsListener) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.REMOVE);
        builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
        builder.addNode(Util.SERVER, serverName);
        builder.addNode(Util.HTTPS_LISTENER, httpsListener);
        return builder.buildRequest();
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
