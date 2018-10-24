/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.operations;

import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Reports the current server {@link org.jboss.as.server.suspend.SuspendController.State}
 *
 * @author Stuart Douglas
 */
public class SuspendStateReadHandler implements OperationStepHandler {

    public static final SuspendStateReadHandler INSTANCE = new SuspendStateReadHandler();

    private SuspendStateReadHandler(){}

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        @SuppressWarnings("unchecked")
        ServiceController<SuspendController> sc = (ServiceController<SuspendController>) context.getServiceRegistry(false).getService(JBOSS_SUSPEND_CONTROLLER);
        SuspendController.State state;
        if(sc != null) {
            state = sc.getValue().getState();
        } else {
            // Either we haven't installed the SC yet or we're stopping and it's been removed
            // If we haven't installed, when we do its initial state is SUSPENDED
            // If it's been removed, it's last state was SUSPENDED.
            // So, report that.
            state = SuspendController.State.SUSPENDED;
        }
        context.getResult().set(state.name());
    }


}
