/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.PROVIDERS_API_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.PROVIDERS_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAMES;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.LOADED_PROVIDERS;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.populateProviders;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionConsumer;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.extension.elytron.ElytronCommonTrivialResourceDefinition.Builder;


/**
 * Resource definition(s) for resources satisfying the Provider[] capability.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
abstract class ElytronCommonProviderDefinitions {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PATH, FileAttributeDefinitions.PATH)
            .setAttributeGroup(ElytronCommonConstants.CONFIGURATION)
            .setAlternatives(ElytronCommonConstants.CONFIGURATION)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.RELATIVE_TO, FileAttributeDefinitions.RELATIVE_TO)
            .setAttributeGroup(ElytronCommonConstants.CONFIGURATION)
            .setRequires(ElytronCommonConstants.PATH)
            .setRestartAllServices()
            .build();

    static final SimpleMapAttributeDefinition CONFIGURATION = new SimpleMapAttributeDefinition.Builder(ElytronCommonConstants.CONFIGURATION, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.CONFIGURATION)
            .setAllowExpression(true)
            .setAlternatives(ElytronCommonConstants.PATH, ElytronCommonConstants.ARGUMENT)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition ARGUMENT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ARGUMENT, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.CONFIGURATION)
            .setRequires(ElytronCommonConstants.CLASS_NAMES)
            .setAlternatives(ElytronCommonConstants.PATH, ElytronCommonConstants.CONFIGURATION)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static ElytronCommonAggregateComponentDefinition<Provider[]> getAggregateProvidersDefinition(Class<?> extensionClass) {
        return ElytronCommonAggregateComponentDefinition.createCommonDefinition(extensionClass, Provider[].class, ElytronCommonConstants.AGGREGATE_PROVIDERS,
                ElytronCommonConstants.PROVIDERS, PROVIDERS_RUNTIME_CAPABILITY, PROVIDERS_API_CAPABILITY, ElytronCommonProviderDefinitions::aggregate, false);
    }

    static ResourceDefinition getProviderLoaderDefinition(final Class<?> extensionClass, boolean serverOrHostController) {
        AttributeDefinition[] attributes = new AttributeDefinition[] { MODULE, CLASS_NAMES, PATH, RELATIVE_TO, ARGUMENT, CONFIGURATION };

        AbstractAddStepHandler add = new ElytronCommonDoohickeyAddHandler<Provider[]>(extensionClass, PROVIDERS_RUNTIME_CAPABILITY, attributes, PROVIDERS_API_CAPABILITY) {

            @Override
            protected ElytronDoohickey<Provider[]> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<Provider[]>(resourceAddress) {

                    private volatile String module;
                    private volatile String[] classNames;
                    private volatile String path;
                    private volatile String relativeTo;
                    private volatile String argument;
                    private volatile Properties properties;

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        module = MODULE.resolveModelAttribute(context, model).asStringOrNull();
                        ModelNode classNamesNode = CLASS_NAMES.resolveModelAttribute(context, model);
                        if (classNamesNode.isDefined()) {
                            List<ModelNode> values = classNamesNode.asList();
                            classNames = new String[values.size()];
                            for (int i = 0; i < classNames.length; i++) {
                                classNames[i] = values.get(i).asString();
                            }
                        } else {
                            classNames = null;
                        }
                        path = PATH.resolveModelAttribute(context, model).asStringOrNull();
                        relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                        argument = ARGUMENT.resolveModelAttribute(context, model).asStringOrNull();
                        ModelNode configuration = CONFIGURATION.resolveModelAttribute(context, model);
                        if (configuration.isDefined()) {
                            properties = new Properties();
                            for (String s : configuration.keys()) {
                                properties.setProperty(s, configuration.require(s).asString());
                            }
                        } else {
                            properties = null;
                        }
                    }

                    @Override
                    protected ExceptionSupplier<Provider[], StartException> prepareServiceSupplier(OperationContext context,
                            CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
                        final Supplier<PathManagerService> pathManager;
                        if (properties == null && relativeTo != null) {
                            pathManager = serviceBuilder.requires(PathManagerService.SERVICE_NAME);
                            serviceBuilder.requires(pathName(relativeTo));
                        } else {
                            pathManager = null;
                        }

                        return new ExceptionSupplier<Provider[], StartException>() {

                            @Override
                            public Provider[] get() throws StartException {
                                File resolved = null;
                                if (properties == null && relativeTo != null) {
                                    PathResolver pathResolver = pathResolver();
                                    pathResolver.path(path);
                                    if (relativeTo != null) {
                                        pathResolver.relativeTo(relativeTo, pathManager.get());
                                    }
                                    resolved = pathResolver.resolve();
                                    pathResolver.clear();
                                }

                                return loadProviders(resolved);
                            }
                        };
                    }

                    @Override
                    protected Provider[] createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        File resolvedPath = null;
                        if (properties == null && path != null) {
                            resolvedPath = resolveRelativeToImmediately(path, relativeTo, foreignContext);
                        }

                        try {
                            return loadProviders(resolvedPath);
                        } catch (StartException e) {
                            throw new OperationFailedException(e);
                        }
                    }

                    private Provider[] loadProviders(final File resolvedPath) throws StartException {
                        final Supplier<InputStream> configSupplier;
                        if (properties != null) {
                            configSupplier = getConfigurationSupplier(properties);
                        } else if (resolvedPath != null) {
                            configSupplier = getConfigurationSupplier(resolvedPath);
                        } else {
                            configSupplier = null;
                        }

                        try {
                            Collection<ExceptionConsumer<InputStream, IOException>> deferred = new ArrayList<>();
                            ClassLoader classLoader = doPrivileged(
                                    (PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));
                            final List<Provider> loadedProviders;
                            if (classNames != null) {
                                loadedProviders = new ArrayList<>(classNames.length);
                                for (String className : classNames) {
                                    Class<? extends Provider> providerClazz = classLoader.loadClass(className)
                                            .asSubclass(Provider.class);
                                    if (argument != null) {
                                        Constructor<? extends Provider> constructor = doPrivileged(
                                                (PrivilegedExceptionAction<Constructor<? extends Provider>>) () -> providerClazz
                                                        .getConstructor(String.class));
                                        loadedProviders.add(constructor.newInstance(new Object[] { argument }));
                                    } else if (configSupplier != null) {
                                        // Try and find a constructor that takes an input stream and use it - or use default
                                        // constructor and enable deferred initialisation.
                                        try {
                                            Constructor<? extends Provider> constructor = doPrivileged(
                                                    (PrivilegedExceptionAction<Constructor<? extends Provider>>) () -> providerClazz
                                                            .getConstructor(InputStream.class));
                                            loadedProviders.add(constructor.newInstance(new Object[]{configSupplier.get()}));
                                        } catch (NoSuchMethodException constructorDoesNotExist) {
                                            File tempFile = null;
                                            try {
                                                Method configureMethod = doPrivileged((PrivilegedExceptionAction<Method>) () -> providerClazz.getMethod("configure", String.class));
                                                Provider provider = providerClazz.newInstance();
                                                tempFile = inputStreamToFile(configSupplier);
                                                loadedProviders.add((Provider) configureMethod.invoke(provider, tempFile.getAbsolutePath()));
                                            } catch (NoSuchMethodException configureMethodDoesNotExist) {
                                                Provider provider = providerClazz.newInstance();
                                                loadedProviders.add(provider);
                                                deferred.add(provider::load);
                                            } finally {
                                                if (tempFile != null) {
                                                    tempFile.delete();
                                                }
                                            }
                                        }
                                    } else {
                                        loadedProviders.add(providerClazz.newInstance());
                                    }
                                }
                            } else {
                                loadedProviders = new ArrayList<>();
                                try {
                                    Iterable<Provider> providers = Module.findServices(Provider.class, new Predicate<Class<?>>() {
                                        @Override
                                        public boolean test(final Class<?> providerClass) {
                                            // We don't want to pick up JDK services resolved via JPMS definitions.
                                            return providerClass.getClassLoader() instanceof ModuleClassLoader;
                                        }
                                    }, classLoader);
                                    Iterator<Provider> iterator = providers.iterator();
                                    while (iterator.hasNext()) {
                                        final Provider p = iterator.next();
                                        if (configSupplier != null) {
                                            deferred.add(p::load);
                                        }
                                        loadedProviders.add(p);
                                    }
                                } catch (Exception e) {
                                    ROOT_LOGGER.tracef(e, "Failed to initialize a security provider");
                                }
                            }

                            for (ExceptionConsumer<InputStream, IOException> current : deferred) {
                                // We know from above the deferred Collection is only populated if we do have a configSupplier.
                                current.accept(configSupplier.get());
                            }

                            Provider[] providers = loadedProviders.toArray(new Provider[loadedProviders.size()]);
                            if (ROOT_LOGGER.isTraceEnabled()) {
                                ROOT_LOGGER.tracef("Loaded providers %s", Arrays.toString(providers));
                            }
                            return providers;
                        } catch (PrivilegedActionException e) {
                            throw new StartException(e.getCause());
                        } catch (Exception e) {
                            throw new StartException(e);
                        }
                    }
                };
            }

            @Override
            protected boolean dependOnProviderRegistration() {
                return false;
            }
        };

        Builder builder = ElytronCommonTrivialResourceDefinition.getCommonBuilder(extensionClass)
                .setPathKey(ElytronCommonConstants.PROVIDER_LOADER)
                .setAddHandler(add)
                .setAttributes(attributes)
                .setRuntimeCapabilities(PROVIDERS_RUNTIME_CAPABILITY);

        if (serverOrHostController) {
            builder.addReadOnlyAttribute(LOADED_PROVIDERS, new LoadedProvidersAttributeHandler());
        }

        return builder.build();
    }

    private static Provider[] aggregate(Provider[] ... providers) {
        int length = 0;
        for (Provider[] current : providers) {
            length += current.length;
        }

        Provider[] combined = new Provider[length];
        int startPos = 0;
        for (Provider[] current : providers) {
            System.arraycopy(current, 0, combined, startPos, current.length);
            startPos += current.length;
        }

        return combined;
    }

    private static Supplier<InputStream> getConfigurationSupplier(final File location) throws StartException {
        try {
            byte[] configuration = Files.readAllBytes(location.toPath());

            return () -> new ByteArrayInputStream(configuration);
        } catch (IOException e) {
            throw ROOT_LOGGER.unableToStartService(e);
        }
    }

    private static Supplier<InputStream> getConfigurationSupplier(final Properties properties) throws StartException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            properties.store(baos, "");
            final byte[] configuration = baos.toByteArray();

            return () -> new ByteArrayInputStream(configuration);
        } catch (IOException e) {
            throw ROOT_LOGGER.unableToStartService(e);
        }
    }

    private static File inputStreamToFile(Supplier<InputStream> configSupplier) throws IOException {
        File tempFile = new File(Files.createTempFile("temp", "cfg").toString());
        InputStream inputStream = configSupplier.get();
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        OutputStream outStream = new FileOutputStream(tempFile);
        outStream.write(buffer);
        outStream.close();
        return tempFile;
    }

    private static class LoadedProvidersAttributeHandler extends ElytronRuntimeOnlyHandler {

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            final ExceptionFunction<OperationContext, Provider[], OperationFailedException> providerApi = context
                    .getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, context.getCurrentAddressValue(), ExceptionFunction.class);

            populateProviders(context.getResult(), providerApi.apply(context));
        }

    }

}
