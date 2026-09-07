/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.controller.resources;

import java.lang.reflect.Field;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.client.helpers.JBossModulesNameUtil;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.management.DependencyInfo;
import org.jboss.modules.management.ModuleInfo;
import org.jboss.modules.management.ModuleLoaderMXBean;
import org.jboss.modules.management.ResourceLoaderInfo;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class ModuleInfoHandler implements OperationStepHandler {


    static final AttributeDefinition MODULE_NAME = SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING)
            .setRequired(true)
            .build();
    static final AttributeDefinition MODULE_SLOT = SimpleAttributeDefinitionBuilder.create("slot", ModelType.STRING)
            .setDefaultValue(new ModelNode("main"))
            .setRequired(false)
            .build();

    //reply parameters
    static final AttributeDefinition MAIN_CLASS = SimpleAttributeDefinitionBuilder.create("main-class", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    static final AttributeDefinition FALLBACK_LOADER = SimpleAttributeDefinitionBuilder.create("fallback-loader", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    static final ObjectTypeAttributeDefinition DEPENDENCY = ObjectTypeAttributeDefinition.Builder.of("dependency",
            SimpleAttributeDefinitionBuilder.create("dependency-name", ModelType.STRING).build(),
            SimpleAttributeDefinitionBuilder.create("module-name", ModelType.STRING).build(),
            SimpleAttributeDefinitionBuilder.create("export-filter", ModelType.STRING).build(),
            SimpleAttributeDefinitionBuilder.create("import-filter", ModelType.STRING).build(),
            SimpleAttributeDefinitionBuilder.create("optional", ModelType.BOOLEAN).build(),
            SimpleAttributeDefinitionBuilder.create("local-loader-class", ModelType.STRING, true).build(),
            new StringListAttributeDefinition.Builder("local-loader-paths").build()
    )
            .setStorageRuntime()
            .build();

    static final AttributeDefinition DEPENDENCIES = ObjectListAttributeDefinition.Builder.of("dependencies", DEPENDENCY)
            .setStorageRuntime()
            .build();

    static final ObjectTypeAttributeDefinition RESOURCE_LOADER = ObjectTypeAttributeDefinition.Builder.of("resource-loader",
            SimpleAttributeDefinitionBuilder.create("type", ModelType.STRING).build(),
            new StringListAttributeDefinition.Builder("paths").build()

    )
            .setStorageRuntime()
            .build();


    static final AttributeDefinition RESOURCE_LOADERS = ObjectListAttributeDefinition.Builder.of("resource-loaders", RESOURCE_LOADER)
            .setStorageRuntime()
            .build();


    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("module-info", ModuleLoadingResourceDefinition.INSTANCE.getResourceDescriptionResolver())
            .setParameters(MODULE_NAME, MODULE_SLOT)
            .setRuntimeOnly()
            .setReadOnly()
            .setReplyType(ModelType.OBJECT)
            .setReplyParameters(MODULE_NAME, MAIN_CLASS, FALLBACK_LOADER, DEPENDENCIES, RESOURCE_LOADERS)
            .build();

    public static ModuleInfoHandler INSTANCE = new ModuleInfoHandler();

    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attr : DEFINITION.getParameters()) {
            attr.validateAndSet(operation, model);
        }
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode model = new ModelNode();
        populateModel(operation, model);
        String moduleName = MODULE_NAME.resolveModelAttribute(context, model).asString();
        String slot = MODULE_SLOT.resolveModelAttribute(context, model).asString();
        String id = JBossModulesNameUtil.canonicalModuleIdentifier(moduleName, slot);
        ModuleLoader loader = Module.getBootModuleLoader();
        try {
            ModuleLoaderMXBean mxBean = getMxBean(loader);
            ModuleInfo moduleInfo = mxBean.getModuleDescription(id);
            context.getResult().set(populateModuleInfo(moduleInfo));
        } catch (Exception e) {
            throw ServerLogger.ROOT_LOGGER.couldNotGetModuleInfo(id, e);
        }
    }


    protected ModuleLoaderMXBean getMxBean(ModuleLoader loader) throws ReflectiveOperationException {
        Field mxBeanField = ModuleLoader.class.getDeclaredField("mxBean");
        mxBeanField.setAccessible(true);
        return (ModuleLoaderMXBean) mxBeanField.get(loader);
    }

    /*
    Here is the information to expose (use ModuleLoaderMXBean as a source of information):

        Loaded module names
        For each module:
            Module name
            Main class name (if any)
            Class loader name string
            Fallback loader name string
            Dependency information:
                Dependency type
                Export filter string
                Import filter string
                Dependency module name
                Is-optional flag
                Local loader type class name
                Local loader paths
            Resource loader information for each module:
                Resource loader type string
                Resource loader paths

    Note that there are multiple module loaders. For our purposes there should be (at least) two categories displayed:

        The static module loader
        The deployment module loader


     */
    private ModelNode populateModuleInfo(ModuleInfo module) throws Exception {
        ModelNode result = new ModelNode();
        result.get("name").set(module.getName());

        ModelNode value;
        value = result.get("main-class");
        if (module.getMainClass() != null) {
            value.set(module.getMainClass());
        }
        value = result.get("fallback-loader");
        if (module.getFallbackLoader() != null) {
            value.set(module.getFallbackLoader());
        }

        ModelNode dependencies = result.get("dependencies").setEmptyList();
        for (DependencyInfo dependencySpec : module.getDependencies()) {
            if (dependencySpec.getModuleName() == null) {
                continue; //todo check why it returns empty dependancy
            }
            ModelNode dependency = dependencies.add();
            dependency.get("dependency-name").set(dependencySpec.getDependencyType());
            dependency.get("module-name").set(dependencySpec.getModuleName());
            dependency.get("export-filter").set(dependencySpec.getExportFilter());
            dependency.get("import-filter").set(dependencySpec.getImportFilter());
            dependency.get("optional").set(dependencySpec.isOptional());
            value = result.get("local-loader-class");
            if (dependencySpec.getLocalLoader() != null) {
                value.set(dependencySpec.getLocalLoader());
            }

            if (dependencySpec.getLocalLoaderPaths() != null) {
                ModelNode paths = dependency.get("local-loader-paths");
                for (String path : dependencySpec.getLocalLoaderPaths()) {
                    paths.add(path);
                }
            }
        }
        ModelNode resourceLoaders = result.get("resource-loaders").setEmptyList();
        for (ResourceLoaderInfo loaderInfo : module.getResourceLoaders()) {
            ModelNode loader = resourceLoaders.add();
            loader.get("type").set(loaderInfo.getType());
            ModelNode paths = loader.get("paths");
            for (String path : loaderInfo.getPaths()) {
                paths.add(path);
            }

        }
        return result;
    }
}
