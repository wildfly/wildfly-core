/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.AbstractModelResource;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.ResourceProvider;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * @author Alexey Loubyansky
 */
class PatchResource extends AbstractModelResource {


    /**
     * The local model.
     */
    private final ModelNode model = new ModelNode();

    protected PatchResource(ServiceController<InstallationManager> imController) {
        this(imController, true);
    }

    protected PatchResource(ServiceController<InstallationManager> imController, boolean includeStream) {
        super.registerResourceProvider("layer", new LayerResourceProvider(imController));
        super.registerResourceProvider("addon", new AddOnResourceProvider(imController));
        if(includeStream) {
            super.registerResourceProvider("patch-stream", new PatchStreamResourceProvider(imController));
        }
        model.protect();
    }

    @Override
    public ModelNode getModel() {
        return model;
    }

    @Override
    public void writeModel(ModelNode newModel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isModelDefined() {
        return model.isDefined();
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Resource clone() {
        return this;
    }

    class LayerResourceProvider extends ElementProviderResourceProvider {

        LayerResourceProvider(ServiceController<InstallationManager> imController) {
            super(imController);
        }

        @Override
        protected Collection<? extends PatchableTarget> getChildTargets(InstalledIdentity identity) {
            return identity.getLayers();
        }
    }

    class AddOnResourceProvider extends ElementProviderResourceProvider {

        AddOnResourceProvider(ServiceController<InstallationManager> imController) {
            super(imController);
        }

        @Override
        protected Collection<? extends PatchableTarget> getChildTargets(InstalledIdentity identity) {
            return identity.getAddOns();
        }
    }

    class PatchStreamResourceProvider extends PatchingChildResourceProvider {

        private final PatchResource child;

        PatchStreamResourceProvider(ServiceController<InstallationManager> imController) {
            super(imController);
            child = new PatchResource(imController, false);
        }

        @Override
        public Resource get(String name) {
            return child;
        }

        @Override
        public Set<String> children() {
            final InstallationManager manager = getInstallationManager();
            if (manager == null) {
                return Collections.emptySet();
            }
            List<InstalledIdentity> installedIdentities;
            try {
                installedIdentities = manager.getInstalledIdentities();
            } catch (PatchingException e) {
                throw new IllegalStateException(e);
            }
            if(installedIdentities.size() == 1) {
                return Collections.singleton(installedIdentities.get(0).getIdentity().getName());
            }
            final Set<String> set = new HashSet<String>(installedIdentities.size());
            for(InstalledIdentity identity : installedIdentities) {
                set.add(identity.getIdentity().getName());
            }
            return set;
        }
    }

    abstract class PatchingChildResourceProvider implements ResourceProvider {

        protected final ServiceController<InstallationManager> imController;

        PatchingChildResourceProvider(ServiceController<InstallationManager> imController) {
            this.imController = checkNotNullParam("imController", imController);
        }

        @Override
        public boolean has(String name) {
            return children().contains(name);
        }

        @Override
        public Resource get(String name) {
            return PlaceholderResource.INSTANCE;
        }

        @Override
        public boolean hasChildren() {
            return !children().isEmpty();
        }

        @Override
        public void register(String name, Resource resource) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(String value, int index, Resource resource) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Resource remove(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceProvider clone() {
            return this;
        }

        protected InstallationManager getInstallationManager() {
            while (imController != null && imController.getState() == ServiceController.State.UP) {
                try {
                    return imController.getValue();
                } catch (IllegalStateException e) {
                    // ignore, caused by race from WFLY-3505
                }
            }
            return null;
        }
    }

    abstract class ElementProviderResourceProvider extends PatchingChildResourceProvider {

        ElementProviderResourceProvider(ServiceController<InstallationManager> imController) {
            super(imController);
        }

        protected abstract Collection<? extends PatchableTarget> getChildTargets(InstalledIdentity identity);

        @Override
        public Set<String> children() {
            final InstallationManager manager = getInstallationManager();
            if (manager == null) {
                return Collections.emptySet();
            }
            final Collection<? extends PatchableTarget> targets = getChildTargets(manager.getDefaultIdentity());
            if (targets.isEmpty()) {
                return Collections.emptySet();
            }
            if (targets.size() == 1) {
                final PatchableTarget target = targets.iterator().next();
                return Collections.singleton(target.getName());
            }
            final Set<String> names = new HashSet<String>(targets.size());
            for (PatchableTarget target : targets) {
                names.add(target.getName());
            }
            return names;
        }
    }
}
