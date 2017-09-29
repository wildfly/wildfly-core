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
package org.jboss.as.cli.impl.aesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.impl.internal.ProcessedOption;
import org.wildfly.core.cli.command.aesh.activator.DependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.RejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.StandaloneOptionActivator;

/**
 * This class implements the logic needed to generate command help synopsis.
 * Option constraints (domain only, standalone only, conflicts, dependencies)
 * are taken into account to generate a synopsis that reflect the actual option
 * combinations.
 *
 * @author jdenise@redhat.com
 */
class SynopsisGenerator {
    private final List<ProcessedOption> opts;
    private final ProcessedOption arg;
    private final boolean domain;
    private Set<SynopsisOption> synopsisOptions;
    private final ResourceBundle bundle;
    private final String parentName;
    private final String commandName;
    private final boolean hasActions;
    private final List<String> superNames;
    private final boolean isOperation;

    SynopsisGenerator(ResourceBundle bundle,
            String parentName,
            String commandName,
            List<ProcessedOption> opts,
            ProcessedOption arg,
            boolean hasActions,
            List<String> superNames,
            boolean isOperation, boolean domain) {
        this.bundle = bundle;
        this.parentName = parentName;
        this.commandName = commandName;
        this.opts = opts;
        this.arg = arg;
        this.hasActions = hasActions;
        this.superNames = superNames;
        this.isOperation = isOperation;
        this.domain = domain;

    }

    // main class entry point.
    String generateSynopsis() {
        synopsisOptions = buildSynopsisOptions(opts, arg, domain);
        StringBuilder synopsisBuilder = new StringBuilder();
        if (parentName != null) {
            synopsisBuilder.append(parentName).append(" ");
        }
        synopsisBuilder.append(commandName);
        if (isOperation && !opts.isEmpty()) {
            synopsisBuilder.append("(");
        } else {
            synopsisBuilder.append(" ");
        }
        boolean hasOptions = arg != null || !opts.isEmpty();
        if (hasActions && hasOptions) {
            synopsisBuilder.append(" [");
        }
        if (hasActions) {
            synopsisBuilder.append(" <action>");
        }
        if (hasActions && hasOptions) {
            synopsisBuilder.append(" ] || [");
        }
        SynopsisOption opt;
        while ((opt = retrieveNextOption(synopsisOptions, false)) != null) {
            String content = addSynopsisOption(opt);
            if (content != null) {
                synopsisBuilder.append(content.trim());
                if (isOperation) {
                    if (!synopsisOptions.isEmpty()) {
                        synopsisBuilder.append(",");
                    } else {
                        synopsisBuilder.append(")");
                    }
                }
                synopsisBuilder.append(" ");
            }
        }
        if (hasActions && hasOptions) {
            synopsisBuilder.append(" ]");
        }
        return synopsisBuilder.toString();
    }

