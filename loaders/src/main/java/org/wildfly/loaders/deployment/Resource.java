/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.loaders.deployment;

import java.net.URL;

/**
 * This interface is exposed only because of Undertow Jastow JSP compiler
 * which expects all JSP related resources to be available as regular
 * files. This is also the reason why all web deployments are exploded.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface Resource extends org.jboss.modules.Resource {

    /**
     * Provides <b>deployment</b> URL scheme.
     * This method is used by JBoss Modules to obtain resource URLs.
     * @return deployment scheme URL.
     */
    @Override
    URL getURL();

    /**
     * Provides either <b>jar</b> or <b>file</b> URL scheme.
     * This method is exposed because of Undertow Jastow requirements.
     * @return either <b>jar</b> or <b>file</b> URL scheme.
     */
    URL getNativeURL();

}
