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

package org.jboss.as.server.deployment.module;

import java.io.IOException;
import java.security.Permission;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Enumeration;
import java.util.List;
import java.util.PropertyPermission;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.moduleservice.ModuleDefinition;
import org.jboss.as.server.moduleservice.ModuleLoadService;
import org.jboss.as.server.moduleservice.ModuleResolvePhaseService;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.modules.security.FactoryPermissionCollection;
import org.jboss.modules.security.ImmediatePermissionFactory;
import org.jboss.modules.security.PermissionFactory;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;
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
 */
public class ModuleSpecProcessor implements DeploymentUnitProcessor {

    private static final ServerLogger logger = ServerLogger.DEPLOYMENT_LOGGER;

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.hasAttachment(Attachments.MODULE))
            return;

        // No {@link ModuleSpec} creation for OSGi deployments
        if (deploymentUnit.hasAttachment(Attachments.OSGI_MANIFEST))
            return;

        deployModuleSpec(phaseContext);
    }

    @Override
    public void undeploy(final DeploymentUnit deploymentUnit) {
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

        final ModuleIdentifier moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER);
        if (moduleIdentifier == null) {
            throw ServerLogger.ROOT_LOGGER.noModuleIdentifier(deploymentUnit.getName());
        }

        // create the module service and set it to attach to the deployment in the next phase
        final ServiceName moduleServiceName = createModuleService(phaseContext, deploymentUnit, resourceRoots, parentResourceRoots, moduleSpec, moduleIdentifier);
        phaseContext.addDeploymentDependency(moduleServiceName, Attachments.MODULE);

        for (final DeploymentUnit subDeployment : deploymentUnit.getAttachmentList(Attachments.SUB_DEPLOYMENTS)) {
            ModuleIdentifier moduleId = subDeployment.getAttachment(Attachments.MODULE_IDENTIFIER);
            if (moduleId != null) {
                phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, ServiceModuleLoader.moduleSpecServiceName(moduleId));
            }
        }

        if (parentDeployment == null) {
            //only top level deployment adds additional modules
            final List<AdditionalModuleSpecification> additionalModules = deploymentUnit.getAttachmentList(Attachments.ADDITIONAL_MODULES);
            for (final AdditionalModuleSpecification module : additionalModules) {
                addAllDependenciesAndPermissions(moduleSpec, module);
                List<ResourceRoot> roots = module.getResourceRoots();
                ServiceName serviceName = createModuleService(phaseContext, deploymentUnit, roots, parentResourceRoots, module, module.getModuleIdentifier());
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
        module.addSystemDependencies(moduleSpecification.getSystemDependencies());
        module.addLocalDependencies(moduleSpecification.getLocalDependencies());
        for(ModuleDependency dep : moduleSpecification.getUserDependencies()) {
            if(!dep.getIdentifier().equals(module.getModuleIdentifier())) {
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
                                            final ModuleSpecification moduleSpecification, final ModuleIdentifier moduleIdentifier) throws DeploymentUnitProcessingException {
        logger.debugf("Creating module: %s", moduleIdentifier);
        final ModuleSpec.Builder specBuilder = ModuleSpec.build(moduleIdentifier);
        for (final DependencySpec dep : moduleSpecification.getModuleSystemDependencies()) {
            specBuilder.addDependency(dep);
        }
        final List<ModuleDependency> dependencies = moduleSpecification.getSystemDependencies();
        final List<ModuleDependency> localDependencies = moduleSpecification.getLocalDependencies();
        final List<ModuleDependency> userDependencies = moduleSpecification.getUserDependencies();

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

        final DelegatingClassFileTransformer delegatingClassFileTransformer = new DelegatingClassFileTransformer();
        specBuilder.setClassFileTransformer(delegatingClassFileTransformer);
        deploymentUnit.putAttachment(DelegatingClassFileTransformer.ATTACHMENT_KEY, delegatingClassFileTransformer);
        final ModuleSpec moduleSpec = specBuilder.create();
        final ServiceName moduleSpecServiceName = ServiceModuleLoader.moduleSpecServiceName(moduleIdentifier);

        ModuleDefinition moduleDefinition = new ModuleDefinition(moduleIdentifier, new HashSet<>(moduleSpecification.getAllDependencies()), moduleSpec);

        final ValueService<ModuleDefinition> moduleSpecService = new ValueService<>(new ImmediateValue<>(moduleDefinition));
        phaseContext.getServiceTarget().addService(moduleSpecServiceName, moduleSpecService).addDependencies(
                deploymentUnit.getServiceName()).addDependencies(phaseContext.getPhaseServiceName()).setInitialMode(
                Mode.ON_DEMAND).install();

        final List<ModuleDependency> allDependencies = new ArrayList<ModuleDependency>();
        allDependencies.addAll(dependencies);
        allDependencies.addAll(localDependencies);
        allDependencies.addAll(userDependencies);

        ModuleResolvePhaseService.installService(phaseContext.getServiceTarget(), moduleDefinition);

        return ModuleLoadService.install(phaseContext.getServiceTarget(), moduleIdentifier, allDependencies);
    }

    private void installAliases(final ModuleSpecification moduleSpecification, final ModuleIdentifier moduleIdentifier, final DeploymentUnit deploymentUnit, final DeploymentPhaseContext phaseContext) {

        for (final ModuleIdentifier alias : moduleSpecification.getAliases()) {
            final ServiceName moduleSpecServiceName = ServiceModuleLoader.moduleSpecServiceName(alias);
            final ModuleSpec spec = ModuleSpec.buildAlias(alias, moduleIdentifier).create();

            ModuleDefinition moduleDefinition = new ModuleDefinition(alias, new HashSet<>(moduleSpecification.getAllDependencies()), spec);

            final ValueService<ModuleDefinition> moduleSpecService = new ValueService<>(new ImmediateValue<>(moduleDefinition));
            phaseContext.getServiceTarget().addService(moduleSpecServiceName, moduleSpecService).addDependencies(
                    deploymentUnit.getServiceName()).addDependencies(phaseContext.getPhaseServiceName()).setInitialMode(
                    Mode.ON_DEMAND).install();
            ModuleLoadService.installService(phaseContext.getServiceTarget(), alias, Collections.singletonList(moduleIdentifier));

            ModuleResolvePhaseService.installService(phaseContext.getServiceTarget(), moduleDefinition);
        }
    }

    private void createDependencies(final ModuleSpec.Builder specBuilder, final List<ModuleDependency> apiDependencies, final boolean requireTransitive) {
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
                final DependencySpec depSpec = DependencySpec.createModuleDependencySpec(importFilter, exportFilter, dependency
                        .getModuleLoader(), dependency.getIdentifier(), dependency.isOptional());
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

}
