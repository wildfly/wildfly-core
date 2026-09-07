/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE_LOADING;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedList;
import java.util.List;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.client.helpers.JBossModulesNameUtil;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.LocalModuleFinder;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleNotFoundException;
import org.jboss.modules.management.ModuleLoaderMXBean;
import org.jboss.modules.management.ResourceLoaderInfo;

/**
 * Definition of the core-service=module-loading resource.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ModuleLoadingResourceDefinition extends SimpleResourceDefinition {

    private static final AttributeDefinition MODULE_NAME = new SimpleAttributeDefinitionBuilder(MODULE, ModelType.STRING).build();

    public static final ModuleLoadingResourceDefinition INSTANCE = new ModuleLoadingResourceDefinition();

    private ModuleLoadingResourceDefinition() {
        super(new Parameters(PathElement.pathElement(CORE_SERVICE, MODULE_LOADING),
                ServerDescriptions.getResourceDescriptionResolver("core", MODULE_LOADING))
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.MODULE_LOADING)
                .setFeature(false) // TODO this is not needed when setRuntime is set but just in case I'm wrong and removing it will break urgent Galleon work I'm leaving it
                .setRuntime()
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AttributeDefinition ad = SimpleListAttributeDefinition.Builder.of("module-roots",
                new SimpleAttributeDefinitionBuilder("module-root", ModelType.STRING).build())
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .setDeprecated(ModelVersion.create(1, 4, 0))
                .build();
        resourceRegistration.registerReadOnlyAttribute(ad, new ListModuleRootsHandler());
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {

        super.registerOperations(resourceRegistration);

        final OperationDefinition definition = new SimpleOperationDefinitionBuilder("list-resource-loader-paths", getResourceDescriptionResolver())
                .addParameter(MODULE_NAME)
                .setRuntimeOnly()
                .setReplyType(ModelType.LIST)
                .setReplyValueType(ModelType.STRING)
                .setDeprecated(ModelVersion.create(1, 4, 0))
                .setReadOnly()
                .build();

         resourceRegistration.registerOperationHandler(definition, new ModuleLocationHandler());
         resourceRegistration.registerOperationHandler(ModuleInfoHandler.DEFINITION, ModuleInfoHandler.INSTANCE);
    }

    /** Read attribute handler for "module-roots" */
    private static class ListModuleRootsHandler extends AbstractRuntimeOnlyHandler {

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected boolean resourceMustExist(OperationContext context, ModelNode operation) {
            return false;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {

            final ModelNode list = context.getResult().setEmptyList();

            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                    @Override
                    public Void run() throws Exception {
                        storeRepoRoots(list);
                        return null;
                    }
                });
            } catch (PrivilegedActionException e) {

                if ( e.getCause() instanceof OperationFailedException
                        || e.getCause() instanceof ModuleNotFoundException){
                    throw new OperationFailedException(e.getCause());
                }

                throw new RuntimeException(e.getCause());
            }
        }

        private static void storeRepoRoots(final ModelNode list) throws NoSuchFieldException, IllegalAccessException {
            // TODO get a formal API from jboss-modules to replace this reflection
            ModuleLoader loader = Module.getBootModuleLoader();
            if (loader instanceof LocalModuleLoader) {
                LocalModuleLoader lml = (LocalModuleLoader) loader;
                Field findersField = ModuleLoader.class.getDeclaredField("finders");
                Field repoRootsField = null;
                findersField.setAccessible(true);
                try {
                    Object[] finders = (Object[]) findersField.get(lml);
                    if (finders.length > 0 && finders[0] instanceof LocalModuleFinder) {
                        LocalModuleFinder lmf = (LocalModuleFinder) finders[0];
                        repoRootsField = LocalModuleFinder.class.getDeclaredField("repoRoots") ;
                        repoRootsField.setAccessible(true);
                        File[] repoRoots = (File[]) repoRootsField.get(lmf);
                        for (File file : repoRoots) {
                            list.add(file.getAbsolutePath());
                        }
                    }
                } finally {
                    findersField.setAccessible(false);
                    if (repoRootsField != null) {
                        repoRootsField.setAccessible(false);
                    }
                }
            }

        }
    }

    /** Handler for the "list-resource-loader-paths" operation */
    private static final class ModuleLocationHandler implements OperationStepHandler {

        /** {@inheritDoc} */
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                    final String moduleName = MODULE_NAME.resolveModelAttribute(context, operation).asString();

                    try {
                        List<String> paths = AccessController.doPrivileged(new PrivilegedExceptionAction<List<String>>() {
                            @Override
                            public List<String> run() throws Exception {
                                return findResourcePaths(moduleName);
                            }
                        });

                        ModelNode list = context.getResult().setEmptyList();
                        for (String path : paths) {
                            list.add(path);
                        }

                    } catch (PrivilegedActionException e) {

                        if ( e.getCause() instanceof OperationFailedException
                                || e.getCause() instanceof ModuleNotFoundException ){
                            throw new OperationFailedException(e.getCause());
                        }

                        throw new RuntimeException(e.getCause());
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }


    private static List<String> findResourcePaths(String moduleName) throws ModuleLoadException, ReflectiveOperationException, IOException, URISyntaxException {
        ModuleLoader moduleLoader = Module.getCallerModuleLoader();
        ModuleLoaderMXBean loader = ModuleInfoHandler.INSTANCE.getMxBean(moduleLoader);
        moduleLoader.loadModule(JBossModulesNameUtil.parseCanonicalModuleIdentifier(moduleName));

        List<String> result = new LinkedList<>();
        for (ResourceLoaderInfo rl : loader.getResourceLoaders(moduleName)){
            final String location = rl.getLocation();
            if (location != null && !location.equals("null")) {
                URL url = new URL(location);

                switch (url.getProtocol()){

                    case "jar": {
                        JarURLConnection jarConnection = (JarURLConnection)url.openConnection();
                        result.add(jarConnection.getJarFile().getName());

                        break;
                    }
                    default: {
                        result.add(new File(url.getFile() ).toString());
                    }
                }
            }
        }

        return result;
    }
}
