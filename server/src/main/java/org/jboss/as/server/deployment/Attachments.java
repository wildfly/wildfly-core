/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.lang.ref.Reference;
import java.security.PermissionCollection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.annotation.AnnotationIndexSupport;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.AdditionalModuleSpecification;
import org.jboss.as.server.deployment.module.ExtensionInfo;
import org.jboss.as.server.deployment.module.ExtensionListEntry;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.as.server.deployment.reflect.ProxyMetadataSource;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.as.server.moduleservice.ExternalModule;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.jandex.Index;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Attachments {

    //
    // GENERAL
    //
    /**
     * A list of service dependencies that must be satisfied before the next deployment phase can begin executing.
     */
    public static final AttachmentKey<AttachmentList<ServiceName>> NEXT_PHASE_DEPS = AttachmentKey.createList(ServiceName.class);
    /**
     * A list of service dependencies that must be satisfied before the next deployment phase can begin executing.
     */
    public static final AttachmentKey<AttachmentList<AttachableDependency>> NEXT_PHASE_ATTACHABLE_DEPS = AttachmentKey.createList(AttachableDependency.class);

    /**
     * A set of subsystem names that should not be processed. Any subsystem whos name is in this list will not have
     * its deployment unit processors run.
     */
    public static final AttachmentKey<Set<String>> EXCLUDED_SUBSYSTEMS = AttachmentKey.create(Set.class);

    /**
     * Set of subsystems who register deployment unit processors to at least run in one deployment phase.
     */
    public static final AttachmentKey<Set<String>> REGISTERED_SUBSYSTEMS = AttachmentKey.create(Set.class);

    /**
     * The name that uniquely identifies the deployment to the management layer across the domain.
     */
    public static final AttachmentKey<String> MANAGEMENT_NAME = AttachmentKey.create(String.class);

    /**
     * The deployment contents
     */
    public static final AttachmentKey<VirtualFile> DEPLOYMENT_CONTENTS = AttachmentKey.create(VirtualFile.class);

    /**
     * An attachment defining a transformer of the ServiceTarget used to install a deployment unit phase.
     */
    public static final AttachmentKey<UnaryOperator<ServiceTarget>> DEPLOYMENT_UNIT_PHASE_SERVICE_TARGET_TRANSFORMER = AttachmentKey.create(UnaryOperator.class);

    /**
     * A function which will be used to expand expressions within spec descriptors
     */
    public static final AttachmentKey<Function<String, String>> SPEC_DESCRIPTOR_EXPR_EXPAND_FUNCTION = AttachmentKey.create(Function.class);

    /**
     * A function which will be used to expand expressions within JBoss/WildFly (vendor specific) descriptors
     */
    public static final AttachmentKey<Function<String, String>> WFLY_DESCRIPTOR_EXPR_EXPAND_FUNCTION = AttachmentKey.create(Function.class);

    /**
     * Functions that can be used to resolve expressions found in deployment resources.
     * Functions that cannot resolve a particular input must return null.
     */
    public static final AttachmentKey<AttachmentList<Function<String, String>>>
            DEPLOYMENT_EXPRESSION_RESOLVERS = AttachmentKey.createList(Function.class);

    //
    // STRUCTURE
    //

    public static final AttachmentKey<PathManager> PATH_MANAGER = AttachmentKey.create(PathManager.class);

    public static final AttachmentKey<DeploymentOverlayIndex> DEPLOYMENT_OVERLAY_INDEX = AttachmentKey.create(DeploymentOverlayIndex.class);
    /**
     * The primary deployment root.
     */
    public static final AttachmentKey<ResourceRoot> DEPLOYMENT_ROOT = AttachmentKey.create(ResourceRoot.class);
    /**
     * Information used to build up the deployments Module
     */
    public static final AttachmentKey<ModuleSpecification> MODULE_SPECIFICATION = AttachmentKey.create(ModuleSpecification.class);
    /**
     * The additional resource roots of the deployment unit.
     */
    public static final AttachmentKey<AttachmentList<ResourceRoot>> RESOURCE_ROOTS = AttachmentKey.createList(ResourceRoot.class);
    /**
     * The MANIFEST.MF of the deployment unit.
     */
    public static final AttachmentKey<Manifest> MANIFEST = AttachmentKey.create(Manifest.class);

    /**
     * Resource roots for additional modules referenced via Class-Path.
     * <p/>
     * These are attached to the resource root that actually defined the class path entry, and are used to transitively resolve
     * the annotation index for class path items.
     */
    public static final AttachmentKey<AttachmentList<ResourceRoot>> CLASS_PATH_RESOURCE_ROOTS = AttachmentKey.createList(ResourceRoot.class);

    /**
     * The list of extensions given in the manifest and structure configurations.
     */
    public static final AttachmentKey<AttachmentList<ExtensionListEntry>> EXTENSION_LIST_ENTRIES = AttachmentKey.createList(ExtensionListEntry.class);
    /**
     * Information about extensions in a jar library deployment.
     */
    public static final AttachmentKey<ExtensionInfo> EXTENSION_INFORMATION = AttachmentKey.create(ExtensionInfo.class);

    /**
     * The server deployment repository
     */
    public static final AttachmentKey<DeploymentMountProvider> SERVER_DEPLOYMENT_REPOSITORY = AttachmentKey.create(DeploymentMountProvider.class);

    /**
     * An annotation index for a (@link ResourceRoot). This is attached to the {@link ResourceRoot}s of the deployment that contain
     * the annotations
     */
    public static final AttachmentKey<Index> ANNOTATION_INDEX = AttachmentKey.create(Index.class);

    /**
     * A reference to a support utility object for processing annotation indices. This is attached to the {@link DeploymentUnit} for
     * a top-level deployment and any subdeployments. A {@link Reference} holds the support object so it can be
     * garbage collected once the management operation that created it completes.
     */
    public static final AttachmentKey<Reference<AnnotationIndexSupport>> ANNOTATION_INDEX_SUPPORT = AttachmentKey.create(Reference.class);

    /**
     * The composite annotation index for this deployment.
     */
    public static final AttachmentKey<CompositeIndex> COMPOSITE_ANNOTATION_INDEX = AttachmentKey.create(CompositeIndex.class);

    /**
     * Flag to indicate whether to compute the composite annotation index for this deployment.  Absence of this flag will
     * be cause the composite index to be attached.
     */
    public static final AttachmentKey<Boolean> COMPUTE_COMPOSITE_ANNOTATION_INDEX = AttachmentKey.create(Boolean.class);

    /**
     * An attachment that indicates if a {@link ResourceRoot} should be indexed by the {@link org.jboss.as.server.deployment.annotation.AnnotationIndexProcessor}. If this
     * is not present then the resource root is indexed by default.
     */
    public static final AttachmentKey<Boolean> INDEX_RESOURCE_ROOT = AttachmentKey.create(Boolean.class);

     /**
     * A list of paths within a root to ignore when indexing.
     */
    public static final AttachmentKey<AttachmentList<String>> INDEX_IGNORE_PATHS = AttachmentKey.createList(String.class);

    /**
     * Sub deployment services
     */
    public static final AttachmentKey<AttachmentList<DeploymentUnit>> SUB_DEPLOYMENTS = AttachmentKey.createList(DeploymentUnit.class);
    /**
     * Additional modules attached to the top level deployment
     */
    public static final AttachmentKey<AttachmentList<AdditionalModuleSpecification>> ADDITIONAL_MODULES = AttachmentKey.createList(AdditionalModuleSpecification.class);

    /**
     * A list of modules for which annotation indexes should be prepared (or, in later phases, have been prepared).
     *
     * @deprecated use {@link #ADDITIONAL_INDEX_MODULES}
     */
    @Deprecated(forRemoval = true)
    public static final AttachmentKey<AttachmentList<ModuleIdentifier>> ADDITIONAL_ANNOTATION_INDEXES = AttachmentKey.createList(ModuleIdentifier.class);

    /**
     * A list of modules for which annotation indexes should be prepared (or, in later phases, have been prepared).
     *
     */
    public static final AttachmentKey<AttachmentList<String>> ADDITIONAL_INDEX_MODULES = AttachmentKey.createList(String.class);

    /**
     * Annotation indices, keyed by the identifier of the module from which they were obtained.
     *
     * @deprecated use {@link #ADDITIONAL_ANNOTATION_INDEXES_BY_MODULE_NAME}
     */
    @Deprecated(forRemoval = true)
    public static final AttachmentKey<Map<ModuleIdentifier, CompositeIndex>> ADDITIONAL_ANNOTATION_INDEXES_BY_MODULE = AttachmentKey.create(Map.class);

    /**
     * Annotation indices, keyed by the canonical name module from which they were obtained.
     */
    public static final AttachmentKey<Map<String, CompositeIndex>> ADDITIONAL_ANNOTATION_INDEXES_BY_MODULE_NAME = AttachmentKey.create(Map.class);

    public static final AttachmentKey<Map<String, MountedDeploymentOverlay>> DEPLOYMENT_OVERLAY_LOCATIONS = AttachmentKey.create(Map.class);

    /**
     * Support for getting and creating resource models on a deployment's resource.
     */
    public static final AttachmentKey<DeploymentResourceSupport> DEPLOYMENT_RESOURCE_SUPPORT = AttachmentKey.create(DeploymentResourceSupport.class);

    /**
     * Support for integrating with services and other runtime API provided by managed capabilities.
     */
    public static final AttachmentKey<CapabilityServiceSupport> CAPABILITY_SERVICE_SUPPORT = AttachmentKey.create(CapabilityServiceSupport.class);

    /**
     * A service target that can be used to install services outside the scope of the deployment.
     * <p/>
     * These services will not be removed automatically on undeploy, so if this is used some other strategy must be used
     * to handle undeployment.
     */
    public static final AttachmentKey<ServiceTarget> EXTERNAL_SERVICE_TARGET = AttachmentKey.create(ServiceTarget.class);

    //
    // VALIDATE
    //

    //
    // PARSE
    //

    //
    // REGISTER
    //

    //
    // DEPENDENCIES
    //
    public static final AttachmentKey<AttachmentList<ModuleDependency>> MANIFEST_DEPENDENCIES = AttachmentKey.createList(ModuleDependency.class);

    //
    // CONFIGURE
    //
    /**
     * The module identifier.
     *
     * @deprecated use {@link #MODULE_NAME}
     */
    @Deprecated(forRemoval = true)
    public static final AttachmentKey<ModuleIdentifier> MODULE_IDENTIFIER = AttachmentKey.create(ModuleIdentifier.class);

    /**
     * The canonical name of the module.
     */
    public static final AttachmentKey<String> MODULE_NAME = AttachmentKey.create(String.class);

    //
    // MODULARIZE
    //

    /**
     * The module of this deployment unit.
     */
    public static final AttachmentKey<Module> MODULE = AttachmentKey.create(Module.class);

    /**
     * The module loader for the deployment
     */
    public static final AttachmentKey<ServiceModuleLoader> SERVICE_MODULE_LOADER  = AttachmentKey.create(ServiceModuleLoader.class);

    /**
     * The external module service
     */
    public static final AttachmentKey<ExternalModule> EXTERNAL_MODULE_SERVICE  = AttachmentKey.create(ExternalModule.class);

    /**
     * An index of {@link java.util.ServiceLoader}-type services in this deployment unit
     */
    public static final AttachmentKey<ServicesAttachment> SERVICES = AttachmentKey.create(ServicesAttachment.class);

    /**
     * Sub deployments that are visible from this deployments module loader, in the order they are accessible.
     * <p/>
     * This list includes the current deployment, which under normal circumstances will be the first item in the list
     */
    public static final AttachmentKey<AttachmentList<DeploymentUnit>> ACCESSIBLE_SUB_DEPLOYMENTS = AttachmentKey.createList(DeploymentUnit.class);

    /**
     * The module's permission collection
     */
    public static final AttachmentKey<PermissionCollection> MODULE_PERMISSIONS = AttachmentKey.create(PermissionCollection.class);

    //
    // POST_MODULE
    //

    //
    // INSTALL
    //

    /**
     * A list of services that a web deployment should have as dependencies.
     */
    public static final AttachmentKey<AttachmentList<ServiceName>> WEB_DEPENDENCIES = AttachmentKey.createList(ServiceName.class);

    /**
     * JNDI dependencies, only attached to the top level deployment
     */
    public static final AttachmentKey<AttachmentList<ServiceName>> JNDI_DEPENDENCIES = AttachmentKey.createList(ServiceName.class);

    /**
     * Component JNDI dependencies, only attached to the top level deployment
     */
    public static final AttachmentKey<Map<ServiceName, Set<ServiceName>>> COMPONENT_JNDI_DEPENDENCIES = AttachmentKey.create(Map.class);

    /**
     * The reflection index for the deployment.
     */
    public static final AttachmentKey<DeploymentReflectionIndex> REFLECTION_INDEX = AttachmentKey.create(DeploymentReflectionIndex.class);

    /**
     * The reflection index used to generate jboss-invocation proxies
     */
    public static final AttachmentKey<ProxyMetadataSource> PROXY_REFLECTION_INDEX = AttachmentKey.create(ProxyMetadataSource.class);

    /**
     * Setup actions that must be run before running an arquillian test
     */
    public static final AttachmentKey<AttachmentList<SetupAction>> SETUP_ACTIONS = AttachmentKey.createList(SetupAction.class);

    /**
     * List of services that need to be up before we consider this deployment 'done'. This is used to manage initialize-in-order,
     * and inter deployment dependencies.
     *
     * It would better if this could be handled by MSC without needing to add all these into a list manually
     */
    public static final AttachmentKey<AttachmentList<ServiceName>> DEPLOYMENT_COMPLETE_SERVICES = AttachmentKey.createList(ServiceName.class);

    //
    // CLEANUP
    //

    private Attachments() {
    }
}
