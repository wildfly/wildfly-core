/*
Copyright 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.core.embedded;

/**
 * Exception thrown during {@link EmbeddedManagedProcess#start()}.
 *
 * @author Brian Stansberry
 */
public class EmbeddedProcessStartException extends Exception {

    private static final long serialVersionUID = 7991468792402261287L;

    public EmbeddedProcessStartException(String message) {
        super(message);
    }

    public EmbeddedProcessStartException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmbeddedProcessStartException(Throwable cause) {
        super(cause);
    }
}
