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

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceListener;

/**
 * <strong>Deprecated.</strong> Does nothing. Verification of services installed during operation execution is
 * now an internal function of the core management layer and implementors of {@link org.jboss.as.controller.OperationStepHandler}s
 * need not concern themselves with it.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 *
 * @deprecated has no function and will be removed in a later release
 *
 * @see org.jboss.as.controller.AbstractAddStepHandler#performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode, ServiceVerificationHandler, java.util.List)
 */
@Deprecated
public final class ServiceVerificationHandler extends AbstractServiceListener<Object> implements ServiceListener<Object>, OperationStepHandler {

    @SuppressWarnings("deprecation")
    public static final ServiceVerificationHandler INSTANCE = new ServiceVerificationHandler();

    public void execute(final OperationContext context, final ModelNode operation) {
        // no-op
        context.stepCompleted();
    }
}