    private String addSynopsisOption(SynopsisOption currentOption) {
        if (!synopsisOptions.contains(currentOption)) {
            return null;
        }
        StringBuilder synopsisBuilder = new StringBuilder();
        synopsisOptions.remove(currentOption);
        // We know that it has no (more) dependency.
        if (!currentOption.dependsOn.isEmpty()) {
            throw new RuntimeException("Option has still some dependencies");
        }
        // If conflict
        if (!currentOption.conflictWith.isEmpty()) {
            // Complex situation. We must:
            // Retrieve all options that are a common dependency of all conflicts.
            // They must be added prior to this conflict.
            Set<SynopsisOption> commonDependencies = retrieveCommonDependenciesFromConflicts(currentOption);
            // All dependendedby will be next removed from the conflicts of the conflicts, they are handled
            // when adding the currentOption and all its dependedBy.
            Set<SynopsisOption> allDependedBy = retrieveAllDependedBy(currentOption);

            // Allconflicts that are handled at this level will be removed from this option dependedBy
            Set<SynopsisOption> allConflicts = retrieveAllDependenciesOfConflicts(currentOption.conflictWith);
            allConflicts.addAll(currentOption.conflictWith);

            // We can add the shared dependencies
            SynopsisOption nextOption;
            while ((nextOption = retrieveNextOption(commonDependencies, false)) != null) {
                commonDependencies.remove(nextOption);
                String next = addSynopsisOption(nextOption);
                if (next != null) {
                    synopsisBuilder.append(" ").append(next.trim());
                }
            }
            // Now we should be able to add the current option and its dependedBy.
            synopsisBuilder.append(" ( ");
            addSynopsisOptionNameValue(synopsisBuilder, currentOption);
            // Remove the current option from the dependencies now that it has been added
            for (SynopsisOption option : currentOption.dependedBy) {
                option.dependsOn.remove(currentOption);
            }
            while ((nextOption = retrieveNextOption(currentOption.dependedBy, false)) != null) {
                currentOption.dependedBy.remove(nextOption);
                // Remove the conflicts (and all their dependencies) that are also the currentOption conflicts, they are handled by this option
                for (SynopsisOption conflict : allConflicts) {
                    nextOption.conflictWith.remove(conflict);
                    conflict.conflictWith.remove(nextOption);
                }
                String next = addSynopsisOption(nextOption);
                if (next != null) {
                    synopsisBuilder.append(" ").append(next.trim());
                }
            }
            synopsisBuilder.append(" |");
            // Keep the set of all conflicts, they will be removed from all conflicts, being handled at this level.
            Set<SynopsisOption> conflicts = new HashSet<>(currentOption.conflictWith);
            Set<SynopsisOption> conflicts2 = new HashSet<>(currentOption.conflictWith);
            // Conflicts can have dependencies that must be added prior to them
            for (SynopsisOption so : conflicts2) {
                Set<SynopsisOption> conflictAndDependencies = retrieveAllDependencies(so);
                conflictAndDependencies.add(so);
                // Remove all conflict with currentOption
                for (SynopsisOption s : conflictAndDependencies) {
                    s.conflictWith.remove(currentOption);
                    currentOption.conflictWith.remove(s);
                }
                while ((nextOption = retrieveNextOption(conflictAndDependencies, false)) != null) {
                    conflictAndDependencies.remove(nextOption);
                    // remove the conflict symetricaly.
                    nextOption.conflictWith.remove(currentOption);
                    currentOption.conflictWith.remove(nextOption);
                    // The conflict could also be in conflict with some options that are dependedBy currentOption
                    // They have been added previously, so we can remove them.
                    for (SynopsisOption conflict : allDependedBy) {
                        nextOption.conflictWith.remove(conflict);
                        conflict.conflictWith.remove(nextOption);
                    }
                    //Remove all the condlficts that are common to the currentOption, they are handled at this level
                    for (SynopsisOption conflict : conflicts) {
                        nextOption.conflictWith.remove(conflict);
                        conflict.conflictWith.remove(nextOption);
                    }
                    String next = addSynopsisOption(nextOption);
                    if (next != null) {
                        synopsisBuilder.append(" ").append(next.trim());
                    }
                    if (!currentOption.conflictWith.isEmpty()) {
                        synopsisBuilder.append(" |");
                    }
                }
            }
            synopsisBuilder.append(" ) ");

        } else {
            addSynopsisOptionNameValue(synopsisBuilder, currentOption);
            SynopsisOption nextOption;
            // Remove the current option from the dependencies now that it has been added
            for (SynopsisOption option : currentOption.dependedBy) {
                option.dependsOn.remove(currentOption);
            }
            while ((nextOption = retrieveNextOption(currentOption.dependedBy, true)) != null) {
                currentOption.dependedBy.remove(nextOption);
                String next = addSynopsisOption(nextOption);
                if (next != null) {
                    synopsisBuilder.append(" ").append(next.trim());
                }
            }
        }
        return synopsisBuilder.toString();
    }

    private static Set<SynopsisOption> retrieveCommonDependenciesFromConflicts(SynopsisOption currentOption) {
        // Retrieve all dependencies of each option
        // Retrieve all conflicts of each option
        // Common means present in all and no conflict
        Set<SynopsisOption> common = new HashSet<>();
        List<Set<SynopsisOption>> dependencies = new ArrayList<>();
        dependencies.add(retrieveAllDependencies(currentOption));
        Set<SynopsisOption> allDependencies = new HashSet<>();
        for (SynopsisOption option : currentOption.conflictWith) {
            Set<SynopsisOption> deps = retrieveAllDependencies(option);
            dependencies.add(deps);
            allDependencies.addAll(deps);
        }

        Set<SynopsisOption> allConflicts = new HashSet<>();
        for (SynopsisOption option : currentOption.conflictWith) {
            Set<SynopsisOption> confs = retrieveAllConflicts(option);
            allConflicts.addAll(confs);
        }

        for (SynopsisOption opt : allDependencies) {
            boolean shared = true;
            // Check that option is a dependency of ALL conflicts
            for (Set<SynopsisOption> set : dependencies) {
                if (!set.contains(opt)) {
                    shared = false;
                    break;
                }
            }
            if (shared) {
                // We have a conflict somewhere.
                if (allConflicts.contains(opt)) {
                    shared = false;
                }
            }
            if (shared) {
                common.add(opt);
            }
        }
        return common;
    }

