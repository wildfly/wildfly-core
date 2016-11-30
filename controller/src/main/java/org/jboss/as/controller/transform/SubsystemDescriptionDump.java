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

package org.jboss.as.controller.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.SubsystemInformation;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/** todo move to test code, no need to have it at proper runtime
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class SubsystemDescriptionDump implements OperationStepHandler {

    private final ExtensionRegistry extensionRegistry;
    protected static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinition("path", ModelType.STRING, false);
    public static final String OPERATION_NAME = "subsystem-description-dump";
    public static final OperationDefinition DEFINITION =
            new SimpleOperationDefinitionBuilder(OPERATION_NAME, new NonResolvingResourceDescriptionResolver())
                    .setPrivateEntry()
                    .setReadOnly()
                    .setParameters(PATH)
                    .build();


    public SubsystemDescriptionDump(final ExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        String path = PATH.resolveModelAttribute(context, operation).asString();
        PathAddress profileAddress = PathAddress.pathAddress(PathElement.pathElement(ModelDescriptionConstants.PROFILE));
        ImmutableManagementResourceRegistration profileRegistration = context.getResourceRegistration().getSubModel(profileAddress);
        dumpManagementResourceRegistration(profileRegistration, extensionRegistry, path);
    }

    public static void dumpManagementResourceRegistration(final ImmutableManagementResourceRegistration profileRegistration,
                                                          final ExtensionRegistry registry, final String path) throws OperationFailedException{
        try {
            for (PathElement pe : profileRegistration.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
                ImmutableManagementResourceRegistration registration = profileRegistration.getSubModel(PathAddress.pathAddress(pe));
                String subsystem = pe.getValue();
                SubsystemInformation info = registry.getSubsystemInfo(subsystem);
                ModelNode desc = readFullModelDescription(PathAddress.pathAddress(pe), registration);
                String name = subsystem + "-" + info.getManagementInterfaceMajorVersion() + "." + info.getManagementInterfaceMinorVersion() +"."+info.getManagementInterfaceMicroVersion()+ ".dmr";
                PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get(path,name), StandardCharsets.UTF_8));
                desc.writeString(pw, false);
                pw.close();
            }
        } catch (IOException e) {
            throw new OperationFailedException("could not save,", e);
        }
    }

    public static ModelNode readFullModelDescription(PathAddress address, ImmutableManagementResourceRegistration reg) {
         ModelNode node = new ModelNode();
         node.get(ModelDescriptionConstants.MODEL_DESCRIPTION).set(reg.getModelDescription(PathAddress.EMPTY_ADDRESS).getModelDescription(Locale.getDefault()));
         node.get(ModelDescriptionConstants.ADDRESS).set(address.toModelNode());
         for (PathElement pe : reg.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
             ModelNode children = node.get(ModelDescriptionConstants.CHILDREN);
             ImmutableManagementResourceRegistration sub = reg.getSubModel(PathAddress.pathAddress(pe));
             children.add(readFullModelDescription(address.append(pe), sub));
         }
         return node;
     }

}
