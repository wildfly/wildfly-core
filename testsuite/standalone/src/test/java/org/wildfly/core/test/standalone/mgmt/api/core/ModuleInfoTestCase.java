/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt.api.core;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleLoadException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE_LOADING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

/**
 * Tests of the core-service=module-loading:module-info command.
 *
 * @author <a href="mailto:msimka@redhat.com">Martin Simka</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
public class ModuleInfoTestCase extends ContainerResourceMgmtTestBase {
    private static final PathAddress RESOURCE = PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MODULE_LOADING));

    private static final String TARGET_MODULE_NAME = "org.jboss.logmanager";
    private static final String MODULES_DIR = System.getProperty("jboss.home") + File.separator + "modules";
    private static final String LAYERS_BASE = MODULES_DIR + File.separator + "system" + File.separator
            + "layers" + File.separator + "base";

    @Test
    public void testModuleInfo() throws Exception {
        /*[standalone@localhost:9990 /] /core-service=module-loading:module-info(name=org.jboss.logmanager)
        {
            "outcome" => "success",
                "result" => {
            "name" => "org.jboss.logmanager:main",
                    "main-class" => undefined,
                    "fallback-loader" => undefined,
                    "dependencies" => [
            {
                "dependency-name" => "ModuleDependency",
                    "module-name" => "javax.api:main",
                    "export-filter" => "Reject",
                    "import-filter" => "multi-path filter {exclude children of \"META-INF/\", exclude equals \"META-INF\", default accept}",
                    "optional" => false
            },
            {
                "dependency-name" => "ModuleDependency",
                    "module-name" => "org.jboss.modules:main",
                    "export-filter" => "Reject",
                    "import-filter" => "multi-path filter {exclude children of \"META-INF/\", exclude equals \"META-INF\", default accept}",
                    "optional" => false
            }
            ],
            "local-loader-class" => undefined,
                    "resource-loaders" => [
            {
                "type" => "org.jboss.modules.JarFileResourceLoader",
                    "paths" => [
                "",
                        "org/jboss/logmanager",
                        "META-INF/services",
                        "org",
                        "META-INF/maven/org.jboss.logmanager/jboss-logmanager",
                        "org/jboss",
                        "org/jboss/logmanager/errormanager",
                        "org/jboss/logmanager/formatters",
                        "META-INF",
                        "org/jboss/logmanager/filters",
                        "org/jboss/logmanager/config",
                        "META-INF/maven",
                        "org/jboss/logmanager/handlers",
                        "META-INF/maven/org.jboss.logmanager"
                ]
            },
            {
                "type" => "org.jboss.modules.NativeLibraryResourceLoader",
                    "paths" => undefined
            }
            ]
        }*/

        // load module.xml
        String identifier = TARGET_MODULE_NAME;
        Module module = loadModule(identifier);

        // run module-info operation
        ModelNode op = Util.createEmptyOperation("module-info", RESOURCE);
        op.get(NAME).set(TARGET_MODULE_NAME);
        ModelNode response = getModelControllerClient().execute(op);
        ModelNode result = validateResponse(response);

        // compare module name
        Assert.assertEquals("Unexpected name", TARGET_MODULE_NAME , result.get(NAME).asString());

        // compare dependencies
        List<ModelNode> dependencies = result.get("dependencies").asList();
        DependencySpec[] xmlDependencies = module.getDependencies();
        Map<String, ModuleDependencySpec> moduleDependencies = new HashMap<>();
        for (DependencySpec d : xmlDependencies) {
            if (d instanceof ModuleDependencySpec) {
                ModuleDependencySpec mds = (ModuleDependencySpec) d;
                moduleDependencies.put(mds.getName(), mds);
            }
        }
        int foundDependencies = 0;
        for (ModelNode d : dependencies) {
            String dependencyName = d.get("module-name").asString();
            Assert.assertTrue("Expected dependency", moduleDependencies.containsKey(dependencyName));
            foundDependencies++;
        }
        Assert.assertEquals("# of dependencies in module.xml != # of dependencies in module-info output",
                moduleDependencies.size(), foundDependencies);

        // compare local paths
        List<ModelNode> resourceLoaders = result.get("resource-loaders").asList();
        // there should be only one jar loader in org.jboss.logmanager module
        List<String> paths = new ArrayList<>();
        for (ModelNode n : resourceLoaders) {
            if (n.get("type").asString().equals("org.jboss.modules.JarFileResourceLoader")) {
                for (ModelNode node : n.get("paths").asList()) {
                    paths.add(node.asString());
                }
                break;
            }
        }
        Assert.assertTrue(module.getClassLoader().getLocalPaths().containsAll(paths));
    }

    private Module loadModule(String identifier) throws IOException, ModuleLoadException {
        File[] roots = new File[]{new File(LAYERS_BASE)};
        LocalModuleLoader moduleLoader = new LocalModuleLoader(roots);
        return moduleLoader.loadModule(identifier);
    }
}
