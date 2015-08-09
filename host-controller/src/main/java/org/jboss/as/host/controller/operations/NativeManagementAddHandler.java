/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.operations;

import static org.jboss.as.host.controller.resources.NativeManagementResourceDefinition.ATTRIBUTE_DEFINITIONS;
import static org.jboss.as.host.controller.resources.NativeManagementResourceDefinition.NATIVE_MANAGEMENT_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.resources.NativeManagementResourceDefinition;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.services.net.NetworkInterfaceService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class NativeManagementAddHandler extends AbstractAddStepHandler {

    public static final String OPERATION_NAME = ModelDescriptionConstants.ADD;


    private final LocalHostControllerInfoImpl hostControllerInfo;


    public NativeManagementAddHandler(final LocalHostControllerInfoImpl hostControllerInfo) {
        super(NATIVE_MANAGEMENT_CAPABILITY, ATTRIBUTE_DEFINITIONS);
        this.hostControllerInfo = hostControllerInfo;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        populateHostControllerInfo(hostControllerInfo, context, model);
        final ServiceTarget serviceTarget = context.getServiceTarget();

        final boolean onDemand = context.isBooting();
        NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostControllerInfo.getLocalHostName(), context.getServiceRegistry(false), onDemand);

        OptionMap options = createConnectorOptions(context, model);
        installNativeManagementServices(serviceTarget, hostControllerInfo, options);
    }

    static void populateHostControllerInfo(LocalHostControllerInfoImpl hostControllerInfo, OperationContext context, ModelNode model) throws OperationFailedException {
        hostControllerInfo.setNativeManagementInterface(NativeManagementResourceDefinition.INTERFACE.resolveModelAttribute(context, model).asString());
        final ModelNode portNode = NativeManagementResourceDefinition.NATIVE_PORT.resolveModelAttribute(context, model);
        hostControllerInfo.setNativeManagementPort(portNode.isDefined() ? portNode.asInt() : -1);
        final ModelNode realmNode = NativeManagementResourceDefinition.SECURITY_REALM.resolveModelAttribute(context, model);
        hostControllerInfo.setNativeManagementSecurityRealm(realmNode.isDefined() ? realmNode.asString() : null);
    }

    public static void installNativeManagementServices(final ServiceTarget serviceTarget, final LocalHostControllerInfo hostControllerInfo, final OptionMap options) {

        String nativeSecurityRealm = hostControllerInfo.getNativeManagementSecurityRealm();

        final ServiceName nativeManagementInterfaceBinding =
                NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(hostControllerInfo.getNativeManagementInterface());

        ManagementRemotingServices.installDomainConnectorServices(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                nativeManagementInterfaceBinding, hostControllerInfo.getNativeManagementPort(), nativeSecurityRealm, options);
    }

    private static OptionMap createConnectorOptions(final OperationContext context, final ModelNode model) throws OperationFailedException {
        Builder builder = OptionMap.builder();

        builder.addAll(NativeManagementServices.CONNECTION_OPTIONS);
        builder.set(RemotingOptions.SASL_PROTOCOL, NativeManagementResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        ModelNode serverName = NativeManagementResourceDefinition.SERVER_NAME.resolveModelAttribute(context, model);
        if (serverName.isDefined()) {
            builder.set(RemotingOptions.SERVER_NAME, serverName.asString());
        }

        return builder.getMap();
    }

}
