/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.wildfly.core.launcher;

import java.util.List;

/**
 * Builds a list of commands that can be used to launch WildFly.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface CommandBuilder {

    /**
     * A list of command arguments required to launch WildFly instance.
     * <p/>
     * These are the arguments the follow a {@code java} executable command.
     *
     * @return the list of arguments required to launch WildFly
     */
    List<String> buildArguments();

    /**
     * A list of commands, including a {@code java} executable, required to launch WildFly
     * instance.
     *
     * @return the list of arguments required to launch WildFly
     */
    List<String> build();
}
