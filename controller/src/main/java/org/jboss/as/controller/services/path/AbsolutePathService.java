/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
