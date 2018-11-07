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

import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAMES;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.LOADED_PROVIDERS;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.populateProviders;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
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
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback;
import org.jboss.as.controller.services.path.PathManager.Callback.Handle;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManager.PathEventContext;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionConsumer;
import org.wildfly.extension.elytron.TrivialResourceDefinition.Builder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;


/**
 * Resource definition(s) for resources satisfying the Provider[] capability.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderDefinitions {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
            .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
            .setAlternatives(ElytronDescriptionConstants.CONFIGURATION)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, FileAttributeDefinitions.RELATIVE_TO)
            .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
            .setRequires(ElytronDescriptionConstants.PATH)
            .setRestartAllServices()
            .build();

    static final SimpleMapAttributeDefinition CONFIGURATION = new SimpleMapAttributeDefinition.Builder(ElytronDescriptionConstants.CONFIGURATION, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
            .setAllowExpression(true)
            .setAlternatives(ElytronDescriptionConstants.PATH, ElytronDescriptionConstants.ARGUMENT)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition ARGUMENT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ARGUMENT, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
            .setRequires(ElytronDescriptionConstants.CLASS_NAMES)
            .setAlternatives(ElytronDescriptionConstants.PATH, ElytronDescriptionConstants.CONFIGURATION)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    private static final AggregateComponentDefinition<Provider[]> AGGREGATE_PROVIDERS = AggregateComponentDefinition.create(Provider[].class,
            ElytronDescriptionConstants.AGGREGATE_PROVIDERS, ElytronDescriptionConstants.PROVIDERS, PROVIDERS_RUNTIME_CAPABILITY, ProviderDefinitions::aggregate, false);

    static final ListAttributeDefinition REFERENCES = AGGREGATE_PROVIDERS.getReferencesAttribute();

    static AggregateComponentDefinition<Provider[]> getAggregateProvidersDefinition() {
        return AGGREGATE_PROVIDERS;
    }

    static ResourceDefinition getProviderLoaderDefinition(boolean serverOrHostController) {
        AttributeDefinition[] attributes = new AttributeDefinition[] { MODULE, CLASS_NAMES, PATH, RELATIVE_TO, ARGUMENT, CONFIGURATION };
        AbstractAddStepHandler add = new TrivialAddHandler<Provider[]>(Provider[].class, attributes, PROVIDERS_RUNTIME_CAPABILITY) {

            @Override
            protected boolean dependOnProviderRegistration() {
                return false;
            }

            @Override
            protected ValueSupplier<Provider[]> getValueSupplier(ServiceBuilder<Provider[]> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String module = MODULE.resolveModelAttribute(context, model).asStringOrNull();

                final String[] classNames;
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

                final String path = PATH.resolveModelAttribute(context, model).asStringOrNull();
                final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                final String argument = ARGUMENT.resolveModelAttribute(context, model).asStringOrNull();

                final Properties properties;
                ModelNode configuration = CONFIGURATION.resolveModelAttribute(context, model);
                if (configuration.isDefined()) {
                    properties = new Properties();
                    for (String s : configuration.keys()) {
                        properties.setProperty(s, configuration.require(s).asString());
                    }
                } else {
                    properties = null;
                }

                final InjectedValue<PathManager> pathManager = new InjectedValue<>();

                if (relativeTo != null) {
                    serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManager);
                    serviceBuilder.requires(pathName(relativeTo));
                }

                return new ValueSupplier<Provider[]>() {

                    private volatile Handle handle = null;

                    @Override
                    public Provider[] get() throws StartException {
                        final Supplier<InputStream> configSupplier;
                        if (properties != null) {
                            configSupplier = getConfigurationSupplier(properties);
                        } else if (path != null) {
                            configSupplier = getConfigurationSupplier(resolveConfigLocation());
                        } else {
                            configSupplier = null;
                        }

                        try {
                            Collection<ExceptionConsumer<InputStream, IOException>> deferred = new ArrayList<>();
                            ClassLoader classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));
                            final List<Provider> loadedProviders;
                            if (classNames != null) {
                                loadedProviders = new ArrayList<>(classNames.length);
                                for (String className : classNames) {
                                    Class<? extends Provider> providerClazz = classLoader.loadClass(className).asSubclass(Provider.class);
                                    if (argument != null) {
                                        Constructor<? extends Provider> constructor = doPrivileged((PrivilegedExceptionAction<Constructor<? extends Provider>>) () ->  providerClazz.getConstructor(String.class));
                                        loadedProviders.add(constructor.newInstance(new Object[] { argument }));
                                    } else if (configSupplier != null) {
                                        // Try and find a constructor that takes an input stream and use it - or use default constructor and enable deferred initialisation.
                                        try {
                                            Constructor<? extends Provider> constructor = doPrivileged((PrivilegedExceptionAction<Constructor<? extends Provider>>) () ->  providerClazz.getConstructor(InputStream.class));
                                            loadedProviders.add(constructor.newInstance(new Object[] { configSupplier.get() }));
                                        } catch (NoSuchMethodException ignored) {
                                            Provider provider = providerClazz.newInstance();
                                            loadedProviders.add(provider);
                                            deferred.add(provider::load);
                                        }
                                    } else {
                                        loadedProviders.add(providerClazz.newInstance());
                                    }
                                }
                            } else {
                                //todo look into why we get also system / jdk providers here, it slows down boot
                                loadedProviders = new ArrayList<>();
                                ServiceLoader<Provider> loader = ServiceLoader.load(Provider.class, classLoader);
                                Iterator<Provider> iterator = loader.iterator();
                                for (; ; ) {
                                    try {
                                        if (!(iterator.hasNext())) { break; }
                                        final Provider p = iterator.next();
                                        if (configSupplier != null) {
                                            deferred.add(p::load);
                                        }
                                        loadedProviders.add(p);
                                    } catch (ServiceConfigurationError | RuntimeException e) {
                                        ROOT_LOGGER.tracef(e, "Failed to initialize a security provider");
                                    }
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

                    @Override
                    public void dispose() {
                        if (handle != null) {
                            handle.remove();
                            handle = null;
                        }
                    }

                    private File resolveConfigLocation() {
                        final File resolvedPath;
                        if (relativeTo != null) {
                            PathManager pm = pathManager.getValue();
                            resolvedPath = new File(pm.resolveRelativePathEntry(path, relativeTo));
                            handle = pm.registerCallback(relativeTo, new Callback() {

                                @Override
                                public void pathModelEvent(PathEventContext eventContext, String name) {
                                    if (eventContext.isResourceServiceRestartAllowed() == false) {
                                        eventContext.reloadRequired();
                                    }
                                }

                                @Override
                                public void pathEvent(Event event, PathEntry pathEntry) {
                                    // Service dependencies should trigger a stop and start.
                                }
                            }, Event.REMOVED, Event.UPDATED);
                        } else {
                            resolvedPath = new File(path);
                        }
                        return resolvedPath;
                    }

                };
            }
        };

        Builder builder = TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.PROVIDER_LOADER)
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

    private static class LoadedProvidersAttributeHandler extends ElytronRuntimeOnlyHandler {

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName providerLoaderName = context.getCapabilityServiceName(PROVIDERS_CAPABILITY, context.getCurrentAddressValue(), Provider[].class);
            ServiceController<Provider[]> serviceContainer = getRequiredService(context.getServiceRegistry(false), providerLoaderName, Provider[].class);
            if (serviceContainer.getState() != State.UP) {
                return;
            }

            populateProviders(context.getResult(), serviceContainer.getValue());
        }

    }

}
