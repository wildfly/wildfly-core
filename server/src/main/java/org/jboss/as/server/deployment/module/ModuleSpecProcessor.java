/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.IOException;
import java.security.Permission;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.function.Consumer;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.moduleservice.ModuleDefinition;
import org.jboss.as.server.moduleservice.ModuleLoadService;
import org.jboss.as.server.moduleservice.ModuleResolvePhaseService;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleDependencySpecBuilder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.modules.security.FactoryPermissionCollection;
import org.jboss.modules.security.ImmediatePermissionFactory;
import org.jboss.modules.security.PermissionFactory;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFilePermission;

/**
 * Processor responsible for creating the module spec service for this deployment. Once the module spec service is created the
 * module can be loaded by {@link ServiceModuleLoader}.
 *
 * @author John Bailey
 * @author Stuart Douglas
 * @author Marius Bogoevici
 * @author Thomas.Diesler@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ModuleSpecProcessor implements DeploymentUnitProcessor {

    private static final ServerLogger logger = ServerLogger.DEPLOYMENT_LOGGER;

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.hasAttachment(Attachments.MODULE))
            return;
        deployModuleSpec(phaseContext);
    }

    @Override
    public void undeploy(final DeploymentUnit deploymentUnit) {
        deploymentUnit.removeAttachment(Attachments.MODULE);
        deploymentUnit.removeAttachment(Attachments.MODULE_PERMISSIONS);
        deploymentUnit.removeAttachment(DelegatingClassTransformer.ATTACHMENT_KEY);
    }

    private void deployModuleSpec(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit parentDeployment = deploymentUnit.getParent();

        final ResourceRoot mainRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        if (mainRoot == null)
            return;

        // Add internal resource roots
        final ModuleSpecification moduleSpec = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final List<ResourceRoot> resourceRoots = new ArrayList<ResourceRoot>();
        if (ModuleRootMarker.isModuleRoot(mainRoot)) {
            resourceRoots.add(mainRoot);
        }
        final List<ResourceRoot> additionalRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (final ResourceRoot additionalRoot : additionalRoots) {
            if (ModuleRootMarker.isModuleRoot(additionalRoot) && !SubDeploymentMarker.isSubDeployment(additionalRoot)) {
                resourceRoots.add(additionalRoot);
            }
        }

        final List<ResourceRoot> parentResourceRoots = new ArrayList<>();
        if (parentDeployment != null) {
            final List<ResourceRoot> additionalParentRoots = parentDeployment.getAttachmentList(Attachments.RESOURCE_ROOTS);
            for (final ResourceRoot additionalParentRoot : additionalParentRoots) {
                if (ModuleRootMarker.isModuleRoot(additionalParentRoot) && !SubDeploymentMarker.isSubDeployment(additionalParentRoot)) {
                    parentResourceRoots.add(additionalParentRoot);
                }
            }
        }

        final String moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_NAME);
        if (moduleIdentifier == null) {
            throw ServerLogger.ROOT_LOGGER.noModuleIdentifier(deploymentUnit.getName());
        }

        // create the module service and set it to attach to the deployment in the next phase
        final ServiceName moduleServiceName = createModuleService(phaseContext, deploymentUnit, resourceRoots, parentResourceRoots, moduleSpec, moduleIdentifier);
        phaseContext.addDeploymentDependency(moduleServiceName, Attachments.MODULE);

        for (final DeploymentUnit subDeployment : deploymentUnit.getAttachmentList(Attachments.SUB_DEPLOYMENTS)) {
            ModuleIdentifier moduleId = subDeployment.getAttachment(Attachments.MODULE_IDENTIFIER);
            if (moduleId != null) {
                phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, ServiceModuleLoader.moduleSpecServiceName(moduleId.toString()));
            }
        }

        if (parentDeployment == null) {
            //only top level deployment adds additional modules
            final List<AdditionalModuleSpecification> additionalModules = deploymentUnit.getAttachmentList(Attachments.ADDITIONAL_MODULES);
            for (final AdditionalModuleSpecification module : additionalModules) {
                addAllDependenciesAndPermissions(moduleSpec, module);
                List<ResourceRoot> roots = module.getResourceRoots();
                ServiceName serviceName = createModuleService(phaseContext, deploymentUnit, roots, parentResourceRoots, module, module.getModuleName());
                phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, serviceName);
            }
        }
    }

    /**
     * Gives any additional modules the same dependencies and permissions as the primary module.
     * <p/>
     * This makes sure they can access all API classes etc.
     *
     * @param moduleSpecification The primary module spec
     * @param module              The additional module
     */
    private void addAllDependenciesAndPermissions(final ModuleSpecification moduleSpecification, final AdditionalModuleSpecification module) {
        module.addSystemDependencies(moduleSpecification.getSystemDependenciesSet());
        module.addLocalDependencies(moduleSpecification.getLocalDependenciesSet());
        for(ModuleDependency dep : moduleSpecification.getUserDependenciesSet()) {
            if(!dep.getDependencyModule().equals(module.getModuleName())) {
                module.addUserDependency(dep);
            }
        }
        for(PermissionFactory factory : moduleSpecification.getPermissionFactories()) {
            module.addPermissionFactory(factory);
        }
    }

    private static final Permissions DEFAULT_PERMISSIONS;

    static {
        final Permissions permissions = new Permissions();
        permissions.add(new PropertyPermission("file.encoding", "read"));
        permissions.add(new PropertyPermission("file.separator", "read"));
        permissions.add(new PropertyPermission("java.class.version", "read"));
        permissions.add(new PropertyPermission("java.specification.version", "read"));
        permissions.add(new PropertyPermission("java.specification.vendor", "read"));
        permissions.add(new PropertyPermission("java.specification.name", "read"));
        permissions.add(new PropertyPermission("java.vendor", "read"));
        permissions.add(new PropertyPermission("java.vendor.url", "read"));
        permissions.add(new PropertyPermission("java.version", "read"));
        permissions.add(new PropertyPermission("java.vm.name", "read"));
        permissions.add(new PropertyPermission("java.vm.vendor", "read"));
        permissions.add(new PropertyPermission("java.vm.version", "read"));
        permissions.add(new PropertyPermission("line.separator", "read"));
        permissions.add(new PropertyPermission("os.name", "read"));
        permissions.add(new PropertyPermission("os.version", "read"));
        permissions.add(new PropertyPermission("os.arch", "read"));
        permissions.add(new PropertyPermission("path.separator", "read"));
        // these permissions are apparently non-standard, but there is no reason not to make them available if the above are
        permissions.add(new PropertyPermission("java.runtime.name", "read"));
        permissions.add(new PropertyPermission("java.runtime.version", "read"));
        permissions.add(new PropertyPermission("java.vendor.url.bug", "read"));
        permissions.add(new PropertyPermission("java.vm.info", "read"));
        permissions.add(new PropertyPermission("java.vm.specification.name", "read"));
        permissions.add(new PropertyPermission("java.vm.specification.vendor", "read"));
        permissions.add(new PropertyPermission("java.vm.specification.version", "read"));
        permissions.add(new PropertyPermission("sun.cpu.endian", "read"));
        permissions.add(new PropertyPermission("sun.cpu.isalist", "read"));
        permissions.add(new PropertyPermission("sun.management.compiler", "read"));
        permissions.setReadOnly();
        DEFAULT_PERMISSIONS = permissions;
    }

    private ServiceName createModuleService(final DeploymentPhaseContext phaseContext, final DeploymentUnit deploymentUnit,
                                            final List<ResourceRoot> resourceRoots, final List<ResourceRoot> parentResourceRoots,
                                            final ModuleSpecification moduleSpecification, final String moduleIdentifier) throws DeploymentUnitProcessingException {
        logger.debugf("Creating module: %s", moduleIdentifier);
        final ModuleSpec.Builder specBuilder = ModuleSpec.build(moduleIdentifier);
        for (final DependencySpec dep : moduleSpecification.getModuleSystemDependencies()) {
            specBuilder.addDependency(dep);
        }
        final Set<ModuleDependency> dependencies = moduleSpecification.getSystemDependenciesSet();
        final Set<ModuleDependency> localDependencies = moduleSpecification.getLocalDependenciesSet();
        final Set<ModuleDependency> userDependencies = moduleSpecification.getUserDependenciesSet();

        final List<PermissionFactory> permFactories = moduleSpecification.getPermissionFactories();

        installAliases(moduleSpecification, moduleIdentifier, deploymentUnit, phaseContext);

        // add additional resource loaders first
        for (final ResourceLoaderSpec resourceLoaderSpec : moduleSpecification.getResourceLoaders()) {
            logger.debugf("Adding resource loader %s to module %s", resourceLoaderSpec, moduleIdentifier);
            specBuilder.addResourceRoot(resourceLoaderSpec);
        }

        for (final ResourceRoot resourceRoot : resourceRoots) {
            logger.debugf("Adding resource %s to module %s", resourceRoot.getRoot(), moduleIdentifier);
            addResourceRoot(specBuilder, resourceRoot, permFactories);
        }

        createDependencies(specBuilder, dependencies, false);
        createDependencies(specBuilder, userDependencies, false);

        if (moduleSpecification.isLocalLast()) {
            createDependencies(specBuilder, localDependencies, moduleSpecification.isLocalDependenciesTransitive());
            specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        } else {
            specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
            createDependencies(specBuilder, localDependencies, moduleSpecification.isLocalDependenciesTransitive());
        }

        final Enumeration<Permission> e = DEFAULT_PERMISSIONS.elements();
        while (e.hasMoreElements()) {
            permFactories.add(new ImmediatePermissionFactory(e.nextElement()));
        }
        // TODO: servlet context temp dir FilePermission

        // add file permissions for parent roots
        for (ResourceRoot additionalParentRoot : parentResourceRoots) {
            final VirtualFile root = additionalParentRoot.getRoot();
            // start with the root
            permFactories.add(new ImmediatePermissionFactory(
                    new VirtualFilePermission(root.getPathName(), VirtualFilePermission.FLAG_READ)));
            // also include all children, recursively
            permFactories.add(new ImmediatePermissionFactory(
                    new VirtualFilePermission(root.getChild("-").getPathName(), VirtualFilePermission.FLAG_READ)));
        }

        FactoryPermissionCollection permissionCollection = new FactoryPermissionCollection(permFactories.toArray(new PermissionFactory[permFactories.size()]));

        specBuilder.setPermissionCollection(permissionCollection);
        deploymentUnit.putAttachment(Attachments.MODULE_PERMISSIONS, permissionCollection);

        final DelegatingClassTransformer delegatingClassTransformer = new DelegatingClassTransformer();
        specBuilder.setClassFileTransformer(delegatingClassTransformer);
        deploymentUnit.putAttachment(DelegatingClassTransformer.ATTACHMENT_KEY, delegatingClassTransformer);
        final ModuleSpec moduleSpec = specBuilder.create();
        final ServiceName moduleSpecServiceName = ServiceModuleLoader.moduleSpecServiceName(moduleIdentifier.toString());

        ModuleDefinition moduleDefinition = new ModuleDefinition(moduleIdentifier, new HashSet<>(moduleSpecification.getAllDependencies()), moduleSpec);

        final ServiceBuilder sb = phaseContext.getServiceTarget().addService(moduleSpecServiceName);
        final Consumer<ModuleDefinition> moduleDefinitionConsumer = sb.provides(moduleSpecServiceName);
        sb.requires(deploymentUnit.getServiceName());
        sb.requires(phaseContext.getPhaseServiceName());
        sb.setInitialMode(Mode.ON_DEMAND);
        sb.setInstance(new ModuleDefinitionService(moduleDefinitionConsumer, moduleDefinition));
        sb.install();

        ModuleResolvePhaseService.installService(phaseContext.getServiceTarget(), moduleDefinition);

        return ModuleLoadService.install(phaseContext.getServiceTarget(), moduleIdentifier, dependencies, localDependencies, userDependencies);
    }

    private void installAliases(final ModuleSpecification moduleSpecification, final String moduleIdentifier, final DeploymentUnit deploymentUnit, final DeploymentPhaseContext phaseContext) {

        ModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        for (final String aliasName : moduleSpecification.getModuleAliases()) {
            final ModuleIdentifier alias = ModuleIdentifier.fromString(aliasName);

            final ServiceName moduleSpecServiceName = ServiceModuleLoader.moduleSpecServiceName(alias.toString());
            final ModuleSpec spec = ModuleSpec.buildAlias(aliasName, moduleIdentifier).create();

            HashSet<ModuleDependency> dependencies = new HashSet<>(moduleSpecification.getAllDependencies());
            //we need to add the module we are aliasing as a dependency, to make sure that it will be resolved
            dependencies.add(ModuleDependency.Builder.of(moduleLoader, moduleIdentifier.toString()).build());
            ModuleDefinition moduleDefinition = new ModuleDefinition(alias, dependencies, spec);

            final ServiceBuilder sb = phaseContext.getServiceTarget().addService(moduleSpecServiceName);
            final Consumer<ModuleDefinition> moduleDefinitionConsumer = sb.provides(moduleSpecServiceName);
            sb.requires(deploymentUnit.getServiceName());
            sb.requires(phaseContext.getPhaseServiceName());
            sb.setInitialMode(Mode.ON_DEMAND);
            sb.setInstance(new ModuleDefinitionService(moduleDefinitionConsumer, moduleDefinition));
            sb.install();

            ModuleLoadService.installAliases(phaseContext.getServiceTarget(), alias, Collections.singletonList(moduleIdentifier));

            ModuleResolvePhaseService.installService(phaseContext.getServiceTarget(), moduleDefinition);
        }
    }

    private void createDependencies(final ModuleSpec.Builder specBuilder, final Collection<ModuleDependency> apiDependencies, final boolean requireTransitive) {
        if (apiDependencies != null) {
            for (final ModuleDependency dependency : apiDependencies) {
                final boolean export = requireTransitive ? true : dependency.isExport();
                final List<FilterSpecification> importFilters = dependency.getImportFilters();
                final List<FilterSpecification> exportFilters = dependency.getExportFilters();
                final PathFilter importFilter;
                final PathFilter exportFilter;
                final MultiplePathFilterBuilder importBuilder = PathFilters.multiplePathFilterBuilder(true);
                for (final FilterSpecification filter : importFilters) {
                    importBuilder.addFilter(filter.getPathFilter(), filter.isInclude());
                }
                if (dependency.isImportServices()) {
                    importBuilder.addFilter(PathFilters.getMetaInfServicesFilter(), true);
                }
                importBuilder.addFilter(PathFilters.getMetaInfSubdirectoriesFilter(), false);
                importBuilder.addFilter(PathFilters.getMetaInfFilter(), false);
                importFilter = importBuilder.create();
                if (exportFilters.isEmpty()) {
                    if (export) {
                        exportFilter = PathFilters.acceptAll();
                    } else {
                        exportFilter = PathFilters.rejectAll();
                    }
                } else {
                    final MultiplePathFilterBuilder exportBuilder = PathFilters
                            .multiplePathFilterBuilder(export);
                    for (final FilterSpecification filter : exportFilters) {
                        exportBuilder.addFilter(filter.getPathFilter(), filter.isInclude());
                    }
                    exportFilter = exportBuilder.create();
                }
                final DependencySpec depSpec = new ModuleDependencySpecBuilder()
                        .setModuleLoader(dependency.getModuleLoader())
                        .setName(dependency.getDependencyModule())
                        .setOptional(dependency.isOptional())
                        .setImportFilter(importFilter)
                        .setExportFilter(exportFilter)
                        .build();
                specBuilder.addDependency(depSpec);
                logger.debugf("Adding dependency %s to module %s", dependency, specBuilder.getIdentifier());
            }
        }
    }

    private void addResourceRoot(final ModuleSpec.Builder specBuilder, final ResourceRoot resource, final List<PermissionFactory> permFactories)
            throws DeploymentUnitProcessingException {
        try {
            final VirtualFile root = resource.getRoot();
            if (resource.getExportFilters().isEmpty()) {
                specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(new VFSResourceLoader(resource
                        .getRootName(), root, resource.isUsePhysicalCodeSource())));
            } else {
                final MultiplePathFilterBuilder filterBuilder = PathFilters.multiplePathFilterBuilder(true);
                for (final FilterSpecification filter : resource.getExportFilters()) {
                    filterBuilder.addFilter(filter.getPathFilter(), filter.isInclude());
                }
                specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(new VFSResourceLoader(resource
                        .getRootName(), root, resource.isUsePhysicalCodeSource()), filterBuilder.create()));
            }
            // start with the root
            permFactories.add(new ImmediatePermissionFactory(
                    new VirtualFilePermission(root.getPathName(), VirtualFilePermission.FLAG_READ)));
            // also include all children, recursively
            permFactories.add(new ImmediatePermissionFactory(
                    new VirtualFilePermission(root.getChild("-").getPathName(), VirtualFilePermission.FLAG_READ)));
        } catch (IOException e) {
            throw ServerLogger.ROOT_LOGGER.failedToCreateVFSResourceLoader(resource.getRootName(), e);
        }
    }

    private static final class ModuleDefinitionService implements Service {
        private final Consumer<ModuleDefinition> moduleDefinitionConsumer;
        private final ModuleDefinition moduleDefinition;

        private ModuleDefinitionService(final Consumer<ModuleDefinition> moduleDefinitionConsumer, final ModuleDefinition moduleDefinition) {
            this.moduleDefinitionConsumer = moduleDefinitionConsumer;
            this.moduleDefinition = moduleDefinition;
        }
        @Override
        public void start(final StartContext startContext) {
            moduleDefinitionConsumer.accept(moduleDefinition);
        }

        @Override
        public void stop(final StopContext stopContext) {
            moduleDefinitionConsumer.accept(null);
        }

        @Override
        public Object getValue() {
            return moduleDefinition;
        }
    }

}
