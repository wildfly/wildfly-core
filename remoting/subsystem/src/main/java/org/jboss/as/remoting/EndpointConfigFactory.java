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

package org.jboss.as.remoting;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.io.OptionList;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Runtime configuration factory for remoting endpoints.
 *
 * @author Emanuel Muckenhuber
 */
public final class EndpointConfigFactory {

    private EndpointConfigFactory() {
        //
    }

    public static OptionMap populate(final ExpressionResolver resolver, final ModelNode model) throws OperationFailedException {
        OptionMap.Builder builder = OptionMap.builder()
        .set(Options.TCP_NODELAY, Boolean.TRUE)
        .set(Options.REUSE_ADDRESSES, true)
        .addAll(OptionList.resolveOptions(resolver, model, RemotingSubsystemRootResource.OPTIONS));

        return builder.getMap();
    }

    /**
     * creates option map for remoting connections
     * @param resolver
     * @param model
     * @param defaults
     * @return
     * @throws OperationFailedException
     * @deprecated configuring xnio worker options is no longer supported and should be replaced for referencing IO subsystem
     */
    @Deprecated
    public static OptionMap create(final ExpressionResolver resolver, final ModelNode model, final OptionMap defaults) throws OperationFailedException {
        final OptionMap map = OptionMap.builder()
                .addAll(defaults)
                .getMap();
        return map;
    }

}
