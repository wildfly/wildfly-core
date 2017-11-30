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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOneOfOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.StandaloneOptionActivator;

/**
 * A bunch of commands to test for help.
 *
 * @author jdenise@redhat.com
 */
public class Commands {

    static List<Class<? extends Command>> TESTS_STANDALONE = new ArrayList<>();

    static {
        for (Class<?> clazz : Commands.Standalone.class.getDeclaredClasses()) {
            TESTS_STANDALONE.add((Class<? extends Command>) clazz);
        }
    }

    static List<Class<? extends Command>> TESTS_DOMAIN = new ArrayList<>();

    static {
        for (Class<?> clazz : Commands.Domain.class.getDeclaredClasses()) {
            TESTS_DOMAIN.add((Class<? extends Command>) clazz);
        }
    }

    static List<Class<? extends Command>> TESTS_STANDALONE_ONLY = new ArrayList<>();

    static {
        for (Class<?> clazz : Commands.StandaloneOnly.class.getDeclaredClasses()) {
            TESTS_STANDALONE_ONLY.add((Class<? extends Command>) clazz);
        }
    }

    /**
     * Commands that don't have domain dependent options
     */
    public static class Standalone {

        /**
         * Simplest command.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command0 implements TestCommand, Command {

            @Override
            public String getSynopsis() {
                return "command1";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with options.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command1 implements TestCommand, Command {

            @Option(name = "opt1-with-value", hasValue = true)
            String opt1;
            @Option(name = "opt2-without-value", hasValue = false)
            String opt2;
            @Option(name = "opt3-with-value", hasValue = true)
            String opt3;
            @Argument()
            String args;

            @Override
            public String getSynopsis() {
                return "command1 [<argument>] [--opt1-with-value=<a string>] [--opt2-without-value] [--opt3-with-value=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with required option.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command2 implements TestCommand, Command {

            @Option(name = "opt1-with-value", hasValue = true)
            String opt1;
            @Option(name = "opt2-without-value", hasValue = false, required = true)
            String opt2;
            @Option(name = "opt3-with-value", hasValue = true, required = true)
            String opt3;
            @Argument()
            String args;

            @Override
            public String getSynopsis() {
                return "command1 --opt2-without-value --opt3-with-value=<a string> [<argument>] [--opt1-with-value=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with simple conflict between 2 options.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command3 implements TestCommand, Command {

            public static class Opt3Activator extends AbstractRejectOptionActivator {

                public Opt3Activator() {
                    super("opt2-without-value");
                }
            };
            @Option(name = "opt1-with-value", hasValue = true)
            String opt1;
            @Option(name = "opt2-without-value", hasValue = false, required = true)
            String opt2;
            @Option(name = "opt3-with-value", hasValue = true, required = true, activator = Opt3Activator.class)
            String opt3;

            @Override
            public String getSynopsis() {
                return "command1 ( --opt2-without-value | --opt3-with-value=<a string> ) [--opt1-with-value=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with simple conflict between 2 options that depend onto the
         * same option.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command4 implements TestCommand, Command {

            public static class Opt2Activator extends AbstractDependOptionActivator {

                public Opt2Activator() {
                    super(false, "opt1-with-value");
                }
            };

            public static class Opt3Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    // Argument.
                    EXPECTED.add("opt1-with-value");
                    NOT_EXPECTED.add("opt2-without-value");
                }

                public Opt3Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }
            @Option(name = "opt1-with-value", hasValue = true)
            String opt1;
            @Option(name = "opt2-without-value", hasValue = false, required = true, activator = Opt2Activator.class)
            String opt2;
            @Option(name = "opt3-with-value", hasValue = true, required = true, activator = Opt3Activator.class)
            String opt3;

            @Override
            public String getSynopsis() {
                return "command1 [--opt1-with-value=<a string>] ( --opt2-without-value | --opt3-with-value=<a string> )";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with simple conflict between 2 options that depend onto the
         * argument.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command5 implements TestCommand, Command {

            public static class Opt2Activator extends AbstractDependOptionActivator {

                public Opt2Activator() {
                    super(false, "");
                }
            };

            public static class Opt3Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    // Argument.
                    EXPECTED.add("");
                    NOT_EXPECTED.add("opt2-without-value");
                }

                public Opt3Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }
            @Option(name = "opt1-with-value", hasValue = true)
            String opt1;
            @Option(name = "opt2-without-value", hasValue = false, required = true, activator = Opt2Activator.class)
            String opt2;
            @Option(name = "opt3-with-value", hasValue = true, required = true, activator = Opt3Activator.class)
            String opt3;
            @Argument()
            String args;

            @Override
            public String getSynopsis() {
                return "command1 [<argument>] ( --opt2-without-value | --opt3-with-value=<a string> ) [--opt1-with-value=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with simple conflict between 2 options that depend onto the
         * argument and an option.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command6 implements TestCommand, Command {

            public static class Opt2Activator extends AbstractDependOptionActivator {

                public Opt2Activator() {
                    super(false, "", "opt1-with-value");
                }
            };

            public static class Opt3Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    // Argument.
                    EXPECTED.add("");
                    EXPECTED.add("opt1-with-value");
                    NOT_EXPECTED.add("opt2-without-value");
                }

                public Opt3Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }
            @Option(name = "opt1-with-value", hasValue = true)
            String opt1;
            @Option(name = "opt2-without-value", hasValue = false, required = true, activator = Opt2Activator.class)
            String opt2;
            @Option(name = "opt3-with-value", hasValue = true, required = true, activator = Opt3Activator.class)
            String opt3;
            @Argument()
            String args;

            @Override
            public String getSynopsis() {
                return "command1 [<argument>] [--opt1-with-value=<a string>] ( --opt2-without-value | --opt3-with-value=<a string> )";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with conflict between an option and a 2 other options and the
         * argument. The 2 other options depend on the argument. That is similar
         * to Patch info.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command7 implements TestCommand, Command {

            public static class NoStreamsActivator extends AbstractRejectOptionActivator {

                public NoStreamsActivator() {
                    super("streams");
                }
            };

            public static class NoPatchIdActivator extends AbstractRejectOptionActivator {

                public NoPatchIdActivator() {
                    super("");
                }
            };

            public static class PatchIdNoStreamsActivator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    // Argument.
                    EXPECTED.add("");
                    NOT_EXPECTED.add("streams");
                }

                public PatchIdNoStreamsActivator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }

            @Option(name = "patch-stream", hasValue = true, required = false, activator = PatchIdNoStreamsActivator.class)
            private String patchStream;

            @Argument(activator = NoStreamsActivator.class)
            private String patchIdArg;

            @Option(hasValue = false, shortName = 'v', required = false, activator = PatchIdNoStreamsActivator.class)
            boolean verbose;

            @Option(hasValue = false, required = false, activator = NoPatchIdActivator.class)
            boolean streams;

            @Override
            public String getSynopsis() {
                return "command1 ( [<argument>] [--patch-stream=<a string>] [--verbose] | [--streams] )";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with hidden options and arguments
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command8 implements TestCommand, Command {

            @Option(name = "opt1", hasValue = true, required = false, activator = HideOptionActivator.class)
            private String patchStream;

            @Argument(activator = HideOptionActivator.class)
            private String patchIdArg;

            @Option(hasValue = false, shortName = 'v', required = false, activator = HideOptionActivator.class)
            boolean verbose;

            @Option(hasValue = false, required = false, activator = HideOptionActivator.class)
            boolean streams;

            @Override
            public String getSynopsis() {
                return "command1";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with conflict between 2 groups of 2 options. Each group
         * composed of an option that depends on another one. XXX JFDENISE DOES
         * NOT WORK YET!!!!!
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command9 implements TestCommand, Command {

            public static class Opt3Activator extends AbstractRejectOptionActivator {

                public Opt3Activator() {
                    super("opt2-depends-on-opt1-conflict-with-opt4");
                }
            };

            public static class Opt4Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    EXPECTED.add("opt3-conflict-with-opt2");
                    NOT_EXPECTED.add("opt2-depends-on-opt1-conflict-with-opt4");
                }

                public Opt4Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }

            public static class Opt1Activator extends AbstractRejectOptionActivator {

                public Opt1Activator() {
                    super("opt4-depends-on-opt3-conflict-with-opt2");
                }
            };

            public static class Opt2Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    EXPECTED.add("opt1-conflict-with-opt4");
                    NOT_EXPECTED.add("opt4-depends-on-opt3-conflict-with-opt2");
                }

                public Opt2Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }
            @Option(name = "opt1-conflict-with-opt4", hasValue = false, activator = Opt1Activator.class)
            String opt1;
            @Option(name = "opt2-depends-on-opt1-conflict-with-opt4", hasValue = false, activator = Opt2Activator.class)
            String opt2;
            @Option(name = "opt3-conflict-with-opt2", hasValue = false, activator = Opt3Activator.class)
            String opt3;
            @Option(name = "opt4-depends-on-opt3-conflict-with-opt2", hasValue = false, activator = Opt4Activator.class)
            String opt4;

            @Override
            public String getSynopsis() {
                return "command1 ( [--opt1-conflict-with-opt4] [--opt2-depends-on-opt1-conflict-with-opt4] | "
                        + "[--opt3-conflict-with-opt2] [--opt4-depends-on-opt3-conflict-with-opt2] )";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Command with simple conflict between 3 options that depend onto the
         * argument. The 3 options are in conflict with each others, that is the
         * deployment deploy-file case. all-server-groups | replace |
         * server-groups
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command10 implements TestCommand, Command {

            public static class Opt1Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    EXPECTED.add("");
                    NOT_EXPECTED.add("replace");
                    NOT_EXPECTED.add("server-groups");
                }

                public Opt1Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            };

            public static class Opt2Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    EXPECTED.add("");
                    NOT_EXPECTED.add("replace");
                    NOT_EXPECTED.add("all-server-groups");
                }

                public Opt2Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            };

            public static class Opt3Activator extends AbstractDependRejectOptionActivator {

                private static final Set<String> EXPECTED = new HashSet<>();
                private static final Set<String> NOT_EXPECTED = new HashSet<>();

                static {
                    // Argument.
                    EXPECTED.add("");
                    NOT_EXPECTED.add("server-groups");
                    NOT_EXPECTED.add("all-server-groups");
                }

                public Opt3Activator() {
                    super(false, EXPECTED, NOT_EXPECTED);
                }
            }
            @Option(name = "all-server-groups", hasValue = false, activator = Opt1Activator.class)
            String opt1;
            @Option(name = "server-groups", hasValue = false, activator = Opt2Activator.class)
            String opt2;
            @Option(name = "replace", hasValue = false, activator = Opt3Activator.class)
            String opt3;
            @Argument()
            String args;

            @Override
            public String getSynopsis() {
                return "command1 [<argument>] ( [--all-server-groups] | [--replace] | [--server-groups] )";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * An option that is depending on one of 2 options in conflict.
         *
         * @author jdenise@redhat.com
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command11 implements TestCommand, Command {

            public static class Opt3Activator extends AbstractDependOneOfOptionActivator {

                public Opt3Activator() {
                    super("opt1", "opt2");
                }
            };

            public static class Opt5Activator extends AbstractDependOneOfOptionActivator {

                public Opt5Activator() {
                    super("opt2", "opt1");
                }
            };

            public static class Opt4Activator extends AbstractDependOptionActivator {

                public Opt4Activator() {
                    super(false, "opt1");
                }
            };

            public static class Opt1Activator extends AbstractRejectOptionActivator {

                public Opt1Activator() {
                    super("opt2");
                }
            };

            public static class Opt2Activator extends AbstractRejectOptionActivator {

                public Opt2Activator() {
                    super("opt1");
                }
            };

            @Option(name = "opt1", hasValue = false, activator = Opt1Activator.class)
            String opt1;
            @Option(name = "opt2", hasValue = false, activator = Opt2Activator.class)
            String opt2;
            @Option(name = "opt3", hasValue = false, activator = Opt3Activator.class)
            String opt3;
            @Option(name = "opt4", hasValue = false, activator = Opt4Activator.class)
            String opt4;
            @Option(name = "opt5", hasValue = false, activator = Opt5Activator.class)
            String opt5;

            @Override
            public String getSynopsis() {
                return "command1 ( [--opt1] [--opt4] | [--opt2] ) [--opt3] [--opt5]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

    }

    public static class DomainA extends AbstractOptionActivator implements DomainOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return getCommandContext().isDomainMode();
        }

    }

    public static class StandaloneA extends AbstractOptionActivator implements StandaloneOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return !getCommandContext().isDomainMode();
        }

    }

    /**
     * Commands that have domain dependent options
     */
    public static class Domain {