    private static Set<SynopsisOption> retrieveAllDependencies(SynopsisOption option) {
        Set<SynopsisOption> options = new HashSet<>();
        for (SynopsisOption opt : option.dependsOn) {
            options.add(opt);
            options.addAll(retrieveAllDependencies(opt));
        }
        return options;
    }

    private static Set<SynopsisOption> retrieveAllDependenciesOfConflicts(Set<SynopsisOption> conflicts) {
        Set<SynopsisOption> options = new HashSet<>();
        for (SynopsisOption conflict : conflicts) {
            options.addAll(conflict.dependsOn);
            options.addAll(retrieveAllDependenciesOfConflicts(conflict.dependsOn));
        }
        return options;
    }

    private static Set<SynopsisOption> retrieveAllConflicts(SynopsisOption option) {
        Set<SynopsisOption> options = new HashSet<>();
        options.addAll(option.conflictWith);
        for (SynopsisOption opt : option.dependsOn) {
            options.addAll(retrieveAllConflicts(opt));
        }
        return options;
    }

    private static Set<SynopsisOption> retrieveAllDependedBy(SynopsisOption option) {
        Set<SynopsisOption> options = new HashSet<>();
        options.addAll(option.dependedBy);
        for (SynopsisOption opt : option.dependedBy) {
            options.addAll(retrieveAllDependedBy(opt));
        }
        return options;
    }

    public static class SynopsisOption {

