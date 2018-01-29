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

/**
 * When an Option is only valid in domain mode, it must be activated by an
 * {@code org.aesh.command.activator.OptionActivator} that implements this
 * interface. Usage of this interface allows CLI to automatically generate
 * command help synopsis.
 *
 * @author jdenise@redhat.com
 */
public interface DomainOptionActivator {

}
