/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;

import java.io.File;
import java.util.function.Consumer;

import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.Assert;

/**
 * {@link AbstractPathService} implementation for paths that are not relative to other paths.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class AbsolutePathService extends AbstractPathService {

    private final String absolutePath;

    private AbsolutePathService(final Consumer<String> pathConsumer, final String abstractPath) {
        super(pathConsumer);
        absolutePath = convertPath(abstractPath);
    }

    public static ServiceController<?> addService(final String name, final String abstractPath, final ServiceTarget serviceTarget) {
        return addService(pathNameOf(name), abstractPath, serviceTarget);
    }

    public static ServiceController<?> addService(final ServiceName sname, final String abstractPath, final ServiceTarget serviceTarget) {
        final ServiceBuilder<?> builder = serviceTarget.addService(sname);
        final Consumer<String> pathConsumer = builder.provides(sname);
        builder.setInstance(new AbsolutePathService(pathConsumer, abstractPath));
        return builder.install();
    }

    public static void addService(final ServiceName name, final ModelNode element, final ServiceTarget serviceTarget) {
        final String path = element.require(PATH).asString();
        addService(name, path, serviceTarget);
    }

    public static String convertPath(String abstractPath) {
        Assert.checkNotNullParam("abstractPath", abstractPath);
        Assert.checkNotEmptyParam("abstractPath", abstractPath);
        // Use File.getAbsolutePath() to make relative paths absolute
        File f = new File(abstractPath);
        return f.getAbsolutePath();
    }

    @Override
    protected String resolvePath() {
        return absolutePath;
    }

}
