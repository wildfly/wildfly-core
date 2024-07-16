/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * The base add handler for the native management interface.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseNativeInterfaceAddStepHandler extends ManagementInterfaceAddStepHandler {

    @Override
    public void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String saslAuthenticationFactory = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SASL_AUTHENTICATION_FACTORY, model);
        final String sslContext = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SSL_CONTEXT, model);

        String serverName = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SERVER_NAME, model);
        Builder builder = OptionMap.builder();
        builder.set(RemotingOptions.SASL_PROTOCOL, BaseNativeInterfaceResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        if (serverName != null) {
            builder.set(RemotingOptions.SERVER_NAME, serverName);
        }
        final OptionMap options = builder.getMap();

        List<ServiceName> requiredServices = installServices(context, new NativeInterfaceCommonPolicy() {

            @Override
            public String getSaslAuthenticationFactory() {
                return saslAuthenticationFactory;
            }

            @Override
            public String getSSLContext() {
                return sslContext;
            }

            @Override
            public OptionMap getConnectorOptions() {
                return options;
            }
        }, model);
        addVerifyInstallationStep(context, requiredServices);
    }

    protected abstract List<ServiceName> installServices(OperationContext context, NativeInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException;
}
