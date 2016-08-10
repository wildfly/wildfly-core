/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition.Parameters;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.logging.LoggingProfileOperations.LoggingProfileAdd;
import org.jboss.as.logging.deployments.resources.LoggingDeploymentResources;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.logmanager.WildFlyLogContextSelector;
import org.jboss.as.logging.stdio.LogContextStdioContextSelector;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.stdio.StdioContext;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingExtension implements Extension {

    private static final String RESOURCE_NAME = LoggingExtension.class.getPackage().getName() + ".LocalDescriptions";

    public static final String SUBSYSTEM_NAME = "logging";

    static final PathElement LOGGING_PROFILE_PATH = PathElement.pathElement(CommonAttributes.LOGGING_PROFILE);

    static final GenericSubsystemDescribeHandler DESCRIBE_HANDLER = GenericSubsystemDescribeHandler.create(LoggingChildResourceComparator.INSTANCE);

    private static final int MANAGEMENT_API_MAJOR_VERSION = 3;
    private static final int MANAGEMENT_API_MINOR_VERSION = 0;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);

    private static final ModuleIdentifier[] LOGGING_API_MODULES = new ModuleIdentifier[] {
            ModuleIdentifier.create("org.apache.commons.logging"),
            ModuleIdentifier.create("org.apache.log4j"),
            ModuleIdentifier.create("org.jboss.logging"),
            ModuleIdentifier.create("org.jboss.logging.jul-to-slf4j-stub"),
            ModuleIdentifier.create("org.jboss.logmanager"),
            ModuleIdentifier.create("org.slf4j"),
            ModuleIdentifier.create("org.slf4j.ext"),
            ModuleIdentifier.create("org.slf4j.impl"),
    };

    private static final List<String> DELEGATE_DESC_OPTS = Arrays.asList(
            AbstractHandlerDefinition.UPDATE_OPERATION_NAME,
            RootLoggerResourceDefinition.ROOT_LOGGER_ADD_OPERATION_NAME
    );

    /**
     * Returns a resource description resolver that uses common descriptions for some attributes.
     *
     * @param keyPrefix the prefix to be appended to the {@link #SUBSYSTEM_NAME}
     *
     * @return the resolver
     */
    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, LoggingExtension.class.getClassLoader(), true, false) {
            @Override
            public String getOperationParameterDescription(final String operationName, final String paramName, final Locale locale, final ResourceBundle bundle) {
                if (DELEGATE_DESC_OPTS.contains(operationName)) {
                    return getResourceAttributeDescription(paramName, locale, bundle);
                }
                return super.getOperationParameterDescription(operationName, paramName, locale, bundle);
            }

            @Override
            public String getOperationParameterValueTypeDescription(final String operationName, final String paramName, final Locale locale,
                                                                    final ResourceBundle bundle, final String... suffixes) {
                if (DELEGATE_DESC_OPTS.contains(operationName)) {
                    return getResourceAttributeDescription(paramName, locale, bundle);
                }
                return super.getOperationParameterValueTypeDescription(operationName, paramName, locale, bundle, suffixes);
            }

            @Override
            public String getOperationParameterDeprecatedDescription(final String operationName, final String paramName, final Locale locale, final ResourceBundle bundle) {
                if (DELEGATE_DESC_OPTS.contains(operationName)) {
                    return getResourceAttributeDeprecatedDescription(paramName, locale, bundle);
                }
                return super.getOperationParameterDeprecatedDescription(operationName, paramName, locale, bundle);
            }
        };
    }


    @Override
    public void initialize(final ExtensionContext context) {
        // The logging subsystem requires JBoss Log Manager to be used
        // Testing the log manager must use the FQCN as the classes may be loaded via different class loaders
        if (!java.util.logging.LogManager.getLogManager().getClass().getName().equals(org.jboss.logmanager.LogManager.class.getName())) {
            throw LoggingLogger.ROOT_LOGGER.extensionNotInitialized();
        }
        final WildFlyLogContextSelector contextSelector = WildFlyLogContextSelector.Factory.create();
        LogContext.setLogContextSelector(contextSelector);

        // Install STDIO context selector
        StdioContext.setStdioContextSelector(new LogContextStdioContextSelector(StdioContext.getStdioContext()));

        // Load logging API modules
        try {
            final ModuleLoader moduleLoader = Module.forClass(LoggingExtension.class).getModuleLoader();
            for (ModuleIdentifier moduleIdentifier : LOGGING_API_MODULES) {
                try {
                    contextSelector.addLogApiClassLoader(moduleLoader.loadModule(moduleIdentifier).getClassLoader());
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        } catch (Exception ignore) {
            // ignore
        }

        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_VERSION);


        PathManager pathManager = null;
        // The path manager is only available if this is a server
        if (context.getProcessType().isServer()) {
            pathManager = context.getPathManager();
        }
        final LoggingResourceDefinition rootResource = new LoggingResourceDefinition(pathManager, contextSelector);
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(rootResource);
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, DESCRIBE_HANDLER);
        // Register root sub-models
        registerSubModels(registration, true, subsystem, rootResource, context.isRegisterTransformers(), pathManager);

        // Register logging profile sub-models
        ApplicationTypeConfig atc = new ApplicationTypeConfig(SUBSYSTEM_NAME, CommonAttributes.LOGGING_PROFILE);
        ResourceDefinition profile = new SimpleResourceDefinition(
                new Parameters(LOGGING_PROFILE_PATH, getResourceDescriptionResolver())
                        .setAddHandler(new LoggingProfileAdd(pathManager))
                        .setRemoveHandler(LoggingProfileOperations.REMOVE_PROFILE)
                        .setAccessConstraints(new ApplicationTypeAccessConstraintDefinition(atc)));

        registerLoggingProfileSubModels(registration.registerSubModel(profile), pathManager);

        // Register deployment resources
        if (context.isRuntimeOnlyRegistrationValid()) {
            final SimpleResourceDefinition deploymentSubsystem = new SimpleResourceDefinition(LoggingResourceDefinition.SUBSYSTEM_PATH, getResourceDescriptionResolver("deployment"));
            final ManagementResourceRegistration deployments = subsystem.registerDeploymentModel(deploymentSubsystem);
            final ManagementResourceRegistration configurationResource = deployments.registerSubModel(LoggingDeploymentResources.CONFIGURATION);
            configurationResource.registerSubModel(LoggingDeploymentResources.HANDLER);
            configurationResource.registerSubModel(LoggingDeploymentResources.LOGGER);
            configurationResource.registerSubModel(LoggingDeploymentResources.FORMATTER);
            configurationResource.registerSubModel(LoggingDeploymentResources.FILTER);
            configurationResource.registerSubModel(LoggingDeploymentResources.POJO);
            configurationResource.registerSubModel(LoggingDeploymentResources.ERROR_MANAGER);
        }

        subsystem.registerXMLElementWriter(LoggingSubsystemWriter.INSTANCE);
    }


    @Override
    public void initializeParsers(final ExtensionParsingContext context) {
        setParser(context, Namespace.LOGGING_1_0, new LoggingSubsystemParser_1_0());
        setParser(context, Namespace.LOGGING_1_1, new LoggingSubsystemParser_1_1());
        setParser(context, Namespace.LOGGING_1_2, new LoggingSubsystemParser_1_2());
        setParser(context, Namespace.LOGGING_1_3, new LoggingSubsystemParser_1_3());
        setParser(context, Namespace.LOGGING_1_4, new LoggingSubsystemParser_1_4());
        setParser(context, Namespace.LOGGING_1_5, new LoggingSubsystemParser_1_5());
        setParser(context, Namespace.LOGGING_2_0, new LoggingSubsystemParser_2_0());
        setParser(context, Namespace.LOGGING_3_0, new LoggingSubsystemParser_3_0());
    }

    private void registerLoggingProfileSubModels(final ManagementResourceRegistration registration, final PathManager pathManager) {
        registerSubModels(registration, false, null, null, false, pathManager);
    }

    private void registerSubModels(final ManagementResourceRegistration registration,
                                   final boolean includeLegacyAttributes, final SubsystemRegistration subsystem,
                                   final LoggingResourceDefinition subsystemResourceDefinition, final boolean registerTransformers, final PathManager pathManager) {
        // Only register if the path manager is not null, e.g. is a server
        ResolvePathHandler resolvePathHandler = null;
        PathInfoHandler diskUsagePathHandler = null;
        if (pathManager != null) {
            resolvePathHandler = ResolvePathHandler.Builder.of(pathManager)
                    .setParentAttribute(CommonAttributes.FILE)
                    .build();
            diskUsagePathHandler = PathInfoHandler.Builder.of(pathManager)
                    .setParentAttribute(CommonAttributes.FILE)
                    .addAttribute(PathResourceDefinition.PATH, PathResourceDefinition.RELATIVE_TO)
                    .build();
            final LogFileResourceDefinition logFileResourceDefinition = new LogFileResourceDefinition(pathManager);
            registration.registerSubModel(logFileResourceDefinition);
        }

        final RootLoggerResourceDefinition rootLoggerResourceDefinition = new RootLoggerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(rootLoggerResourceDefinition);

        final LoggerResourceDefinition loggerResourceDefinition = new LoggerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(loggerResourceDefinition);

        final AsyncHandlerResourceDefinition asyncHandlerResourceDefinition = new AsyncHandlerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(asyncHandlerResourceDefinition);

        final ConsoleHandlerResourceDefinition consoleHandlerResourceDefinition = new ConsoleHandlerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(consoleHandlerResourceDefinition);

        final FileHandlerResourceDefinition fileHandlerResourceDefinition = new FileHandlerResourceDefinition(resolvePathHandler, diskUsagePathHandler, includeLegacyAttributes);
        registration.registerSubModel(fileHandlerResourceDefinition);

        final PeriodicHandlerResourceDefinition periodicHandlerResourceDefinition = new PeriodicHandlerResourceDefinition(resolvePathHandler, diskUsagePathHandler, includeLegacyAttributes);
        registration.registerSubModel(periodicHandlerResourceDefinition);

        final PeriodicSizeRotatingHandlerResourceDefinition periodicSizeRotatingHandlerResourceDefinition = new PeriodicSizeRotatingHandlerResourceDefinition(resolvePathHandler, diskUsagePathHandler);
        registration.registerSubModel(periodicSizeRotatingHandlerResourceDefinition);

        final SizeRotatingHandlerResourceDefinition sizeRotatingHandlerResourceDefinition = new SizeRotatingHandlerResourceDefinition(resolvePathHandler, includeLegacyAttributes);
        registration.registerSubModel(sizeRotatingHandlerResourceDefinition);

        final CustomHandlerResourceDefinition customHandlerResourceDefinition = new CustomHandlerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(customHandlerResourceDefinition);

        registration.registerSubModel(SyslogHandlerResourceDefinition.INSTANCE);
        registration.registerSubModel(PatternFormatterResourceDefinition.INSTANCE);
        registration.registerSubModel(CustomFormatterResourceDefinition.INSTANCE);

        if (registerTransformers) {
            registerTransformers(subsystem,
                    subsystemResourceDefinition,
                    rootLoggerResourceDefinition,
                    loggerResourceDefinition,
                    asyncHandlerResourceDefinition,
                    consoleHandlerResourceDefinition,
                    fileHandlerResourceDefinition,
                    periodicHandlerResourceDefinition,
                    periodicSizeRotatingHandlerResourceDefinition,
                    sizeRotatingHandlerResourceDefinition,
                    customHandlerResourceDefinition,
                    SyslogHandlerResourceDefinition.INSTANCE,
                    PatternFormatterResourceDefinition.INSTANCE,
                    CustomFormatterResourceDefinition.INSTANCE);
        }
    }

    private void registerTransformers(final SubsystemRegistration registration, final TransformerResourceDefinition... defs) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getSubsystemVersion());

        registerTransformers(chainedBuilder, registration.getSubsystemVersion(), KnownModelVersion.VERSION_2_0_0, defs);
        // Version 1.5.0 has the periodic-size-rotating-file-handler and the suffix attribute on the size-rotating-file-handler.
        // Neither of these are in 2.0.0 (WildFly 8.x). Mapping from 3.0.0 to 1.5.0 is required
        registerTransformers(chainedBuilder, registration.getSubsystemVersion(), KnownModelVersion.VERSION_1_5_0, defs);
        registerTransformers(chainedBuilder, KnownModelVersion.VERSION_1_5_0, KnownModelVersion.VERSION_1_4_0, defs);
        registerTransformers(chainedBuilder, KnownModelVersion.VERSION_1_4_0, KnownModelVersion.VERSION_1_3_0, defs);

        chainedBuilder.buildAndRegister(registration, new ModelVersion[] {
                KnownModelVersion.VERSION_2_0_0.getModelVersion(),
        }, new ModelVersion[] {
                KnownModelVersion.VERSION_1_3_0.getModelVersion(),
                KnownModelVersion.VERSION_1_4_0.getModelVersion(),
                KnownModelVersion.VERSION_1_5_0.getModelVersion(),
        });
    }

    private void registerTransformers(final ChainedTransformationDescriptionBuilder chainedBuilder, final KnownModelVersion fromVersion, final KnownModelVersion toVersion, final TransformerResourceDefinition... defs) {
        registerTransformers(chainedBuilder, fromVersion.getModelVersion(), toVersion, defs);
    }

    private void registerTransformers(final ChainedTransformationDescriptionBuilder chainedBuilder, final ModelVersion fromVersion, final KnownModelVersion toVersion, final TransformerResourceDefinition... defs) {
        final ResourceTransformationDescriptionBuilder subsystemBuilder = chainedBuilder.createBuilder(fromVersion, toVersion.getModelVersion());
        final ResourceTransformationDescriptionBuilder loggingProfileBuilder = subsystemBuilder.addChildResource(LOGGING_PROFILE_PATH);

        for (TransformerResourceDefinition def : defs) {
            def.registerTransformers(toVersion, subsystemBuilder, loggingProfileBuilder);
        }
    }

    private static void setParser(final ExtensionParsingContext context, final Namespace namespace, final XMLElementReader<List<ModelNode>> parser) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, namespace.getUriString(), parser);
    }

    public static class LoggingChildResourceComparator implements Comparator<PathElement> {
        static final LoggingChildResourceComparator INSTANCE = new LoggingChildResourceComparator();
        static final int GREATER = 1;
        static final int EQUAL = 0;
        static final int LESS = -1;

        @Override
        public int compare(final PathElement o1, final PathElement o2) {
            final String key1 = o1.getKey();
            final String key2 = o2.getKey();
            int result = key1.compareTo(key2);
            if (result != EQUAL) {
                if (ModelDescriptionConstants.SUBSYSTEM.equals(key1)) {
                    result = LESS;
                } else if (ModelDescriptionConstants.SUBSYSTEM.equals(key2)) {
                    result = GREATER;
                } else if (CommonAttributes.LOGGING_PROFILE.equals(key1)) {
                    result = LESS;
                } else if (CommonAttributes.LOGGING_PROFILE.equals(key2)) {
                    result = GREATER;
                } else if (PatternFormatterResourceDefinition.PATTERN_FORMATTER.getName().equals(key1)) {
                    result = LESS;
                } else if (PatternFormatterResourceDefinition.PATTERN_FORMATTER.getName().equals(key2)) {
                    result = GREATER;
                } else if (CustomFormatterResourceDefinition.CUSTOM_FORMATTER.getName().equals(key1)) {
                    result = LESS;
                } else if (CustomFormatterResourceDefinition.CUSTOM_FORMATTER.getName().equals(key2)) {
                    result = GREATER;
                } else if (RootLoggerResourceDefinition.ROOT_LOGGER_PATH_NAME.equals(key1)) {
                    result = GREATER;
                } else if (RootLoggerResourceDefinition.ROOT_LOGGER_PATH_NAME.equals(key2)) {
                    result = LESS;
                } else if (LoggerResourceDefinition.LOGGER.equals(key1)) {
                    result = GREATER;
                } else if (LoggerResourceDefinition.LOGGER.equals(key2)) {
                    result = LESS;
                } else if (AsyncHandlerResourceDefinition.ASYNC_HANDLER.equals(key1)) {
                    result = GREATER;
                } else if (AsyncHandlerResourceDefinition.ASYNC_HANDLER.equals(key2)) {
                    result = LESS;
                }
            }
            return result;
        }
    }

}
