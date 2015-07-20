/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;

/**
 * Handler for the socket-binding-group resource's remove operation.
 * If the socket binding group has running servers this operation will fail
 *
 * @author Kabir Khan
 */
public class DomainSocketBindingGroupRemoveHandler extends AbstractRemoveStepHandler {

    public static final String OPERATION_NAME = REMOVE;

    public static final DomainSocketBindingGroupRemoveHandler INSTANCE = new DomainSocketBindingGroupRemoveHandler();

    /**
     * Create the DomainSocketBindingGroupRemoveHandler
     */
    private DomainSocketBindingGroupRemoveHandler() {
        super(SocketBindingGroupResourceDefinition.SOCKET_BINDING_GROUP_CAPABILITY);
    }

    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }

}
