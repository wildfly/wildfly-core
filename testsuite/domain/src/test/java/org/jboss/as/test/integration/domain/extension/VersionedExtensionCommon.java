/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.extension;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class VersionedExtensionCommon implements Extension {

    public static final String EXTENSION_NAME = "org.jboss.as.test.transformers";

    public static final String SUBSYSTEM_NAME = "test-subsystem";
    public static final String IGNORED_SUBSYSTEM_NAME = "ignored-test-subsystem";


    static AttributeDefinition TEST_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create("test-attribute", ModelType.STRING, true).build();

    protected static ResourceDefinition createResourceDefinition(final PathElement element) {
        return new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(element, new NonResolvingResourceDescriptionResolver())
        .setAddHandler(new AbstractAddStepHandler())
        .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    static OperationDefinition getOperationDefinition(String name) {
        return new SimpleOperationDefinitionBuilder(name, new NonResolvingResourceDescriptionResolver())
                .setReadOnly()
                .build();
    }

}
