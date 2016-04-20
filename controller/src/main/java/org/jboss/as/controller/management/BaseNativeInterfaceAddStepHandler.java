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

package org.jboss.as.controller.management;

import static org.jboss.as.controller.management.BaseNativeInterfaceResourceDefinition.NATIVE_MANAGEMENT_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * The base add handler for the native management interface.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseNativeInterfaceAddStepHandler extends AbstractAddStepHandler {

    protected BaseNativeInterfaceAddStepHandler(final AttributeDefinition[] attributeDefinitions) {
        super(NATIVE_MANAGEMENT_CAPABILITY, attributeDefinitions);
    }

    @Override
    public void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String securityRealm = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SECURITY_REALM, model);

        String serverName = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SERVER_NAME, model);
        Builder builder = OptionMap.builder();
        builder.set(RemotingOptions.SASL_PROTOCOL, BaseNativeInterfaceResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        if (serverName != null) {
            builder.set(RemotingOptions.SERVER_NAME, serverName);
        }
        final OptionMap options = builder.getMap();

        installServices(context, new NativeInterfaceCommonPolicy() {

            @Override
            public String getSecurityRealm() {
                return securityRealm;
            }

            @Override
            public OptionMap getConnectorOptions() {
                return options;
            }
        }, model);
    }

    private String asStringIfDefined(OperationContext context, AttributeDefinition attribute, ModelNode model) throws OperationFailedException {
        ModelNode attributeValue = attribute.resolveModelAttribute(context, model);

        return attributeValue.isDefined() ? attributeValue.asString() : null;
    }

    protected abstract void installServices(OperationContext context, NativeInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException;
}
