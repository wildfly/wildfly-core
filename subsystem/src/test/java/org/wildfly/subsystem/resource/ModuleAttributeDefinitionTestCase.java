/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.Mockito.*;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.junit.Test;
import org.wildfly.subsystem.service.ServiceDependency;

import io.smallrye.common.function.Functions;

/**
 * Unit test for {@link ModuleAttributeDefinition}.
 * @author Paul Ferraro
 */
public class ModuleAttributeDefinitionTestCase {

    @Test
    public void required() throws OperationFailedException, ModuleLoadException {
        ModuleAttributeDefinition attribute = new ModuleAttributeDefinition.Builder().build();
        assertThat(attribute.getName()).isEqualTo(ModelDescriptionConstants.MODULE);
        assertThat(attribute.getXmlName()).isEqualTo(ModelDescriptionConstants.MODULE);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isTrue();
        assertThat(attribute.getDefaultValue()).isNull();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        ThrowingCallable operationValidation = () -> attribute.validateOperation(operation);
        ThrowingCallable modelValidation = () -> attribute.validateAndSet(operation, model);

        // Validation should fail, parameter is required
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(operationValidation);
        assertThatExceptionOfType(OperationFailedException.class).isThrownBy(modelValidation);

        verify(attribute);
    }

    @Test
    public void optional() throws OperationFailedException, ModuleLoadException {
        ModuleAttributeDefinition attribute = new ModuleAttributeDefinition.Builder().setRequired(false).build();
        assertThat(attribute.getName()).isEqualTo(ModelDescriptionConstants.MODULE);
        assertThat(attribute.getXmlName()).isEqualTo(ModelDescriptionConstants.MODULE);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();
        assertThat(attribute.getDefaultValue()).isNull();

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        ThrowingCallable operationValidation = () -> attribute.validateOperation(operation);
        ThrowingCallable modelValidation = () -> attribute.validateAndSet(operation, model);

        // Validation should succeed, parameter is optional
        assertThatNoException().isThrownBy(operationValidation);
        assertThatNoException().isThrownBy(modelValidation);

        ServiceDependency<Module> resolved = attribute.resolve(context, model);

        RequirementServiceBuilder<?> builder = mock(RequirementServiceBuilder.class);

        resolved.accept(builder);
        verifyNoInteractions(builder);

        assertThat(resolved.get()).isNull();

        verify(attribute);
    }

    @Test
    public void defaultValue() throws OperationFailedException, ModuleLoadException {
        String defaultModuleName = "foo";
        Module defaultModule = mock(Module.class);
        doReturn(defaultModuleName).when(defaultModule).getName();

        ModuleAttributeDefinition attribute = new ModuleAttributeDefinition.Builder().setDefaultValue(defaultModule).build();
        assertThat(attribute.getName()).isEqualTo(ModelDescriptionConstants.MODULE);
        assertThat(attribute.getXmlName()).isEqualTo(ModelDescriptionConstants.MODULE);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();
        assertThat(attribute.getDefaultValue()).isEqualTo(new ModelNode(defaultModuleName));

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Validation should succeed, parameter is optional
        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));

        ServiceDependency<Module> resolved = attribute.resolve(context, model);

        RequirementServiceBuilder<?> builder = mock(RequirementServiceBuilder.class);
        ModuleLoader loader = mock(ModuleLoader.class);

        doReturn(Functions.constantSupplier(loader)).when(builder).requires(Services.JBOSS_SERVICE_MODULE_LOADER);
        doReturn(defaultModule).when(loader).loadModule(defaultModuleName);

        resolved.accept(builder);

        assertThat(resolved.get()).isSameAs(defaultModule);

        verify(attribute);
    }

    private void verify(ModuleAttributeDefinition attribute) throws OperationFailedException, ModuleLoadException {

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        String moduleName = "java.base";
        operation.get(attribute.getName()).set(new ModelNode(moduleName));

        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));

        ServiceDependency<Module> resolved = attribute.resolve(context, model);

        RequirementServiceBuilder<?> builder = mock(RequirementServiceBuilder.class);
        ModuleLoader loader = mock(ModuleLoader.class);
        Module module = mock(Module.class);

        doReturn(Functions.constantSupplier(loader)).when(builder).requires(Services.JBOSS_SERVICE_MODULE_LOADER);
        doReturn(module).when(loader).loadModule(moduleName);

        resolved.accept(builder);

        assertThat(resolved.get()).isSameAs(module);
    }
}
