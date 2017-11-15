/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security.util;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.EnvironmentNameReader;
import org.jboss.msc.service.StabilityMonitor;

/**
 * An extension of {@link AbstractControllerTestBase} which ensures the core
 * management resources are defined.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementControllerTestBase extends AbstractControllerTestBase {

    protected volatile PathManagerService pathManagerService;
    protected volatile ManagedAuditLogger auditLogger;
    protected volatile File logDir;
    protected volatile File tmpDir;


    @Override
    protected void initModel(ManagementModel managementModel) {
        if (logDir == null){
            logDir = new File(".");
            logDir = new File(logDir, "target");
            logDir = new File(logDir, "audit-log-test-log-dir").getAbsoluteFile();
            if (!logDir.exists()){
                logDir.mkdirs();
            }
        }

        if (tmpDir == null) {
            tmpDir = new File(".");
            tmpDir = new File(tmpDir, "target");
            tmpDir = new File(tmpDir, "tmp-test-dir").getAbsoluteFile();
            if (!tmpDir.exists()){
                tmpDir.mkdirs();
            }
        }

        for (File file : logDir.listFiles()){
            file.delete();
        }
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        pathManagerService = new PathManagerService() {
            {
                super.addHardcodedAbsolutePath(getContainer(), "log.dir", logDir.getAbsolutePath());
                super.addHardcodedAbsolutePath(getContainer(), "jboss.controller.temp.dir", tmpDir.getAbsolutePath());
            }
        };
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        registration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        StabilityMonitor monitor = new StabilityMonitor();
        getContainer().addService(AbstractControllerService.PATH_MANAGER_CAPABILITY.getCapabilityServiceName(), pathManagerService)
                .addMonitor(monitor)
                .install();

        try {
            monitor.awaitStability(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        registration.registerSubModel(PathResourceDefinition.createSpecified(pathManagerService));
        registration.registerSubModel(CoreManagementResourceDefinition.forStandaloneServer(new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), getAuditLogger(), pathManagerService, new EnvironmentNameReader() {
            public boolean isServer() {
                return true;
            }

            public String getServerName() {
                return "Test";
            }

            public String getHostName() {
                return null;
            }

            public String getProductName() {
                return null;
            }
        }, null));

        Resource rootResource = managementModel.getRootResource();
        pathManagerService.addPathManagerResources(rootResource);
        rootResource.registerChild(CoreManagementResourceDefinition.PATH_ELEMENT, Resource.Factory.create());
    }

}
