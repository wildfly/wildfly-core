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

    static PrincipalTransformer aggregate(PrincipalTransformer... transformers) {
        Assert.checkNotNullParam("transformers", transformers);
        final PrincipalTransformer[] clone = transformers.clone();
        for (int i = 0; i < clone.length; i++) {
            Assert.checkNotNullArrayParam("transformers", i, clone[i]);
        }
        return p -> {
            if (p == null) return null;
            Principal tf;
            for (PrincipalTransformer t : clone) {
                tf = t.apply(p);
                if (tf != null) {
                    return tf;
                }
            }
            return null;
        };
    }

    static PrincipalTransformer chain(PrincipalTransformer... transformers) {
        Assert.checkNotNullParam("transformers", transformers);
        final PrincipalTransformer[] clone = transformers.clone();
        for (int i = 0; i < clone.length; i++) {
            Assert.checkNotNullArrayParam("transformers", i, clone[i]);
        }
        return p -> {
            for (PrincipalTransformer pt : clone) {
                if (p == null) return null;
                p = pt.apply(p);
            }
            return p;
        };
    }

}
