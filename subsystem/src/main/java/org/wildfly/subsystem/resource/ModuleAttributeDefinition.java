/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.function.Function;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ModuleNameValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Attribute definition that resolves to a {@link ServiceDependency} on a module.
 * @author Paul Ferraro
 */
public class ModuleAttributeDefinition extends SimpleAttributeDefinition implements ResourceModelResolver<ServiceDependency<Module>> {

    ModuleAttributeDefinition(Builder builder) {
        super(builder);
    }

    @Override
    public ServiceDependency<Module> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        String moduleId = this.resolveModelAttribute(context, model).asStringOrNull();
        return (moduleId != null) ? ServiceDependency.<ModuleLoader>on(Services.JBOSS_SERVICE_MODULE_LOADER).map(new Function<>() {
            @Override
            public Module apply(ModuleLoader loader) {
                try {
                    return loader.loadModule(moduleId);
                } catch (ModuleLoadException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }) : ServiceDependency.of(null);
    }

    public static class Builder extends AbstractAttributeDefinitionBuilder<Builder, ModuleAttributeDefinition> {

        public Builder() {
            this(ModelDescriptionConstants.MODULE);
        }

        public Builder(String attributeName) {
            super(attributeName, ModelType.STRING);
            this.setAllowExpression(true);
            this.setFlags(Flag.RESTART_RESOURCE_SERVICES);
            this.setCorrector(ModuleIdentifierUtil.MODULE_NAME_CORRECTOR);
            this.setValidator(ModuleNameValidator.INSTANCE);
        }

        public Builder(String attributeName, ModuleAttributeDefinition basis) {
            super(attributeName, basis);
        }

        public Builder setDefaultValue(Module defaultModule) {
            this.setRequired(false);
            return this.setDefaultValue((defaultModule != null) ? new ModelNode(defaultModule.getName()) : null);
        }

        @Override
        public ModuleAttributeDefinition build() {
            return new ModuleAttributeDefinition(this);
        }
    }
}
