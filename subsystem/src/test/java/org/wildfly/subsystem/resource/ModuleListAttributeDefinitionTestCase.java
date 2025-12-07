/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RequirementServiceBuilder;
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
public class ModuleListAttributeDefinitionTestCase {

    @Test
    public void required() throws OperationFailedException, ModuleLoadException {
        String name = "modules";
        ModuleListAttributeDefinition attribute = new ModuleListAttributeDefinition.Builder().build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
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
        String name = "foo";
        ModuleListAttributeDefinition attribute = new ModuleListAttributeDefinition.Builder(name).setRequired(false).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
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

        ServiceDependency<List<Module>> resolved = attribute.resolve(context, model);

        RequirementServiceBuilder<?> builder = mock(RequirementServiceBuilder.class);

        resolved.accept(builder);
        verifyNoInteractions(builder);

        assertThat(resolved.get()).isEmpty();

        verify(attribute);
    }

    @Test
    public void defaultValue() throws OperationFailedException, ModuleLoadException {
        String name = "foo";
        String defaultModuleName = "foo";
        Module defaultModule = mock(Module.class);
        doReturn(defaultModuleName).when(defaultModule).getName();

        ModuleListAttributeDefinition attribute = new ModuleListAttributeDefinition.Builder(name).setDefaultValue(defaultModule).build();
        assertThat(attribute.getName()).isEqualTo(name);
        assertThat(attribute.getXmlName()).isEqualTo(name);
        assertThat(attribute.isAllowExpression()).isTrue();
        assertThat(attribute.isRequired()).isFalse();
        assertThat(attribute.getDefaultValue()).isEqualTo(new ModelNode().add(defaultModuleName));

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        // Validation should succeed, parameter is optional
        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));

        ServiceDependency<List<Module>> resolved = attribute.resolve(context, model);

        RequirementServiceBuilder<?> builder = mock(RequirementServiceBuilder.class);
        ModuleLoader loader = mock(ModuleLoader.class);

        doReturn(Functions.constantSupplier(loader)).when(builder).requires(Services.JBOSS_SERVICE_MODULE_LOADER);
        doReturn(defaultModule).when(loader).loadModule(defaultModuleName);

        resolved.accept(builder);

        assertThat(resolved.get()).containsExactly(defaultModule);

        verify(attribute);
    }

    private void verify(ModuleListAttributeDefinition attribute) throws OperationFailedException, ModuleLoadException {

        OperationContext context = mock(OperationContext.class);
        doAnswer(returnsFirstArg()).when(context).resolveExpressions(any());
        ModelNode operation = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);
        ModelNode model = new ModelNode();

        List<String> moduleNames = List.of("java.base", "java.sql");
        ModelNode list = new ModelNode();
        moduleNames.forEach(list::add);
        operation.get(attribute.getName()).set(list);

        assertThatNoException().isThrownBy(() -> attribute.validateOperation(operation));
        assertThatNoException().isThrownBy(() -> attribute.validateAndSet(operation, model));

        ServiceDependency<List<Module>> resolved = attribute.resolve(context, model);

        RequirementServiceBuilder<?> builder = mock(RequirementServiceBuilder.class);
        ModuleLoader loader = mock(ModuleLoader.class);
        Module module1 = mock(Module.class);
        Module module2 = mock(Module.class);

        doReturn(Functions.constantSupplier(loader)).when(builder).requires(Services.JBOSS_SERVICE_MODULE_LOADER);
        doReturn(module1).when(loader).loadModule(moduleNames.get(0));
        doReturn(module2).when(loader).loadModule(moduleNames.get(1));

        resolved.accept(builder);

        assertThat(resolved.get()).containsExactly(module1, module2);
    }
}
