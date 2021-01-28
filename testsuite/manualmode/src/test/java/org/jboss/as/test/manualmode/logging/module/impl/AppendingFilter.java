/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging.module.impl;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.jboss.as.test.manualmode.logging.module.api.PropertyResolver;
import org.jboss.as.test.manualmode.logging.module.api.PropertyResolverFactory;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AppendingFilter implements Filter {

    private final PropertyResolver resolver;
    private final String text;

    public AppendingFilter(final String text) {
        this.text = text;
        resolver = PropertyResolverFactory.newResolver();
    }

    @Override
    public boolean isLoggable(final LogRecord record) {
        if (resolver != null && text != null) {
            final String currentMsg = record.getMessage();
            record.setMessage(currentMsg + " " + text);
            return true;
        }
        return false;
    }

    public String getText() {
        return text;
    }
}
