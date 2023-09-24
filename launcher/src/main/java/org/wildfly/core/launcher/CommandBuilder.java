/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
