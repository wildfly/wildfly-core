/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;

/**
 * The common attribute definitions for specifying classes to be loaded by the subsystem.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ClassLoadingAttributeDefinitions {

    static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODULE, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.CLASS_LOADING)
        .setAllowExpression(false)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition CLASS_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CLASS_NAME, ModelType.STRING, false)
        .setAttributeGroup(ElytronDescriptionConstants.CLASS_LOADING)
        .setAllowExpression(false)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final StringListAttributeDefinition CLASS_NAMES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.CLASS_NAMES)
        .setAttributeGroup(ElytronDescriptionConstants.CLASS_LOADING)
        .setAllowExpression(false)
        .setRequired(false)
        .setRestartAllServices()
        .build();

    static ClassLoader resolveClassLoader(String module) throws ModuleLoadException {
        Module current = Module.getCallerModule();
        if (module != null && current != null) {
            current = current.getModule(ModuleIdentifierUtil.parseCanonicalModuleIdentifier(module));
        }

        return current != null ? current.getClassLoader() : ClassLoadingAttributeDefinitions.class.getClassLoader();
    }

}