        private ProcessedOption option;
        private final Set<SynopsisOption> dependsOn = new HashSet<>();
        private final Set<SynopsisOption> conflictWith = new HashSet<>();
        private final Set<SynopsisOption> dependedBy = new HashSet<>();

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SynopsisOption)) {
                return false;
            }
            SynopsisOption dep = (SynopsisOption) other;
            return option.name().equals(dep.option.name());
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 13 * hash + Objects.hashCode(this.option.name());
            return hash;
        }

        @Override
        public String toString() {
            return option.name();
        }
    }

    private static Set<SynopsisOption> buildSynopsisOptions(List<ProcessedOption> opts,
            ProcessedOption arg, boolean domain) {
        Map<ProcessedOption, SynopsisOption> options = new IdentityHashMap<>();
        if (arg != null) {
            List<ProcessedOption> argExpected = retrieveExpected(arg.activator(),
                    opts, null, domain);
            SynopsisOption d = new SynopsisOption();
            d.option = arg;
            options.put(arg, d);
            for (ProcessedOption e : argExpected) {
                SynopsisOption de = new SynopsisOption();
                de.option = e;
                options.put(e, de);
                d.dependsOn.add(de);
                de.dependedBy.add(d);
            }
            List<ProcessedOption> argNotExpected = retrieveNotExpected(arg.activator(),
                    opts, null, domain);
            for (ProcessedOption e : argNotExpected) {
                SynopsisOption depDep = options.get(e);
                if (depDep == null) {
                    depDep = new SynopsisOption();
                    depDep.option = e;
                    options.put(e, depDep);
                }
                depDep.option = e;
                options.put(e, depDep);
                d.conflictWith.add(depDep);
                depDep.conflictWith.add(d);
            }
        }
        for (ProcessedOption opt : opts) {
            List<ProcessedOption> expected = retrieveExpected(opt.activator(), opts, arg, domain);
            SynopsisOption optDep = options.get(opt);
            if (optDep == null) {
                optDep = new SynopsisOption();
                optDep.option = opt;
                options.put(opt, optDep);
            }
            for (ProcessedOption e : expected) {
                SynopsisOption depDep = options.get(e);
                if (depDep == null) {
                    depDep = new SynopsisOption();
                    depDep.option = e;
                    options.put(e, depDep);
                }
                optDep.dependsOn.add(depDep);
                depDep.dependedBy.add(optDep);
            }
            List<ProcessedOption> notExpected = retrieveNotExpected(opt.activator(), opts, arg, domain);
            for (ProcessedOption e : notExpected) {
                SynopsisOption depDep = options.get(e);
                if (depDep == null) {
                    depDep = new SynopsisOption();
                    depDep.option = e;
                    options.put(e, depDep);
                }
                optDep.conflictWith.add(depDep);
                depDep.conflictWith.add(optDep);
            }
        }
        return new HashSet<>(options.values());
    }

    private static List<ProcessedOption> retrieveNotExpected(OptionActivator activator, List<ProcessedOption> opts, ProcessedOption arg, boolean domain) {
        List<ProcessedOption> notExpected = new ArrayList<>();
        if (activator == null) {
            return notExpected;
        }
        if (activator instanceof RejectOptionActivator) {
            for (String s : ((RejectOptionActivator) activator).getRejected()) {
                if (s == null || s.equals("")) { // argument
                    if (arg != null) {
                        if (isDomainCompliant(arg, domain)) {
                            notExpected.add(arg);
                        }
                    }
                } else {
                    for (ProcessedOption opt : opts) {
                        if (s.equals(opt.name())) {
                            if (isDomainCompliant(opt, domain)) {
                                notExpected.add(opt);
                            }
                        }
                    }
                }
            }
        }
        return notExpected;
    }

    private static List<ProcessedOption> retrieveExpected(OptionActivator activator, List<ProcessedOption> opts, ProcessedOption arg, boolean domain) {
        List<ProcessedOption> expected = new ArrayList<>();
        if (activator == null) {
            return expected;
        }
        if (activator instanceof DependOptionActivator) {
            for (String s : ((DependOptionActivator) activator).getDependsOn()) {
                if (s == null || s.equals("")) { // argument
                    if (arg != null) {
                        if (isDomainCompliant(arg, domain)) {
                            expected.add(arg);
                        }
                    }
                } else {
                    for (ProcessedOption opt : opts) {
                        if (s.equals(opt.name())) {
                            if (isDomainCompliant(opt, domain)) {
                                expected.add(opt);
                            }
                        }
                    }
                }
            }
        }
        return expected;
    }

    private static boolean isDomainCompliant(ProcessedOption arg, boolean domain) {
        if (domain) {
            // This option is only valid in non domain mode.
            if (arg.activator() instanceof StandaloneOptionActivator) {
                return false;
            }
        } else // This option is only valid in non domain mode.
         if (arg.activator() instanceof DomainOptionActivator) {
                return false;
            }
        return true;
    }

    private static SynopsisOption retrieveNextOption(Set<SynopsisOption> synopsisOptions, boolean wantLeafDependency) {
        if (synopsisOptions.isEmpty()) {
            return null;
        }

        // Look for the first one that has no dependency, required and name as sorting key.
        List<SynopsisOption> options = new ArrayList<>();
        for (SynopsisOption synopsisOption : synopsisOptions) {
            if (synopsisOption.dependsOn.isEmpty()) {
                options.add(synopsisOption);
            }
        }
        if (options.isEmpty()) {
            if (!wantLeafDependency) {
                throw new RuntimeException("No option without dependency...alert");
            } else {
                return null;
            }
        }

        //
        // sort options.
        Collections.sort(options, (SynopsisOption o1, SynopsisOption o2) -> {
            // Headers are last ones.
            if (o1.option.name().equals("headers")) {
                return 1;
            }
            if (o2.option.name().equals("headers")) {
                return -1;
            }
            if (o1.option.isRequired() && o2.option.isRequired()) {
                return o1.option.name().compareTo(o2.option.name());
            }
            if (o1.option.isRequired()) {
                return -1;
            }
            if (o2.option.isRequired()) {
                return 1;
            }
            return o1.option.name().compareTo(o2.option.name());
        });
        return options.get(0);
    }

    private void addSynopsisOptionNameValue(StringBuilder synopsisBuilder,
            SynopsisOption currentOption) {
        if (!currentOption.option.isRequired()) {
            synopsisBuilder.append("[");
        }
        if (currentOption.option.name().equals("")) {
            String value = HelpSupport.getValue(bundle, parentName, commandName, superNames,
                    "arguments.value", true);
            synopsisBuilder.append("<").append(value == null ? "argument" : value).append(">");
        } else {
            if (isOperation) {
                synopsisBuilder.append(currentOption.option.name()).append("=");
            } else {
                synopsisBuilder.append("--").append(currentOption.option.name());
            }
            if (currentOption.option.hasValue()) {
                String val;
                if (isOperation) {
                    val = HelpSupport.VALUES.get(currentOption.option.type());
                } else {
                    val = HelpSupport.getValue(bundle, parentName, commandName, superNames, "option."
                            + currentOption.option.name() + ".value", true);
                    val = val == null ? HelpSupport.VALUES.get(currentOption.option.type()) : val;
                    synopsisBuilder.append("=");
                }

                synopsisBuilder.append("<").append(val).append(">");
            }
        }
        if (!currentOption.option.isRequired()) {
            synopsisBuilder.append("]");
        }
    }

}
