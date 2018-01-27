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
package org.wildfly.core.cli.command.aesh.activator;

import org.aesh.command.impl.internal.ParsedCommand;

/**
 *
 * Activator that hides option from completion and help.
 *
 * @author jdenise@redhat.com
 */
public class HideOptionActivator extends AbstractOptionActivator {

    @Override
    public boolean isActivated(ParsedCommand processedCommand) {
        return false;
    }

}
