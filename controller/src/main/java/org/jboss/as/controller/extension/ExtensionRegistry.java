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

package org.jboss.as.controller.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ExpressionResolverExtension;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ModelVersionRange;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemXmlWriterRegistry;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationEntry;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.transform.CombinedTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLMapper;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A registry for information about {@link org.jboss.as.controller.Extension}s to the core application server.
 * In server/standalone mode there will be one extension registry for the whole server process. In domain mode,
 * there will be:
 * <ul>
 *      <li>One extension registry for extensions in the domain model</li>
 *      <li>One extension registry for extension in the host model</li>
 * </ul>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public final class ExtensionRegistry {

    // Hack to restrict the extensions to which we expose ExtensionContextSupplement
    private static final Set<String> legallySupplemented;
    static {
        Set<String> set = new HashSet<>(4);
        set.add("org.jboss.as.jmx");
        set.add("Test");  // used by shared subsystem test fixture TestModelControllerService
        legallySupplemented = Collections.unmodifiableSet(set);
    }

    private final ProcessType processType;

    private SubsystemXmlWriterRegistry writerRegistry;
    private volatile PathManager pathManager;
    private volatile ExpressionResolver.ResolverExtensionRegistry resolverExtensionRegistry;

    private final ConcurrentMap<String, ExtensionInfo> extensions = new ConcurrentHashMap<String, ExtensionInfo>();
    // subsystem -> extension
    private final ConcurrentMap<String, String> reverseMap = new ConcurrentHashMap<String, String>();
    private final RunningModeControl runningModeControl;
    private final ManagedAuditLogger auditLogger;
    private final JmxAuthorizer authorizer;
    private final Supplier<SecurityIdentity> securityIdentitySupplier;
    private final ConcurrentHashMap<String, SubsystemInformation> subsystemsInfo = new ConcurrentHashMap<String, SubsystemInformation>();
    private volatile TransformerRegistry transformerRegistry = TransformerRegistry.Factory.create();
    private final RuntimeHostControllerInfoAccessor hostControllerInfoAccessor;

    /**
     * Constructor
     *
     * @param processType the type of the process
     * @param runningModeControl the process' running mode
     * @param auditLogger logger for auditing changes
     * @param authorizer hook for exposing access control information to the JMX subsystem
     * @param hostControllerInfoAccessor the host controller
     */
    public ExtensionRegistry(ProcessType processType, RunningModeControl runningModeControl, ManagedAuditLogger auditLogger, JmxAuthorizer authorizer,
            Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostControllerInfoAccessor) {
        this.processType = processType;
        this.runningModeControl = runningModeControl;
        this.auditLogger = auditLogger != null ? auditLogger : AuditLogger.NO_OP_LOGGER;
        this.authorizer = authorizer != null ? authorizer : NO_OP_AUTHORIZER;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.hostControllerInfoAccessor = hostControllerInfoAccessor;
    }

    /**
     * Constructor
     *
     * @param processType the type of the process
     * @param runningModeControl the process' running mode
     * @deprecated Here for core-model-test and subsystem-test backwards compatibility
     */
    @Deprecated
    public ExtensionRegistry(ProcessType processType, RunningModeControl runningModeControl) {
        this(processType, runningModeControl, null, null, null, RuntimeHostControllerInfoAccessor.SERVER);
    }

    /**
     * Sets the {@link SubsystemXmlWriterRegistry} to use for storing subsystem marshallers.
     *
     * @param writerRegistry the writer registry
     */
    public void setWriterRegistry(final SubsystemXmlWriterRegistry writerRegistry) {
        this.writerRegistry = writerRegistry;

    }

    /**
     * Sets the {@link PathManager} to provide {@link ExtensionContext#getPathManager() via the ExtensionContext}.
     *
     * @param pathManager the path manager
     */
    public void setPathManager(final PathManager pathManager) {
        this.pathManager = pathManager;
    }

    public void setResolverExtensionRegistry(ExpressionResolver.ResolverExtensionRegistry registry) {
        this.resolverExtensionRegistry = registry;
    }

    public SubsystemInformation getSubsystemInfo(final String name) {
        return subsystemsInfo.get(name);
    }

    /**
     * Gets the module names of all known {@link org.jboss.as.controller.Extension}s.
     *
     * @return the names. Will not return {@code null}
     */
    public Set<String> getExtensionModuleNames() {
        return Collections.unmodifiableSet(extensions.keySet());
    }

    /**
     * Gets information about the subsystems provided by a given {@link org.jboss.as.controller.Extension}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @return map of subsystem names to information about the subsystem.
     */
    public Map<String, SubsystemInformation> getAvailableSubsystems(String moduleName) {
        Map<String, SubsystemInformation> result = null;
        final ExtensionInfo info = extensions.get(moduleName);
        if (info != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (info) {
                result = Collections.unmodifiableMap(new HashMap<String, SubsystemInformation>(info.subsystems));
            }
        }
        return result;
    }

    /**
     * Gets an {@link ExtensionParsingContext} for use when
     * {@link org.jboss.as.controller.Extension#initializeParsers(ExtensionParsingContext) initializing the extension's parsers}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @param xmlMapper  the {@link XMLMapper} handling the extension parsing. Can be {@code null} if there won't
     *                   be any actual parsing (e.g. in a slave Host Controller or in a server in a managed domain)
     * @return the {@link ExtensionParsingContext}.  Will not return {@code null}
     */
    public ExtensionParsingContext getExtensionParsingContext(final String moduleName, final XMLMapper xmlMapper) {
        return new ExtensionParsingContextImpl(moduleName, xmlMapper);
    }

    /**
     * Ask the given {@code extension} to
     * {@link Extension#initializeParsers(ExtensionParsingContext) initialize its parsers}. Should be used in
     * preference to calling {@link #getExtensionParsingContext(String, XMLMapper)} and passing the returned
     * value to {@code Extension#initializeParsers(context)} as this method allows the registry to take
     * additional action when the extension is done.
     *
     * @param extension  the extension. Cannot be {@code null}
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @param xmlMapper  the {@link XMLMapper} handling the extension parsing. Can be {@code null} if there won't
     *                   be any actual parsing (e.g. in a slave Host Controller or in a server in a managed domain)
     */
    public final void initializeParsers(final Extension extension, final String moduleName, final XMLMapper xmlMapper) {
        ExtensionParsingContextImpl parsingContext = new ExtensionParsingContextImpl(moduleName, xmlMapper);
        extension.initializeParsers(parsingContext);
        parsingContext.attemptCurrentParserInitialization();
    }

    /**
     * Gets an {@link ExtensionContext} for use when handling an {@code add} operation for
     * a resource representing an {@link org.jboss.as.controller.Extension}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @param rootRegistration the root management resource registration
     * @param isMasterDomainController set to {@code true} if we are the master domain controller, in which case transformers get registered
     *
     * @return  the {@link ExtensionContext}.  Will not return {@code null}
     *
     * @deprecated use {@link #getExtensionContext(String, ManagementResourceRegistration, ExtensionRegistryType)}. Main code should be using this, but this is left behind in case any tests need to use this code.
     */
    @Deprecated
    public ExtensionContext getExtensionContext(final String moduleName, ManagementResourceRegistration rootRegistration, boolean isMasterDomainController) {
        ExtensionRegistryType type = isMasterDomainController ? ExtensionRegistryType.MASTER : ExtensionRegistryType.SLAVE;
        return getExtensionContext(moduleName, rootRegistration, type);
    }

    /**
     * Gets an {@link ExtensionContext} for use when handling an {@code add} operation for
     * a resource representing an {@link org.jboss.as.controller.Extension}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @param rootRegistration the root management resource registration
     * @param extensionRegistryType the type of registry we are working on, which has an effect on things like whether extensions get registered etc.
     *
     * @return  the {@link ExtensionContext}.  Will not return {@code null}
     */
    public ExtensionContext getExtensionContext(final String moduleName, ManagementResourceRegistration rootRegistration, ExtensionRegistryType extensionRegistryType) {
        // Can't use processType.isServer() to determine where to look for profile reg because a lot of test infrastructure
        // doesn't add the profile mrr even in HC-based tests
        ManagementResourceRegistration profileRegistration = rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(PROFILE)));
        if (profileRegistration == null) {
            profileRegistration = rootRegistration;
        }
        ManagementResourceRegistration deploymentsRegistration = processType.isServer() ? rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT))) : null;

        // Hack to restrict extra data to specified extension(s)
        boolean allowSupplement = legallySupplemented.contains(moduleName);
        ManagedAuditLogger al = allowSupplement ? auditLogger : null;
        return new ExtensionContextImpl(moduleName, profileRegistration, deploymentsRegistration, pathManager, extensionRegistryType, al);
    }

    public Set<ProfileParsingCompletionHandler> getProfileParsingCompletionHandlers() {
        Set<ProfileParsingCompletionHandler> result = new HashSet<ProfileParsingCompletionHandler>();

        for (ExtensionInfo extensionInfo : extensions.values()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (extensionInfo) {
                if (extensionInfo.parsingCompletionHandler != null) {
                    result.add(extensionInfo.parsingCompletionHandler);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Cleans up a extension module's subsystems from the resource registration model.
     *
     * @param rootResource the model root resource
     * @param moduleName   the name of the extension's module. Cannot be {@code null}
     * @throws IllegalStateException if the extension still has subsystems present in {@code rootResource} or its children
     */
    public void removeExtension(Resource rootResource, String moduleName, ManagementResourceRegistration rootRegistration) throws IllegalStateException {
        final ManagementResourceRegistration profileReg;
        if (rootRegistration.getPathAddress().size() == 0) {
            //domain or server extension
            // Can't use processType.isServer() to determine where to look for profile reg because a lot of test infrastructure
            // doesn't add the profile mrr even in HC-based tests
            ManagementResourceRegistration reg = rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(PROFILE)));
            if (reg == null) {
                reg = rootRegistration;
            }
            profileReg = reg;
        } else {
            //host model extension
            profileReg = rootRegistration;
        }
        ManagementResourceRegistration deploymentsReg = processType.isServer() ? rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT))) : null;

        ExtensionInfo extension = extensions.remove(moduleName);
        if (extension != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (extension) {

                if (extension.expressionResolverExtension != null) {
                    resolverExtensionRegistry.removeResolverExtension(extension.expressionResolverExtension);
                }

                Set<String> subsystemNames = extension.subsystems.keySet();

                final boolean dcExtension = processType.isHostController() ?
                    rootRegistration.getPathAddress().size() == 0 : false;

                for (String subsystem : subsystemNames) {
                    if (hasSubsystemsRegistered(rootResource, subsystem, dcExtension)) {
                        // Restore the data
                        extensions.put(moduleName, extension);
                        throw ControllerLogger.ROOT_LOGGER.removingExtensionWithRegisteredSubsystem(moduleName, subsystem);
                    }
                }
                for (Map.Entry<String, SubsystemInformation> entry : extension.subsystems.entrySet()) {
                    String subsystem = entry.getKey();
                    profileReg.unregisterSubModel(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem));
                    if (deploymentsReg != null) {
                        deploymentsReg.unregisterSubModel(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem));
                        deploymentsReg.unregisterSubModel(PathElement.pathElement(ModelDescriptionConstants.SUBDEPLOYMENT, subsystem));
                    }

                    if (extension.xmlMapper != null) {
                        SubsystemInformationImpl subsystemInformation = SubsystemInformationImpl.class.cast(entry.getValue());
                        for (String namespace : subsystemInformation.getXMLNamespaces()) {
                            extension.xmlMapper.unregisterRootElement(new QName(namespace, SUBSYSTEM));
                        }
                    }
                }
            }
        }
    }

    private boolean hasSubsystemsRegistered(Resource rootResource, String subsystem, boolean dcExtension) {
        if (!dcExtension) {
            return rootResource.getChild(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem)) != null;
        }

        for (Resource profile : rootResource.getChildren(PROFILE)) {
            if (profile.getChild(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem)) != null) {
                return true;
            }
        }
        return false;
    }
    /**
     * Clears the registry to prepare for re-registration (e.g. as part of a reload).
     */
    public void clear() {
        synchronized (extensions) {    // we synchronize just to guard unnamedMerged
            transformerRegistry = TransformerRegistry.Factory.create();
            extensions.clear();
            reverseMap.clear();
            subsystemsInfo.clear();
        }
    }

    /**
     * Records the versions of the subsystems associated with the given {@code moduleName} as properties in the
     * provided {@link ModelNode}. Each subsystem property key will be the subsystem name and the value will be
     * a string composed of the subsystem major version dot appended to its minor version.
     *
     * @param moduleName the name of the extension module
     * @param subsystems a model node of type {@link org.jboss.dmr.ModelType#UNDEFINED} or type {@link org.jboss.dmr.ModelType#OBJECT}
     */
    public void recordSubsystemVersions(String moduleName, ModelNode subsystems) {
        final Map<String, SubsystemInformation> subsystemsInfo = getAvailableSubsystems(moduleName);
        if(subsystemsInfo != null && ! subsystemsInfo.isEmpty()) {
            for(final Map.Entry<String, SubsystemInformation> entry : subsystemsInfo.entrySet()) {
                SubsystemInformation subsystem = entry.getValue();
                subsystems.add(entry.getKey(),
                        subsystem.getManagementInterfaceMajorVersion() + "."
                        + subsystem.getManagementInterfaceMinorVersion()
                        + "." + subsystem.getManagementInterfaceMicroVersion());
            }
        }
    }

    private ExtensionInfo getExtensionInfo(final String extensionModuleName) {
        ExtensionInfo result = extensions.get(extensionModuleName);
        if (result == null) {
            result = new ExtensionInfo(extensionModuleName);
            ExtensionInfo existing = extensions.putIfAbsent(extensionModuleName, result);
            result = existing == null ? result : existing;
        }
        return result;
    }

    private void checkNewSubystem(final String extensionModuleName, final String subsystemName) {
        String existingModule = reverseMap.putIfAbsent(subsystemName, extensionModuleName);
        if (existingModule != null && !extensionModuleName.equals(existingModule)) {
            throw ControllerLogger.ROOT_LOGGER.duplicateSubsystem(subsystemName, extensionModuleName, existingModule);
        }
    }

    public TransformerRegistry getTransformerRegistry() {
        return transformerRegistry;
    }

    private class ExtensionParsingContextImpl implements ExtensionParsingContext {

        private final ExtensionInfo extension;
        private boolean hasNonSupplierParser;
        private String latestNamespace;
        private Supplier<XMLElementReader<List<ModelNode>>> latestSupplier;

        private ExtensionParsingContextImpl(String extensionName, XMLMapper xmlMapper) {
            extension = getExtensionInfo(extensionName);
            if (xmlMapper != null) {
                synchronized (extension) {
                    extension.xmlMapper = xmlMapper;
                }
            }
        }

        @Override
        public ProcessType getProcessType() {
            return processType;
        }

        @Override
        public RunningMode getRunningMode() {
            return runningModeControl.getRunningMode();
        }

        @Override
        public void setSubsystemXmlMapping(String subsystemName, String namespaceUri, XMLElementReader<List<ModelNode>> reader) {
            assert subsystemName != null : "subsystemName is null";
            assert namespaceUri != null : "namespaceUri is null";
            synchronized (extension) {
                extension.getSubsystemInfo(subsystemName).addParsingNamespace(namespaceUri);
                if (extension.xmlMapper != null) {
                    extension.xmlMapper.registerRootElement(new QName(namespaceUri, SUBSYSTEM), reader);
                    preCacheParserDescription(reader);
                    hasNonSupplierParser = true;
                }
            }
        }

        @Override
        public void setSubsystemXmlMapping(String subsystemName, String namespaceUri, Supplier<XMLElementReader<List<ModelNode>>> supplier){
            assert subsystemName != null : "subsystemName is null";
            assert namespaceUri != null : "namespaceUri is null";
            synchronized (extension) {
                extension.getSubsystemInfo(subsystemName).addParsingNamespace(namespaceUri);
                if (extension.xmlMapper != null) {
                    extension.xmlMapper.registerRootElement(new QName(namespaceUri, SUBSYSTEM), supplier);
                    if (!hasNonSupplierParser) {
                        if (latestNamespace == null || latestNamespace.compareTo(namespaceUri) < 0) {
                            latestNamespace = namespaceUri;
                            latestSupplier = supplier;
                        }
                    }
                }
            }
        }

        @Override
        public void setProfileParsingCompletionHandler(ProfileParsingCompletionHandler handler) {
            assert handler != null : "handler is null";
            synchronized (extension) {
                extension.parsingCompletionHandler = handler;
            }
        }

        private void attemptCurrentParserInitialization() {
            if (ExtensionRegistry.this.processType != ProcessType.DOMAIN_SERVER
                    && !hasNonSupplierParser && latestSupplier != null) {
                // We've only been passed suppliers for parsers, which means the model
                // initialization work commonly performed when a parser is constructed or
                // by a PersistentResourceXMLParser may not have been done. And we want
                // it done now, and not during parsing, as this may be called by a
                // parallel boot thread while parsing is single threaded.
                preCacheParserDescription(latestSupplier.get());
            }
        }

        private void preCacheParserDescription(XMLElementReader<List<ModelNode>> reader) {
            if (ExtensionRegistry.this.processType != ProcessType.DOMAIN_SERVER
                    && reader instanceof PersistentResourceXMLParser) {
                // In a standard WildFly boot this method is being called as part of concurrent
                // work on multiple extensions. Generating the PersistentResourceXMLDescription
                // used by PersistentResourceXMLParser involves a lot of classloading and static
                // field initialization that can benefit by being done as part of this concurrent
                // work instead of being deferred to the single-threaded parsing phase. So, we ask
                // the parser to generate and cache that description for later use during parsing.
                //noinspection deprecation
                ((PersistentResourceXMLParser) reader).cacheXMLDescription();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private class ExtensionContextImpl implements ExtensionContext, ExtensionContextSupplement {

        private final ExtensionInfo extension;
        private final PathManager pathManager;
        private final boolean registerTransformers;
        private final ManagedAuditLogger auditLogger;
        private final boolean allowSupplement;
        private final ManagementResourceRegistration profileRegistration;
        private final ManagementResourceRegistration deploymentsRegistration;
        private final ExtensionRegistryType extensionRegistryType;

        private ExtensionContextImpl(String extensionName, ManagementResourceRegistration profileResourceRegistration,
                                     ManagementResourceRegistration deploymentsResourceRegistration, PathManager pathManager,
                                     ExtensionRegistryType extensionRegistryType, ManagedAuditLogger auditLogger) {
            assert pathManager != null || !processType.isServer() : "pathManager is null";
            this.pathManager = pathManager;
            this.extension = getExtensionInfo(extensionName);
            this.registerTransformers = extensionRegistryType == ExtensionRegistryType.MASTER;
            this.auditLogger = auditLogger;
            this.allowSupplement = auditLogger != null;
            this.profileRegistration = profileResourceRegistration;

            if (deploymentsResourceRegistration != null) {
                PathAddress subdepAddress = PathAddress.pathAddress(PathElement.pathElement(ModelDescriptionConstants.SUBDEPLOYMENT));
                final ManagementResourceRegistration subdeployments = deploymentsResourceRegistration.getSubModel(subdepAddress);
                this.deploymentsRegistration = subdeployments == null ? deploymentsResourceRegistration
                        : new DeploymentManagementResourceRegistration(deploymentsResourceRegistration, subdeployments);
            } else {
                this.deploymentsRegistration = null;
            }
            this.extensionRegistryType = extensionRegistryType;
        }

        @Override
        public SubsystemRegistration registerSubsystem(String name, ModelVersion version) {
            return registerSubsystem(name, version, false);
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion) throws IllegalArgumentException, IllegalStateException {
            return registerSubsystem(name, majorVersion, minorVersion, 0);
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion, int microVersion) {
            return registerSubsystem(name, majorVersion, minorVersion, microVersion, false);
        }

        @Override
        @Deprecated
        public SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion, int microVersion, boolean deprecated) {
            return registerSubsystem(name, ModelVersion.create(majorVersion, minorVersion, microVersion), deprecated);
        }

        @Override
        public SubsystemRegistration registerSubsystem(String name, ModelVersion version, boolean deprecated) {
            assert name != null : "name is null";
            checkNewSubystem(extension.extensionModuleName, name);
            SubsystemInformationImpl info = extension.getSubsystemInfo(name);
            info.setVersion(version);
            subsystemsInfo.put(name, info);
            if (deprecated){
                ControllerLogger.DEPRECATED_LOGGER.extensionDeprecated(name);
            }
            SubsystemRegistrationImpl result =  new SubsystemRegistrationImpl(name, version,
                                profileRegistration, deploymentsRegistration, extensionRegistryType, extension.extensionModuleName, processType);
            if (registerTransformers){
                transformerRegistry.loadAndRegisterTransformers(name, version, extension.extensionModuleName);
            }
            return result;
        }

        @Override
        public void registerExpressionResolverExtension(Supplier<ExpressionResolverExtension> supplier, Pattern expressionPattern,
                                                        boolean requireRuntimeResolution) {

            if (resolverExtensionRegistry == null) {
                if (extensionRegistryType.getContextType() != ContextType.DOMAIN) {
                    // some test cases might not configure a registry,
                    // but they need to if they will use an extension that registers one
                    throw new IllegalStateException();
                } // else we don't use ExpressionResolverExtension's from extension when processing the domain config
            } else {
                this.extension.expressionResolverExtension =
                        new FutureExpressionResolverExtension(supplier, expressionPattern, requireRuntimeResolution);
                resolverExtensionRegistry.addResolverExtension(this.extension.expressionResolverExtension);
            }
        }

        @Override
        public ContextType getType() {
            return extensionRegistryType.getContextType();
        }

        @Override
        public ProcessType getProcessType() {
            return processType;
        }

        @Override
        public RunningMode getRunningMode() {
            return runningModeControl.getRunningMode();
        }

        @Override
        public boolean isRuntimeOnlyRegistrationValid() {
            if (processType.isServer()) {
                return true;
            }
            if (processType.isHostController() && extensionRegistryType == ExtensionRegistryType.HOST) {
                return true;
            }
            return false;
        }

        @Override
        public PathManager getPathManager() {
            if (!processType.isServer()) {
                throw ControllerLogger.ROOT_LOGGER.pathManagerNotAvailable(processType);
            }
            return pathManager;
        }

        @Override
        @Deprecated
        public boolean isRegisterTransformers() {
            return registerTransformers;
        }

        // ExtensionContextSupplement implementation

        /**
         * This method is only for internal use. We do NOT currently want to expose it on the ExtensionContext interface.
         */
        @Override
        public AuditLogger getAuditLogger(boolean inheritConfiguration, boolean manualCommit) {
            if (!allowSupplement) {
                throw new UnsupportedOperationException();
            }
            if (inheritConfiguration) {
                return auditLogger;
            }
            return auditLogger.createNewConfiguration(manualCommit);
        }

        /**
         * This method is only for internal use. We do NOT currently want to expose it on the ExtensionContext interface.
         */
        @Override
        public JmxAuthorizer getAuthorizer() {
            if (!allowSupplement) {
                throw new UnsupportedOperationException();
            }
            return authorizer;
        }

        /**
         * This method is only for internal use. We do NOT currently want to expose it on the ExtensionContext interface.
         */
        @Override
        public Supplier<SecurityIdentity> getSecurityIdentitySupplier() {
            if (!allowSupplement) {
                throw new UnsupportedOperationException();
            }
            return securityIdentitySupplier;
        }

        @Override
        public RuntimeHostControllerInfoAccessor getHostControllerInfoAccessor() {
            if (!allowSupplement) {
                throw new UnsupportedOperationException();
            }
            return hostControllerInfoAccessor;
        }
    }

    private class SubsystemInformationImpl implements SubsystemInformation {

        private ModelVersion version;
        private final List<String> parsingNamespaces = new ArrayList<String>();

        @Override
        public List<String> getXMLNamespaces() {
            return Collections.unmodifiableList(parsingNamespaces);
        }

        void addParsingNamespace(final String namespace) {
            parsingNamespaces.add(namespace);
        }

        @Override
        public Integer getManagementInterfaceMajorVersion() {
            return version != null ? version.getMajor() : null;
        }

        @Override
        public Integer getManagementInterfaceMinorVersion() {
            return version != null ? version.getMinor() : null;
        }

        @Override
        public Integer getManagementInterfaceMicroVersion() {
            return version != null ? version.getMicro() : null;
        }

        @Override
        public ModelVersion getManagementInterfaceVersion() {
            return version;
        }

        private void setVersion(ModelVersion version) {
            this.version = version;
        }
    }

    private class SubsystemRegistrationImpl implements SubsystemRegistration {
        private final String name;
        private final ModelVersion version;
        private final ManagementResourceRegistration profileRegistration;
        private final ManagementResourceRegistration deploymentsRegistration;
        private final ExtensionRegistryType extensionRegistryType;
        private final String extensionModuleName;
        private volatile boolean hostCapable;
        private volatile boolean modelsRegistered;

        private SubsystemRegistrationImpl(String name, ModelVersion version,
                                          ManagementResourceRegistration profileRegistration,
                                          ManagementResourceRegistration deploymentsRegistration,
                                          ExtensionRegistryType extensionRegistryType,
                                          String extensionModuleName,
                                          ProcessType processType) {
            assert profileRegistration != null;
            this.name = name;
            this.profileRegistration = profileRegistration;
            if (deploymentsRegistration == null){
                this.deploymentsRegistration = ManagementResourceRegistration.Factory.forProcessType(processType).createRegistration(new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE));
            }else {
                this.deploymentsRegistration = deploymentsRegistration;
            }
            this.version = version;
            this.extensionRegistryType = extensionRegistryType;
            this.extensionModuleName = extensionModuleName;
        }

        @Override
        public void setHostCapable() {
            if (modelsRegistered) {
                throw ControllerLogger.ROOT_LOGGER.registerHostCapableMustHappenFirst(name);
            }
            hostCapable = true;
        }

        @Override
        public ManagementResourceRegistration registerSubsystemModel(ResourceDefinition resourceDefinition) {
            assert resourceDefinition != null : "resourceDefinition is null";
            checkHostCapable();
            return profileRegistration.registerSubModel(resourceDefinition);
        }

        @Override
        public ManagementResourceRegistration registerDeploymentModel(ResourceDefinition resourceDefinition) {
            assert resourceDefinition != null : "resourceDefinition is null";
            return deploymentsRegistration.registerSubModel(resourceDefinition);
        }

        @Override
        public void registerXMLElementWriter(XMLElementWriter<SubsystemMarshallingContext> writer) {
            writerRegistry.registerSubsystemWriter(name, writer);
        }

        @Override
        public void registerXMLElementWriter(Supplier<XMLElementWriter<SubsystemMarshallingContext>> writer) {
            writerRegistry.registerSubsystemWriter(name, writer);
        }

        @Override
        public TransformersSubRegistration registerModelTransformers(final ModelVersionRange range, final ResourceTransformer subsystemTransformer) {
            modelsRegistered = true;
            checkHostCapable();
            return transformerRegistry.registerSubsystemTransformers(name, range, subsystemTransformer);
        }

        @Override
        public TransformersSubRegistration registerModelTransformers(ModelVersionRange version, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean placeholder) {
            modelsRegistered = true;
            checkHostCapable();
            return transformerRegistry.registerSubsystemTransformers(name, version, resourceTransformer, operationTransformer, placeholder);
        }

        @Override
        @Deprecated
        public TransformersSubRegistration registerModelTransformers(ModelVersionRange version, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer) {
            modelsRegistered = true;
            checkHostCapable();
            return transformerRegistry.registerSubsystemTransformers(name, version, resourceTransformer, operationTransformer, false);
        }


        @Override
        public TransformersSubRegistration registerModelTransformers(ModelVersionRange version, CombinedTransformer combinedTransformer) {
            modelsRegistered = true;
            checkHostCapable();
            return transformerRegistry.registerSubsystemTransformers(name, version, combinedTransformer, combinedTransformer, false);
        }

        @Override
        public ModelVersion getSubsystemVersion() {
            return version;
        }

        private void checkHostCapable() {
            if (extensionRegistryType == ExtensionRegistryType.HOST && !hostCapable) {
                throw ControllerLogger.ROOT_LOGGER.nonHostCapableSubsystemInHostModel(name, extensionModuleName);
            }
        }
    }

    private class ExtensionInfo {
        private final Map<String, SubsystemInformation> subsystems = new HashMap<String, SubsystemInformation>();
        private final String extensionModuleName;
        private XMLMapper xmlMapper;
        private ProfileParsingCompletionHandler parsingCompletionHandler;
        private volatile ExpressionResolverExtension expressionResolverExtension;

        public ExtensionInfo(String extensionModuleName) {
            this.extensionModuleName = extensionModuleName;
        }


        private SubsystemInformationImpl getSubsystemInfo(final String subsystemName) {
            checkNewSubystem(extensionModuleName, subsystemName);
            synchronized (this) {
                SubsystemInformationImpl subsystem = SubsystemInformationImpl.class.cast(subsystems.get(subsystemName));
                if (subsystem == null) {
                    subsystem = new SubsystemInformationImpl();
                    subsystems.put(subsystemName, subsystem);
                }
                return subsystem;
            }

        }
    }

    private static class DeploymentManagementResourceRegistration implements ManagementResourceRegistration {

        @Override
        public boolean isOrderedChildResource() {
            return false;
        }

        @Override
        public Set<String> getOrderedChildTypes() {
            return Collections.emptySet();
        }

        private final ManagementResourceRegistration deployments;
        private final ManagementResourceRegistration subdeployments;

        private DeploymentManagementResourceRegistration(final ManagementResourceRegistration deployments,
                                                         final ManagementResourceRegistration subdeployments) {
            this.deployments = deployments;
            this.subdeployments = subdeployments;
        }

        @Override
        public PathAddress getPathAddress() {
            return deployments.getPathAddress();
        }

        @Override
        public ProcessType getProcessType() {
            return deployments.getProcessType();
        }

        @Override
        public ImmutableManagementResourceRegistration getParent() {
            ManagementResourceRegistration deplParent = (ManagementResourceRegistration) deployments.getParent();
            ManagementResourceRegistration subParent = (ManagementResourceRegistration) subdeployments.getParent();
            if (deployments == subParent) {
                return deplParent;
            }
            return new DeploymentManagementResourceRegistration(deplParent, subParent);
        }

        @Override
        public int getMaxOccurs() {
            return deployments.getMaxOccurs();
        }

        @Override
        public int getMinOccurs() {
            return deployments.getMinOccurs();
        }

        @Override
        public boolean isRuntimeOnly() {
            return deployments.isRuntimeOnly();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setRuntimeOnly(final boolean runtimeOnly) {
            deployments.setRuntimeOnly(runtimeOnly);
            subdeployments.setRuntimeOnly(runtimeOnly);
        }


        @Override
        public boolean isRemote() {
            return deployments.isRemote();
        }

        @Override
        public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
            return deployments.getOperationHandler(address, operationName);
        }

        @Override
        public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
            return deployments.getOperationDescription(address, operationName);
        }

        @Override
        public Set<OperationEntry.Flag> getOperationFlags(PathAddress address, String operationName) {
            return deployments.getOperationFlags(address, operationName);
        }

        @Override
        public OperationEntry getOperationEntry(PathAddress address, String operationName) {
            return deployments.getOperationEntry(address, operationName);
        }

        @Override
        public Set<String> getAttributeNames(PathAddress address) {
            return deployments.getAttributeNames(address);
        }

        @Override
        public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
            return deployments.getAttributeAccess(address, attributeName);
        }

        @Override
        public Map<String, AttributeAccess> getAttributes(PathAddress address) {
            return deployments.getAttributes(address);
        }

        @Override
        public Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited) {
            return deployments.getNotificationDescriptions(address, inherited);
        }

        @Override
        public Set<String> getChildNames(PathAddress address) {
            return deployments.getChildNames(address);
        }

        @Override
        public Set<PathElement> getChildAddresses(PathAddress address) {
            return deployments.getChildAddresses(address);
        }

        @Override
        public DescriptionProvider getModelDescription(PathAddress address) {
            return deployments.getModelDescription(address);
        }

        @Override
        public Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited) {
            return deployments.getOperationDescriptions(address, inherited);
        }

        @Override
        public ProxyController getProxyController(PathAddress address) {
            return deployments.getProxyController(address);
        }

        @Override
        public Set<ProxyController> getProxyControllers(PathAddress address) {
            return deployments.getProxyControllers(address);
        }

        @Override
        public ManagementResourceRegistration getOverrideModel(String name) {
            return deployments.getOverrideModel(name);
        }

        @Override
        public ManagementResourceRegistration getSubModel(PathAddress address) {
            return deployments.getSubModel(address);
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return deployments.getAccessConstraints();
        }

        @Override
        public ManagementResourceRegistration registerSubModel(ResourceDefinition resourceDefinition) {
            ManagementResourceRegistration depl = deployments.registerSubModel(resourceDefinition);
            ManagementResourceRegistration subdepl = subdeployments.registerSubModel(resourceDefinition);
            return new DeploymentManagementResourceRegistration(depl, subdepl);
        }

        @Override
        public void unregisterSubModel(PathElement address) {
            deployments.unregisterSubModel(address);
            subdeployments.unregisterSubModel(address);
        }

        @Override
        public boolean isAllowsOverride() {
            return deployments.isAllowsOverride();
        }

        @Override
        public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
            ManagementResourceRegistration depl = deployments.registerOverrideModel(name, descriptionProvider);
            ManagementResourceRegistration subdepl = subdeployments.registerOverrideModel(name, descriptionProvider);
            return new DeploymentManagementResourceRegistration(depl, subdepl);
        }

        @Override
        public void unregisterOverrideModel(String name) {
            deployments.unregisterOverrideModel(name);
            subdeployments.unregisterOverrideModel(name);
        }

        @Override
        public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler) {
            registerOperationHandler(definition, handler, false);
        }

        @Override
        public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
            deployments.registerOperationHandler(definition, handler, inherited);
            subdeployments.registerOperationHandler(definition, handler, inherited);
        }

        @Override
        public void unregisterOperationHandler(String operationName) {
            deployments.unregisterOperationHandler(operationName);
            subdeployments.unregisterOperationHandler(operationName);
        }

        @Override
        public void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler, OperationStepHandler writeHandler) {
            deployments.registerReadWriteAttribute(definition, readHandler, writeHandler);
            subdeployments.registerReadWriteAttribute(definition, readHandler, writeHandler);
        }

        @Override
        public void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler) {
            deployments.registerReadOnlyAttribute(definition, readHandler);
            subdeployments.registerReadOnlyAttribute(definition, readHandler);
        }

        @Override
        public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
            deployments.registerMetric(definition, metricHandler);
            subdeployments.registerMetric(definition, metricHandler);
        }

        @Override
        public void unregisterAttribute(String attributeName) {
            deployments.unregisterAttribute(attributeName);
            subdeployments.unregisterAttribute(attributeName);
        }

        @Override
        public void registerNotification(NotificationDefinition notification, boolean inherited) {
            deployments.registerNotification(notification, inherited);
            subdeployments.registerNotification(notification, inherited);
        }

        @Override
        public void registerNotification(NotificationDefinition notification) {
            deployments.registerNotification(notification);
            subdeployments.registerNotification(notification);
        }

        @Override
        public void unregisterNotification(String notificationType) {
            deployments.unregisterNotification(notificationType);
            subdeployments.unregisterNotification(notificationType);
        }

        @Override
        public void registerProxyController(PathElement address, ProxyController proxyController) {
            deployments.registerProxyController(address, proxyController);
            subdeployments.registerProxyController(address, proxyController);
        }

        @Override
        public void unregisterProxyController(PathElement address) {
            deployments.unregisterProxyController(address);
            subdeployments.unregisterProxyController(address);
        }

        @Override
        public void registerAlias(PathElement address, AliasEntry alias) {
            deployments.registerAlias(address, alias);
            subdeployments.registerAlias(address, alias);
        }

        @Override
        public void unregisterAlias(PathElement address) {
            deployments.unregisterAlias(address);
            subdeployments.unregisterAlias(address);
        }

        @Override
        public void registerCapability(RuntimeCapability capability) {
            deployments.registerCapability(capability);
            subdeployments.registerCapability(capability);
        }

        @Override
        public void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities) {
            deployments.registerIncorporatingCapabilities(capabilities);
            subdeployments.registerIncorporatingCapabilities(capabilities);
        }

        @Override
        public void registerRequirements(Set<CapabilityReferenceRecorder> requirements) {
            deployments.registerRequirements(requirements);
            subdeployments.registerRequirements(requirements);
        }

        @Override
        public AliasEntry getAliasEntry() {
            return deployments.getAliasEntry();
        }

        @Override
        public boolean isAlias() {
            return deployments.isAlias();
        }

        @Override
        public Set<RuntimeCapability> getCapabilities() {
            return deployments.getCapabilities();
        }

        @Override
        public Set<RuntimeCapability> getIncorporatingCapabilities() {
            return deployments.getIncorporatingCapabilities();
        }

        @Override
        public Set<CapabilityReferenceRecorder> getRequirements() {
            return deployments.getRequirements();
        }

        public boolean isFeature() {
            return deployments.isFeature();
        }

        @Override
        public void registerAdditionalRuntimePackages(RuntimePackageDependency... pkgs) {
            deployments.registerAdditionalRuntimePackages(pkgs);
            subdeployments.registerAdditionalRuntimePackages(pkgs);
        }

        @Override
        public Set<RuntimePackageDependency> getAdditionalRuntimePackages() {
            return deployments.getAdditionalRuntimePackages();
        }

    }

    private static final JmxAuthorizer NO_OP_AUTHORIZER = new JmxAuthorizer() {

        @Override
        public AuthorizationResult authorize(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource target) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizerDescription getDescription() {
            return new AuthorizerDescription() {
                @Override
                public boolean isRoleBased() {
                    return false;
                }

                @Override
                public Set<String> getStandardRoles() {
                    return Collections.emptySet();
                }
            };
        }

        @Override
        public AuthorizationResult authorize(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute target) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizationResult authorizeJmxOperation(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public Set<String> getCallerRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> runAsRoles) {
            return null;
        }

        @Override
        public void setNonFacadeMBeansSensitive(boolean sensitive) {
        }

        @Override
        public boolean isNonFacadeMBeansSensitive() {
            return false;
        }
    };

    private static final class FutureExpressionResolverExtension implements ExpressionResolverExtension {

        private final Pattern expressionPattern;
        private final Supplier<ExpressionResolverExtension> delegateSupplier;
        private final boolean requireRuntimeDelegate;

        private FutureExpressionResolverExtension(Supplier<ExpressionResolverExtension> delegateSupplier, Pattern expressionPattern,
                                                 boolean requireRuntimeDelegate) {
            this.delegateSupplier = delegateSupplier;
            this.expressionPattern = expressionPattern;
            this.requireRuntimeDelegate = requireRuntimeDelegate;
        }

        @Override
        public void initialize(OperationContext context) throws OperationFailedException {
            ExpressionResolverExtension delegate = delegateSupplier.get();
            if (delegate != null) {
                delegate.initialize(context);
            } // else we have no initialization logic of our own
        }

        @Override
        public String resolveExpression(String expression, OperationContext context) {
            ExpressionResolverExtension delegate = delegateSupplier.get();
            if (delegate != null) {
                return delegate.resolveExpression(expression, context);
            } else if (expressionPattern.matcher(expression).matches()){
                if (context.getCurrentStage() == OperationContext.Stage.MODEL || requireRuntimeDelegate) {
                    throw ControllerLogger.ROOT_LOGGER.cannotResolveExpression(expression);
                }
            }
            return null;
        }
    }
}
