/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.byteman;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;

/**
 * Byteman Helper to inject the reload of a server on a deployment scanner.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ServerReloadHelper extends Helper {

    public ServerReloadHelper(Rule rule) {
        super(rule);
        openTrace("ServerLoaderhelper", "target" + File.separatorChar + " byteman.log");
        traceln("ServerLoaderhelper", "ServerReloadHelper loaded.");
        System.out.println("ServerReloadHelper loaded.");
    }

    public void shutdownServer(Object deploymentOperations) {
        System.out.println("Trying to shutdown server.");
        traceln("ServerLoaderhelper", "Trying to shutdown server.");
        try {
            executeOperation(deploymentOperations, "shutdown");
        } catch (Exception ex) {
            trace(ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void reloadServer(Object deploymentOperations) {
        System.out.println("Trying to reload server.");
        traceln("ServerLoaderhelper", "Trying to reload server.");
        try {
            traceln("ServerLoaderhelper", "deploymentOperations are " + deploymentOperations);
            if (deploymentOperations != null) {
                traceln("ServerLoaderhelper", "deploymentOperations class is " + deploymentOperations.getClass().getSimpleName());
                if ("DefaultDeploymentOperations".equals(deploymentOperations.getClass().getSimpleName())) {
                    executeOperation(deploymentOperations, "reload");
                }
            }
            delay(100);
        } catch (Exception ex) {
            traceln("ServerLoaderhelper", ex.getMessage());
            ex.printStackTrace();
        }

    }

    private void executeOperation(Object deploymentOperations, String operation) throws Exception {
        Class defaultBootModuleLoaderHolder = this.getClass().getClassLoader().loadClass("org.jboss.modules.DefaultBootModuleLoaderHolder");
        Field instance = defaultBootModuleLoaderHolder.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        ModuleLoader loader = (ModuleLoader) instance.get(null);
        Module module = loader.loadModule(ModuleIdentifier.fromString("org.jboss.as.deployment-scanner"));
        traceln("ServerLoaderhelper", "Module " + module.toString() + " loaded.");
        ModuleClassLoader cl = module.getClassLoader();
        Class modelNodeClass = cl.loadClassLocal("org.jboss.dmr.ModelNode");
        traceln("ServerLoaderhelper", "org.jboss.dmr.ModelNode class loaded.");
        Object modelNode = modelNodeClass.newInstance();
        Method getMethod = modelNodeClass.getDeclaredMethod("get", String.class);
        Method setMethod = modelNodeClass.getDeclaredMethod("set", String.class);
        Method setAddressMethod = modelNodeClass.getDeclaredMethod("set", modelNodeClass);
        Method setEmptyListMethod = modelNodeClass.getDeclaredMethod("setEmptyList");
        Object operationNode = getMethod.invoke(modelNode, "operation");
        setMethod.invoke(operationNode, operation);
        Object operationAddressNode = getMethod.invoke(modelNode, "address");
        Object addressNode = modelNodeClass.newInstance();
        setEmptyListMethod.invoke(addressNode);
        setAddressMethod.invoke(operationAddressNode, addressNode);
        traceln("ServerLoaderhelper", "We have computed the following operation " + modelNode.toString());
        for (Method deployMethod : deploymentOperations.getClass().getDeclaredMethods()) {
            if ("deploy".equals(deployMethod.getName())) {
                deployMethod.setAccessible(true);
                traceln("ServerLoaderhelper", "We have found the execution method.");
                deployMethod.invoke(deploymentOperations, modelNode, null);
                traceln("ServerLoaderhelper", "We have executed the following operation: " + modelNode.toString());
            }
        }
    }
}