        /**
         * Only domain options
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command1 implements TestCommand, Command {

            @Option(name = "server-groups", activator = DomainA.class, required = false)
            public String serverGroups;

            @Option(name = "all-server-groups", activator = DomainA.class,
                    hasValue = false, required = false)
            public boolean allServerGroups;

            @Override
            public String getSynopsis() {
                return "command1 [--all-server-groups] [--server-groups=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Domain options and non domain ones.
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command2 implements TestCommand, Command {

            @Option(name = "server-groups", activator = DomainA.class, required = false)
            public String serverGroups;

            @Option(name = "all-server-groups", activator = DomainA.class,
                    hasValue = false, required = false)
            public boolean allServerGroups;

            @Option(name = "opt1", required = true, hasValue = false)
            public String opt1;

            @Override
            public String getSynopsis() {
                return "command1 --opt1 [--all-server-groups] [--server-groups=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Domain options, non domain ones and Standalone ones are not seen.
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command3 implements TestCommand, Command {

            @Option(name = "server-groups", activator = DomainA.class, required = false)
            public String serverGroups;

            @Option(name = "all-server-groups", activator = DomainA.class,
                    hasValue = false, required = false)
            public boolean allServerGroups;

            @Option(name = "opt1", required = true, hasValue = false)
            public String opt1;

            @Option(name = "opt2", required = true, hasValue = false, activator = StandaloneA.class)
            public String optStandalone;

            @Override
            public String getSynopsis() {
                return "command1 --opt1 [--all-server-groups] [--server-groups=<a string>]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Only standalone
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command4 implements TestCommand, Command {

            @Option(name = "opt1", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt1;

            @Option(name = "opt2", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt2;

            @Override
            public String getSynopsis() {
                return "command1";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }
    }

    /**
     * Commands that have standalone only dependent options
     */
    public static class StandaloneOnly {
        /**
         * Only standalone
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command1 implements TestCommand, Command {

            @Option(name = "opt1", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt1;

            @Option(name = "opt2", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt2;

            @Override
            public String getSynopsis() {
                return "command1 --opt1 --opt2";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Standalone and non domain
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command2 implements TestCommand, Command {

            @Option(name = "opt1", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt1;

            @Option(name = "opt2", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt2;

            @Option(name = "opt0", required = false, hasValue = false)
            public String opt0;

            @Override
            public String getSynopsis() {
                return "command1 --opt1 --opt2 [--opt0]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Standalone non domain and domain
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command3 implements TestCommand, Command {

            @Option(name = "opt1", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt1;

            @Option(name = "opt2", required = true, hasValue = false, activator = StandaloneA.class)
            public String opt2;

            @Option(name = "opt0", required = false, hasValue = false)
            public String opt0;

            @Option(name = "opt3", required = true, hasValue = false, activator = DomainA.class)
            public String opt3;

            @Option(name = "opt4", required = true, hasValue = false, activator = DomainA.class)
            public String opt4;

            @Override
            public String getSynopsis() {
                return "command1 --opt1 --opt2 [--opt0]";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /**
         * Only domain
         */
        @CommandDefinition(name = "command1", description = "")
        public static class Command4 implements TestCommand, Command {

            @Option(name = "opt3", required = true, hasValue = false, activator = DomainA.class)
            public String opt3;

            @Option(name = "opt4", required = true, hasValue = false, activator = DomainA.class)
            public String opt4;

            @Override
            public String getSynopsis() {
                return "command1";
            }

            @Override
            public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }
    }
}
