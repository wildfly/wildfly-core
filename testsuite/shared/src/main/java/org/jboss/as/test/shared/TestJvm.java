/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.shared;

import java.nio.file.Path;

/**
 * Represents a JVM description.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class TestJvm {

    private static final org.wildfly.core.testrunner.TestJvm INSTANCE = org.wildfly.core.testrunner.TestJvm.getInstance();

    private TestJvm() {
    }

    /**
     * The the command which can launch this JVM.
     *
     * @return the command
     */
    public static String getCommand() {
        return INSTANCE.getCommand();
    }

    /**
     * The path to this JVM.
     *
     * @return the path
     */
    public static Path getPath() {
        return INSTANCE.getPath();
    }

    /**
     * Indicates whether or not this is a modular JVM.
     *
     * @return {@code true} if this is a modular JVM, otherwise {@code false}
     */
    public static boolean isModular() {
        return INSTANCE.isModular();
    }

    /**
     * Indicates whether or not this is a IBM JVM.
     *
     * @return {@code true} if this is a IBM JVM, otherwise {@code false}
     *
     * @see #isJ9Jvm()
     */
    public static boolean isIbmJvm() {
        return INSTANCE.isIbmJvm();
    }

    /**
     * Indicates whether or not this is an Eclipse OpenJ9 or IBM J9 JVM.
     *
     * @return {@code true} if this is an Eclipse OpenJ9 or IBM J9 JVM, otherwise {@code false}
     */
    public static boolean isJ9Jvm() {
        return INSTANCE.isJ9Jvm();
    }
}
