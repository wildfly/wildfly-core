/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import java.util.function.Supplier;

import javax.management.MBeanServer;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JMXSubsystemRootResource extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, JMXExtension.SUBSYSTEM_NAME);

    private static final SimpleAttributeDefinition SHOW_MODEL_ALIAS = SimpleAttributeDefinitionBuilder.create(CommonAttributes.SHOW_MODEL, ModelType.BOOLEAN, true)
            .addFlag(AttributeAccess.Flag.ALIAS)
            .setDeprecated(ModelVersion.create(7,0,0))
            .build();

    public static final SimpleAttributeDefinition CORE_MBEAN_SENSITIVITY = new SimpleAttributeDefinitionBuilder(CommonAttributes.NON_CORE_MBEAN_SENSITIVITY, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.ACCESS_CONTROL)
            .setXmlName(CommonAttributes.NON_CORE_MBEANS)
            .setDefaultValue(ModelNode.FALSE).build();

    static final String JMX_CAPABILITY_NAME = "org.wildfly.management.jmx";
    static final RuntimeCapability<Void> JMX_CAPABILITY =
            RuntimeCapability.Builder.of(JMX_CAPABILITY_NAME, MBeanServer.class).build();

    private final ManagedAuditLogger auditLogger;
    private final JmxAuthorizer authorizer;
    private final Supplier<SecurityIdentity> securityIdentitySupplier;
    private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

    private JMXSubsystemRootResource(ManagedAuditLogger auditLogger, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
        super(new Parameters(PATH_ELEMENT, JMXExtension.getResourceDescriptionResolver(JMXExtension.SUBSYSTEM_NAME))
                .setAddHandler(new JMXSubsystemAdd(auditLogger, authorizer, securityIdentitySupplier, hostInfoAccessor))
                .setRemoveHandler(new JMXSubsystemRemove(auditLogger, authorizer, securityIdentitySupplier, hostInfoAccessor))
                .addCapabilities(JMX_CAPABILITY)
                .setAccessConstraints(JMXExtension.JMX_SENSITIVITY_DEF));
        this.auditLogger = auditLogger;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.hostInfoAccessor = hostInfoAccessor;
    }

    public static JMXSubsystemRootResource create(ManagedAuditLogger auditLogger, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
        return new JMXSubsystemRootResource(auditLogger, authorizer, securityIdentitySupplier, hostInfoAccessor);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(SHOW_MODEL_ALIAS, ShowModelAliasReadHandler.INSTANCE, ShowModelAliasWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(CORE_MBEAN_SENSITIVITY, null, CoreMBeansSensitivityWriteHandler.INSTANCE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new ExposeModelResourceResolved(auditLogger, authorizer, securityIdentitySupplier, hostInfoAccessor));
        resourceRegistration.registerSubModel(new ExposeModelResourceExpression(auditLogger, authorizer, securityIdentitySupplier, hostInfoAccessor));
        resourceRegistration.registerSubModel(RemotingConnectorResource.INSTANCE);
        resourceRegistration.registerSubModel(new JmxAuditLoggerResourceDefinition(auditLogger));
    }

    private static class ShowModelAliasWriteHandler implements OperationStepHandler {
        static final ShowModelAliasWriteHandler INSTANCE = new ShowModelAliasWriteHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final boolean value = operation.get(ModelDescriptionConstants.VALUE).asBoolean(false);
            boolean hasResource = context.readResource(PathAddress.EMPTY_ADDRESS).hasChild(ExposeModelResourceResolved.PATH_ELEMENT);
            if (value) {
                if (!hasResource) {
                    OperationStepHandler handler = context.getResourceRegistration().getOperationEntry(PathAddress.pathAddress(ExposeModelResourceResolved.PATH_ELEMENT), ADD).getOperationHandler();
                    ModelNode addOp = new ModelNode();
                    addOp.get(OP).set(ADD);
                    addOp.get(OP_ADDR).set(PathAddress.pathAddress(operation.get(OP_ADDR)).append(ExposeModelResourceResolved.PATH_ELEMENT).toModelNode());
                    context.addStep(addOp, handler, Stage.MODEL, true);
                }
            } else {
                if (hasResource) {
                    OperationStepHandler handler = context.getResourceRegistration().getOperationEntry(PathAddress.pathAddress(ExposeModelResourceResolved.PATH_ELEMENT), REMOVE).getOperationHandler();
                    ModelNode addOp = new ModelNode();
                    addOp.get(OP).set(REMOVE);
                    addOp.get(OP_ADDR).set(PathAddress.pathAddress(operation.get(OP_ADDR)).append(ExposeModelResourceResolved.PATH_ELEMENT).toModelNode());
                    context.addStep(addOp, handler, Stage.MODEL, true);
                }
            }
        }
    }

    private static class ShowModelAliasReadHandler implements OperationStepHandler {
        static final ShowModelAliasReadHandler INSTANCE = new ShowModelAliasReadHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
            context.getResult().set(resource.hasChild(PathElement.pathElement(CommonAttributes.EXPOSE_MODEL, CommonAttributes.RESOLVED)));
        }

    }

    private static class CoreMBeansSensitivityWriteHandler extends AbstractWriteAttributeHandler<Boolean> {
        static final CoreMBeansSensitivityWriteHandler INSTANCE = new CoreMBeansSensitivityWriteHandler();

        private CoreMBeansSensitivityWriteHandler() {
            super(CORE_MBEAN_SENSITIVITY);

        }
        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                ModelNode resolvedValue, ModelNode currentValue,
                org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<Boolean> handbackHolder)
                throws OperationFailedException {
            setPluggableMBeanServerCoreSensitivity(context, resolvedValue.asBoolean());
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                ModelNode valueToRestore, ModelNode valueToRevert, Boolean handback) throws OperationFailedException {
            setPluggableMBeanServerCoreSensitivity(context, valueToRestore.asBoolean());
        }

        private void setPluggableMBeanServerCoreSensitivity(OperationContext context, boolean sensitivity) {
            ServiceController<?> controller = context.getServiceRegistry(false).getRequiredService(MBeanServerService.SERVICE_NAME);
            PluggableMBeanServerImpl server = (PluggableMBeanServerImpl)controller.getValue();
            server.setNonFacadeMBeansSensitive(sensitivity);
        }

    }

}
