/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.subsystem.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.model.test.ModelTestModelControllerService;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.model.test.StringConfigurationPersister;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.DeployerChainAddHandler;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironment.LaunchType;
import org.jboss.as.server.controller.resources.ServerDeploymentResourceDefinition;
import org.jboss.as.subsystem.test.ControllerInitializer.TestControllerAccessor;
import org.jboss.dmr.ModelNode;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Tomaz Cerar
 */
class TestModelControllerService extends ModelTestModelControllerService implements TestControllerAccessor {
    private final Extension mainExtension;
    private final AdditionalInitialization additionalInit;
    private final ControllerInitializer controllerInitializer;
    private final ExtensionRegistry extensionRegistry;
    private final RunningModeControl runningModeControl;
    private final ContentRepository contentRepository = new MockContentRepository();
    private final boolean registerTransformers;

    protected TestModelControllerService(final Extension mainExtension, final ControllerInitializer controllerInitializer,
                                         final AdditionalInitialization additionalInit, final RunningModeControl runningModeControl,
                                         final ExtensionRegistry extensionRegistry, final StringConfigurationPersister persister,
                                         final ModelTestOperationValidatorFilter validateOpsFilter, final boolean registerTransformers) {
        super(additionalInit.getProcessType(), runningModeControl, extensionRegistry.getTransformerRegistry(), persister, validateOpsFilter,
                new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE) , new ControlledProcessState(true), Controller90x.INSTANCE);
        this.mainExtension = mainExtension;
        this.additionalInit = additionalInit;
        this.controllerInitializer = controllerInitializer;
        this.extensionRegistry = extensionRegistry;
        this.runningModeControl = runningModeControl;
        this.registerTransformers = registerTransformers;

    }

    protected TestModelControllerService(final Extension mainExtension, final ControllerInitializer controllerInitializer,
                                            final AdditionalInitialization additionalInit, final RunningModeControl runningModeControl,
                                            final ExtensionRegistry extensionRegistry, final StringConfigurationPersister persister,
                                            final ModelTestOperationValidatorFilter validateOpsFilter, final boolean registerTransformers,
                                            final ExpressionResolver expressionResolver, final CapabilityRegistry capabilityRegistry) {
           super(additionalInit.getProcessType(), runningModeControl, extensionRegistry.getTransformerRegistry(), persister, validateOpsFilter,
                   new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE) , expressionResolver, new ControlledProcessState(true),capabilityRegistry);
           this.mainExtension = mainExtension;
           this.additionalInit = additionalInit;
           this.controllerInitializer = controllerInitializer;
           this.extensionRegistry = extensionRegistry;
           if (expressionResolver instanceof ExpressionResolver.ResolverExtensionRegistry) {
               extensionRegistry.setResolverExtensionRegistry((ExpressionResolver.ResolverExtensionRegistry) expressionResolver);
           }
           this.runningModeControl = runningModeControl;
           this.registerTransformers = registerTransformers;

       }

    static TestModelControllerService create(final Extension mainExtension, final ControllerInitializer controllerInitializer,
                                             final AdditionalInitialization additionalInit, final ExtensionRegistry extensionRegistry,
                                             final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
                                             final boolean registerTransformers, final ExpressionResolver expressionResolver, final CapabilityRegistry capabilityRegistry) {
        return new TestModelControllerService(mainExtension, controllerInitializer, additionalInit,
                new RunningModeControl(additionalInit.getRunningMode()), extensionRegistry, persister, validateOpsFilter,
                registerTransformers, expressionResolver, capabilityRegistry);
    }

    @Override
    protected ModelControllerServiceInitializationParams getModelControllerServiceInitializationParams() {
        if (additionalInit.getProcessType().isServer()) {
            final ServiceLoader<ModelControllerServiceInitialization> sl = ServiceLoader.load(ModelControllerServiceInitialization.class);
            return new ModelControllerServiceInitializationParams(sl) {
                @Override
                public String getHostName() {
                    return null;
                }
            };
        }
        return null;
    }

    @Override
    protected void initExtraModel(ManagementModel managementModel) {
        Resource rootResource = managementModel.getRootResource();
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        initExtraModelInternal(rootResource, rootRegistration);
        additionalInit.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration,
                managementModel.getCapabilityRegistry());
        rootRegistration.registerOperationHandler(TransformerAttachmentGrabber.DESC, new TransformerAttachmentGrabber());
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(managementModel.getRootResourceRegistration(), processType);
    }

    /** @deprecated only for legacy version support */
    @Override
    protected void initExtraModel(Resource rootResource, ManagementResourceRegistration rootRegistration) {
        initExtraModelInternal(rootResource, rootRegistration);
        additionalInit.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration);
    }

    private void initExtraModelInternal(Resource rootResource, ManagementResourceRegistration rootRegistration) {
        rootResource.getModel().get(SUBSYSTEM);

        rootRegistration.registerSubModel(ServerDeploymentResourceDefinition.create(contentRepository, null));

        controllerInitializer.setTestModelControllerAccessor(this);
        controllerInitializer.initializeModel(rootResource, rootRegistration);
    }

    @Override
    protected void preBoot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) {
        mainExtension.initialize(extensionRegistry.getExtensionContext("Test", getRootRegistration(),
                registerTransformers ? ExtensionRegistryType.MASTER : ExtensionRegistryType.SLAVE));
    }

    protected void postBoot() {
        DeployerChainAddHandler.INSTANCE.clearDeployerMap();
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                delete(child);
            }
        }
        file.delete();
    }

    public ServerEnvironment getServerEnvironment() {
        Properties props = new Properties();
        File home = new File("target/jbossas");
        delete(home);
        home.mkdir();
        props.put(ServerEnvironment.HOME_DIR, home.getAbsolutePath());

        File standalone = new File(home, "standalone");
        standalone.mkdir();
        props.put(ServerEnvironment.SERVER_BASE_DIR, standalone.getAbsolutePath());

        File configuration = new File(standalone, "configuration");
        configuration.mkdir();
        props.put(ServerEnvironment.SERVER_CONFIG_DIR, configuration.getAbsolutePath());

        File xml = new File(configuration, "standalone.xml");
        try {
            xml.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        props.put(ServerEnvironment.JBOSS_SERVER_DEFAULT_CONFIG, "standalone.xml");

        return new ServerEnvironment(null, props, new HashMap<String, String>(), "standalone.xml", null, LaunchType.STANDALONE, runningModeControl.getRunningMode(), null, false);
    }

    private static class MockContentRepository implements ContentRepository {

        @Override
        public byte[] addContent(InputStream stream) throws IOException {
            return null;
        }

        @Override
        public VirtualFile getContent(byte[] hash) {
            return null;
        }

        @Override
        public boolean hasContent(byte[] hash) {
            return false;
        }

        @Override
        public boolean syncContent(ContentReference reference) {
            return false;
        }

        @Override
        public void removeContent(ContentReference reference) {
        }

        @Override
        public void addContentReference(ContentReference reference) {
        }

        @Override
        public Map<String, Set<String>> cleanObsoleteContent() {
            return null;
        }
    }
}
