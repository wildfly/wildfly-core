/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.shared.assume;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.junit.Assume;

/**
 * Helper methods which help to skip tests for feature which is not yet fully working. Put the call of the method directly into
 * the failing test method, or if you want to skip whole test class, then put the method call into method annotated with
 * {@link org.junit.BeforeClass}.
 *
 * @author Josef Cacek
 * @deprecated this utility should not be used as tests should be fixed to work with Elytron, see
 *  <a href="https://issues.jboss.org/browse/WFCORE-2775">https://issues.jboss.org/browse/WFCORE-2775</a>
 */
@Deprecated
public class AssumeTestGroupUtil {

    public static final Supplier<Boolean> CONDITION_SKIP_ELYTRON_PROFILE = () -> (System.getProperty("elytron") == null
            || Boolean.getBoolean("wildfly.tmp.enable.elytron.profile.tests"));

    /**
     * Assume for test failures when running with Elytron profile enabled. It skips test in case the {@code '-Delytron'} Maven
     * argument is used (for Elytron profile activation) and system property {@code wildfly.tmp.enable.elytron.profile.tests}
     * hasn't value {@code 'true'}.
     */
    public static void assumeElytronProfileTestsEnabled() {
        assumeCondition("Tests failing in Elytron profile are disabled", CONDITION_SKIP_ELYTRON_PROFILE);
    }

    private static void assumeCondition(final String message, final Supplier<Boolean> assumeTrueCondition) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Assume.assumeTrue(message, assumeTrueCondition.get());
                return null;
            }
        });
    }

}
