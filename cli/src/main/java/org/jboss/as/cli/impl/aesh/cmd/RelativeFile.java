/*
Copyright 2017 Red Hat, Inc.

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
package org.jboss.as.cli.impl.aesh.cmd;

import java.io.File;

/**
 * Use this type for file option that are bound to a relative-to option.
 *
 * @author jdenise@redhat.com
 */
public class RelativeFile extends File {

    private final String originalPath;

    public RelativeFile(String originalPath, File absolutePath) {
        super(absolutePath.getAbsolutePath());
        this.originalPath = originalPath;
    }

    public String getOriginalPath() {
        return originalPath;
    }
}
