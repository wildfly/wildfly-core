/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron.capabilities;

import java.security.Principal;
import java.util.function.Function;

import org.wildfly.common.Assert;

/**
 * A {@link Function} that transforms {@link Principal} instances.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface PrincipalTransformer extends Function<Principal, Principal> {

    static PrincipalTransformer from(final Function<Principal, Principal> function) {
        return function::apply;
    }

    /**
     * Principal transformer which transforms principal by individual transformers
     * in defined order and returns first non-null transformed principal.
     * Input of all transformers is original principal.
     */
    static PrincipalTransformer aggregate(PrincipalTransformer... transformers) {
        Assert.checkNotNullParam("transformers", transformers);
        final PrincipalTransformer[] clone = transformers.clone();
        for (int i = 0; i < clone.length; i++) {
            Assert.checkNotNullArrayParam("transformers", i, clone[i]);
        }
        return principal -> {
            if (principal == null) return null;
            for (PrincipalTransformer transformer : clone) {
                Principal transformed = transformer.apply(principal);
                if (transformed != null) {
                    return transformed;
                }
            }
            return null;
        };
    }

    /**
     * Principal transformer which transforms original principal by first transformer
     * in chain, its output transforms by second transformer etc. Output of last
     * transformer is returned.
     */
    static PrincipalTransformer chain(PrincipalTransformer... transformers) {
        Assert.checkNotNullParam("transformers", transformers);
        final PrincipalTransformer[] clone = transformers.clone();
        for (int i = 0; i < clone.length; i++) {
            Assert.checkNotNullArrayParam("transformers", i, clone[i]);
        }
        return principal -> {
            for (PrincipalTransformer transformer : clone) {
                if (principal == null) return null;
                principal = transformer.apply(principal);
            }
            return principal;
        };
    }

}
