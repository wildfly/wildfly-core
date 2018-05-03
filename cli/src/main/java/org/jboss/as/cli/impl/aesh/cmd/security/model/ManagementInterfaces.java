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
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.dmr.ModelNode;

/**
 * Utility class to interact with management interfaces from core management.
 *
 * @author jdenise@redhat.com
 */
public class ManagementInterfaces {

    public static void enableSSL(String managementInterface, String secureSocketBinding,
            CommandContext ctx, SSLSecurityBuilder builder) throws IOException, OperationFormatException {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(ctx);
        }
        if (managementInterface == null) {
            throw new OperationFormatException("No default management interface found.");
        }
        if (Util.HTTP_INTERFACE.equals(managementInterface)) {
            if (secureSocketBinding == null) {
                secureSocketBinding = DefaultResourceNames.getDefaultHttpSecureSocketBindingName(managementInterface, ctx);
            }
            enableHTTPS(secureSocketBinding, builder);
        } else {
            if (secureSocketBinding != null) {
                throw new OperationFormatException("secure-sockect-binding not applicable to interface " + managementInterface);
            }
            enableNativeSSL(managementInterface, ctx, builder);
        }
    }

    public static String disableSSL(CommandContext context, String managementInterface, ModelNode steps)
            throws IOException, OperationFormatException {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(context);
        }
        if (managementInterface == null) {
            throw new OperationFormatException("No default management interface found.");
        }
        steps.add(writeInterfaceAttribute(managementInterface, Util.SSL_CONTEXT, null));
        if (Util.HTTP_INTERFACE.equals(managementInterface)) {
            steps.add(writeInterfaceAttribute(managementInterface, Util.SECURE_SOCKET_BINDING, null));
        }
        return managementInterface;
    }

    private static void enableHTTPS(String secureSocketBinding, SSLSecurityBuilder security) throws OperationFormatException {
        security.addStep(writeInterfaceAttribute(Util.HTTP_INTERFACE,
                Util.SSL_CONTEXT, security.getServerSSLContext().getName()), new SSLSecurityBuilder.FailureDescProvider() {
            @Override
            public String stepFailedDescription() {
                return "Writing " + Util.SSL_CONTEXT + " attribute of "
                        + Util.HTTP_INTERFACE;
            }
        });
        security.addStep(writeInterfaceAttribute(Util.HTTP_INTERFACE,
                Util.SECURE_SOCKET_BINDING, secureSocketBinding), new SSLSecurityBuilder.FailureDescProvider() {
            @Override
            public String stepFailedDescription() {
                return "Writing " + Util.SECURE_SOCKET_BINDING + " attribute of "
                        + Util.HTTP_INTERFACE;
            }
        });
    }

    private static ModelNode writeInterfaceAttribute(String itf, String name, String value) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.WRITE_ATTRIBUTE);
        builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
        builder.addNode(Util.MANAGEMENT_INTERFACE, itf);
        builder.addProperty(Util.NAME, name);
        if (value != null) {
            builder.addProperty(Util.VALUE, value);
        }
        return builder.buildRequest();
    }

    private static void enableNativeSSL(String managementInterface, CommandContext ctx,
            SSLSecurityBuilder security) throws OperationFormatException {
        security.addStep(writeInterfaceAttribute(managementInterface,
                Util.SSL_CONTEXT, security.getServerSSLContext().getName()), new SSLSecurityBuilder.FailureDescProvider() {
            @Override
            public String stepFailedDescription() {
                return "Writing " + Util.SECURE_SOCKET_BINDING + " attribute of "
                        + managementInterface;
            }
        });
    }

    public static String getManagementInterfaceSSLContextName(CommandContext ctx, String interfaceName) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
            builder.addNode(Util.MANAGEMENT_INTERFACE, interfaceName);
            builder.addProperty(Util.NAME, Util.SSL_CONTEXT);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                ModelNode mn = outcome.get(Util.RESULT);
                if (mn.isDefined()) {
                    return outcome.get(Util.RESULT).asString();
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

    public static String getManagementInterfaceSaslFactoryName(String managementInterface, CommandContext ctx) throws IOException, OperationFormatException {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(ctx);
        }
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
            builder.addNode(Util.MANAGEMENT_INTERFACE, managementInterface);
            String attributeName = Util.SASL_AUTHENTICATION_FACTORY;
            if (Util.HTTP_INTERFACE.equals(managementInterface)) {
                attributeName = Util.HTTP_UPGRADE + "." + Util.SASL_AUTHENTICATION_FACTORY;
            }
            builder.addProperty(Util.NAME, attributeName);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                ModelNode mn = outcome.get(Util.RESULT);
                if (mn.isDefined()) {
                    return outcome.get(Util.RESULT).asString();
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

    public static String getManagementInterfaceHTTPFactoryName(CommandContext ctx) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
            builder.addNode(Util.MANAGEMENT_INTERFACE, Util.HTTP_INTERFACE);
            builder.addProperty(Util.NAME, Util.HTTP_AUTHENTICATION_FACTORY);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                ModelNode mn = outcome.get(Util.RESULT);
                if (mn.isDefined()) {
                    return outcome.get(Util.RESULT).asString();
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

    public static void enableHTTPAuthentication(AuthSecurityBuilder http, CommandContext ctx) throws IOException, OperationFormatException {
        final ModelNode request = writeInterfaceAttribute(Util.HTTP_INTERFACE, Util.HTTP_AUTHENTICATION_FACTORY, http.getAuthFactory().getName());
        http.getSteps().add(request);
    }

    public static void enableSASL(String managementInterface, AuthSecurityBuilder sasl, CommandContext ctx) throws IOException, OperationFormatException {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(ctx);
        }
        String attributeName = Util.SASL_AUTHENTICATION_FACTORY;
        if (Util.HTTP_INTERFACE.equals(managementInterface)) {
            attributeName = Util.HTTP_UPGRADE + "." + Util.SASL_AUTHENTICATION_FACTORY;
        }
        final ModelNode request = writeInterfaceAttribute(managementInterface, attributeName, sasl.getAuthFactory().getName());
        sasl.getSteps().add(request);
    }

    public static ModelNode disableSASL(CommandContext context, String managementInterface)
            throws IOException, OperationFormatException {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(context);
        }
        String attributeName = Util.SASL_AUTHENTICATION_FACTORY;
        if (Util.HTTP_INTERFACE.equals(managementInterface)) {
            attributeName = Util.HTTP_UPGRADE + "." + Util.SASL_AUTHENTICATION_FACTORY;
        }
        return writeInterfaceAttribute(managementInterface, attributeName, null);
    }

    public static ModelNode disableHTTPAuth(CommandContext context)
            throws IOException, OperationFormatException {
        return writeInterfaceAttribute(Util.HTTP_INTERFACE, Util.HTTP_AUTHENTICATION_FACTORY, null);
    }
}
