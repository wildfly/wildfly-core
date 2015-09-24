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

package org.jboss.as.subsystem.test.transformers.subsystem.simple;

import java.lang.reflect.Method;
import java.util.List;

import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class VersionedExtension1 extends VersionedExtensionCommon {

    static final PathElement ORIGINAL = PathElement.pathElement("element", "renamed");

    @Override
    public void initialize(final ExtensionContext context) {
        SubsystemRegistration subsystem;
        try {
            subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        } catch (NoSuchMethodError e) {
            //Older controllers don't have this method, use reflection
            Method m = null;
            try {
                m = context.getClass().getMethod("registerSubsystem", String.class, Integer.TYPE, Integer.TYPE);
                subsystem = (SubsystemRegistration)m.invoke(context, SUBSYSTEM_NAME, 1, 0);
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        }

        final ManagementResourceRegistration registration = initializeSubsystem(subsystem);
        // Register an element which is going to get renamed
        registration.registerSubModel(new TestResourceDefinition(ORIGINAL));

        // No transformers for the first version of the model!
    }

    @Override
    protected void addChildElements(List<ModelNode> list) {
        ModelNode childAdd = createAddOperation(PathAddress.pathAddress(SUBSYSTEM_PATH, ORIGINAL));
        list.add(childAdd);
    }
}
