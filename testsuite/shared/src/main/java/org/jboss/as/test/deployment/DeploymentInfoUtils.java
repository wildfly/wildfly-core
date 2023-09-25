/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.deployment;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.common.annotation.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.AllOf.allOf;
import static org.jboss.as.cli.Util.FAILURE_DESCRIPTION;
import static org.jboss.as.cli.Util.OUTCOME;
import static org.jboss.as.cli.Util.RESULT;
import static org.jboss.as.cli.Util.SUCCESS;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.UNKNOWN;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.mapBooleanByDeploymentStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.fail;

/**
 * Utils to verify the state of deployments using command "deployment list" and "deployment info"
 * Uses legacy and Aesh version of commands.
 * Direct verifying output of this commands and verify it with management operations.
 *
 * @author Vratislav Marek (vmarek@redhat.com)
 **/
public class DeploymentInfoUtils {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String FAILED = "failed";

    private static final Logger log = Logger.getLogger(DeploymentInfoUtils.class);

    private DeploymentInfoUtils() {
        //
    }

    /**
     * Represent application deployment status.
     * Status of deployment you can read by command 'deployment info'.
     */
    public enum DeploymentState {
        // Statuses of Domain
        ENABLED("enabled"), // Represent installed in selected server group and enabled application deployment
        ADDED("added"), // Represent installed in selected server group but disabled application deployment
        NOT_ADDED("not added"), // Represents application deployment of other server group that selected server group

        // Statuses of Standalone
        OK("OK"), // Represent enabled application deployment
        STOPPED("STOPPED"), // Represent disabled application deployment

        // Error status
        UNKNOWN("!--unknown--!"); // Default value if isn't found

        private String title;

        DeploymentState(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        /**
         * Return information about enabled or disabled state from deployment info command statuses
         *
         * @param status Status of application deployment
         * @return true application deployment id enabled, false is disabled
         */
        public static boolean mapBooleanByDeploymentStatus(@NotNull DeploymentState status) {
            switch (status) {
                case OK:
                case ENABLED:
                    return true;
                case ADDED:
                case STOPPED:
                    return false;
                case NOT_ADDED:
                case UNKNOWN:
                default:
                    throw new IllegalArgumentException("Unsupported state " + status + "!");
            }
        }
    }

    // #### BEGIN Internal containers classes

    /**
     * The result of the deployment list/info invocation.
     * Contains the output of the command that can be used to check the state of deployments.
     */
    public static class DeploymentInfoResult {
        // Called command list or info about applications deployments for checking
        private final String command;
        // Selected server group in list/info command
        private final String serverGroup;
        // Original output of list/info command
        private final String originalOutput;
        /* Holding output of called command for multiple checking output without recall command
           Output is parsed for processing*/
        private final List<String> rows;
        // Represent request of management operations to verify check
        private String request;
        // Represent response of management operations to verify check
        private String response;

        private DeploymentInfoResult(String command, String serverGroup, String output) {
            this.command = command;
            this.serverGroup = serverGroup;
            originalOutput = output;
            if (originalOutput != null && !originalOutput.isEmpty()) {
                log.trace("Read output:\n" + output);
                rows = Arrays.asList(originalOutput.split(LINE_SEPARATOR));
            } else {
                log.trace("Read output: <EMPTY>");
                rows = new ArrayList<>();
            }
        }

        /**
         * If is called command has empty output.
         *
         * @return If is command output is empty return true, else If command has some output return false
         */
        public boolean isOutputEmpty() {
            return rows.size() <= 0;
        }

        /**
         * Get parsed output for processing
         *
         * @return Parsed output for processing
         */
        public List<String> getRows() {
            return rows;
        }

        /**
         * Get called command list or info about applications deployments for checking
         *
         * @return Called command list or info about applications deployments for checking
         */
        public String getCommand() {
            return command;
        }

        /**
         * Return joined string of parsed rows or output empty mark in case output is empty
         *
         * @return Return joined string of parsed rows or output empty mark in case output is empty
         */
        @Override
        public String toString() {
            String result = this.getClass().getSimpleName() + "{command='" + command + "', outputs";
            if (isOutputEmpty()) {
                return result + "='<EMPTY>'}";
            }
            return result + ":{" + LINE_SEPARATOR + String.join(LINE_SEPARATOR, rows) + LINE_SEPARATOR +  "}}";
        }

        /**
         * If is set selected server group
         *
         * @return If server group has set return true, else false
         */
        public boolean hasServerGroup() {
            return serverGroup != null;
        }

        /**
         * Get selected server group in list/info command
         *
         * @return Selected server group in list/info command
         */
        public String getServerGroup() {
            return serverGroup;
        }

        /**
         * Get information message about selected server group.
         *
         * @return Information message about selected server group
         */
        public String getServerGroupInfo() {
            return serverGroup != null ? " for server group '" + serverGroup + "'" : "";
        }

        /**
         * Get original command output.
         *
         * @return Raw command output.
         */
        public String getOriginalOutput() {
            return originalOutput;
        }

        // &&&& BEGIN Method for additional information about processing verify check

        /**
         * Additional information about processing verify check.
         *
         * @param request Request of management operations to verify check.
         */
        public void setRequest(String request) {
            this.request = request;
            response = null;
        }

        /**
         * Additional information about processing verify check.
         *
         * @return Request of management operations to verify check.
         */
        public String getRequest() {
            return request;
        }

        /**
         * Additional information about processing verify check.
         *
         * @param response Response of management operations to verify check.
         */
        public void setResponse(String response) {
            this.response = response;
        }

        /**
         * Additional information about processing verify check.
         *
         * @return Response of management operations to verify check.
         */
        public String getResponse() {
            return response;
        }

        // &&&& END   Method for additional information about processing verify check

        @Override
        public int hashCode() {
            int hash = 21 * command.hashCode();
            hash += 37 * originalOutput.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DeploymentInfoResult) {
                DeploymentInfoResult result = (DeploymentInfoResult) obj;
                return command.equals(result.command) && originalOutput.equals(result.originalOutput);
            }
            return false;
        }

    }

    /**
     * Represent container of mainer or optional method arguments
     * Reduce overloading internal methods
     */
    private static class CheckArguments {
        private enum SearchType {
            // Represent searching for status of application deployment
            STATUS
            // Represent searching for deployed application deployment
            , EXIST
            // Represent searching for non-deployed application deployment
            , MISSING
        }

        // Represent result of list of info command, is required for checking
        private final DeploymentInfoResult result;
        // Represent application deployment name
        private String name;
        // Expected state of application deployment for check
        private DeploymentState expectedState;
        private SearchType searchType;
        private CommandContext ctx;
        // Represent string holder for goal of checking to log/error messages
        private String goalStr;

        private CheckArguments(DeploymentInfoResult result) {
            this.result = checkNotNullParam("result", result);
            name = null;
            expectedState = UNKNOWN;
            searchType = SearchType.EXIST;
            ctx = null;
            goalStr = null;
        }

        private DeploymentInfoResult getResult() {
            return result;
        }

        private String getName() {
            return name;
        }

        /**
         * @param name Represent name of application deployment for testing
         * @return Return self, for builder chain
         */
        private CheckArguments setName(String name) {
            this.name = name;
            return this;
        }

        private DeploymentState getExpectedState() {
            return expectedState;
        }

        /**
         * @param expectedState Expected state of application deployment
         * @return Return self, for builder chain
         */
        private CheckArguments setExpectedState(DeploymentState expectedState) {
            this.expectedState = expectedState != null ? expectedState : UNKNOWN;
            searchType = UNKNOWN.equals(this.expectedState) ? SearchType.EXIST : SearchType.STATUS;
            return this;
        }

        private boolean isSearchTypeStatus() {
            return SearchType.STATUS.equals(searchType);
        }

        private boolean isSearchTypeExist() {
            return SearchType.EXIST.equals(searchType);
        }

        private boolean isSearchTypeMissing() {
            return SearchType.MISSING.equals(searchType);
        }

        private CheckArguments setSearchType(SearchType searchType) {
            this.searchType = searchType;
            goalStr = null;
            return this;
        }

        private CommandContext getCtx() {
            return ctx;
        }

        private CheckArguments setCtx(CommandContext ctx) {
            this.ctx = ctx;
            return this;
        }

        private boolean isOutputEmpty() {
            return result.isOutputEmpty();
        }

        private List<String> getRows() {
            return result.getRows();
        }

        /**
         * Lazy loaded cashed message of checking goal to log/error
         *
         * @return Goal message
         */
        private String getGoalStr() {
            if (goalStr == null) {
                goalStr = isSearchTypeStatus() ? " state " + getExpectedState() :
                        isSearchTypeExist() ? " existing in deployment" :
                                isSearchTypeMissing() ? " missing in deployment" :
                                        " UNKNOWN GOAL";
            }
            return goalStr;
        }

        /**
         * Overload to debugging errors
         *
         * @return All information about verification, called parameters and results
         */
        @Override
        public String toString() {
            return "CheckArguments{\n" +
                    "result={\n called_command='" + result.getCommand() + "'" +
                    "\n, server_group='" + result.getServerGroup() + "'" +
                    "\n, command_output={\n" + result.getOriginalOutput() + "\n}" +
                    "\n, request='" + result.getRequest() + "'" +
                    "\n, response={\n" + result.getResponse() + "\n}" +
                    "\n}\n, name='" + name + '\'' +
                    "\n, expectedState=" + expectedState +
                    "\n, searchType=" + searchType +
                    "\n, ctx=" + ctx +
                    "\n, goalStr='" + goalStr + '\'' +
                    "\n}";
        }
    }

    // #### END   Internal containers classes

    // #### BEGIN Public pre-loading methods

    /**
     * Invoke deployment list
     *
     * @param cli CLIWrapper to cli connection and collect raw command output
     * @return Instance of DeploymentInfoResult with command output
     */
    public static DeploymentInfoResult deploymentList(CLIWrapper cli) {
        return callCommand(cli, "deployment list", null);
    }

    /**
     * Invoke deployment info
     * For standalone mode.
     *
     * @param cli CLIWrapper to cli connection and collect raw command output
     * @return Instance of DeploymentInfoResult with command output
     */
    public static DeploymentInfoResult deploymentInfo(CLIWrapper cli) {
        return deploymentInfo(cli, null);
    }

    /**
     * Invoke deployment info
     * For domain mode.
     *
     * @param cli         CLIWrapper to cli connection and collect raw command output
     * @param serverGroup Selected server group in list/info command
     * @return Instance of DeploymentInfoResult with command output
     */
    public static DeploymentInfoResult deploymentInfo(CLIWrapper cli, String serverGroup) {
        String groupPart = serverGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand(cli, "deployment info" + groupPart, serverGroup);
    }

    /**
     * Invoke legacy deployment info
     * For standalone mode.
     *
     * @param cli CLIWrapper to cli connection and collect raw command output
     * @return Instance of DeploymentInfoResult with command output
     */
    public static DeploymentInfoResult legacyDeploymentInfo(CLIWrapper cli) {
        return legacyDeploymentInfo(cli, null);
    }

    /**
     * Invoke legacy deployment info
     * For domain mode.
     *
     * @param cli         CLIWrapper to cli connection and collect raw command output
     * @param serverGroup Selected server group in list/info command
     * @return Instance of DeploymentInfoResult with command output
     */
    public static DeploymentInfoResult legacyDeploymentInfo(CLIWrapper cli, String serverGroup) {
        String groupPart = serverGroup != null ? " --server-group=" + serverGroup : "";
        return callCommand(cli, "deployment-info" + groupPart, serverGroup);
    }
    // #### END   Public pre-loading methods

    // #### BEGIN Method for checking without recalling command for applications deployments state

    /**
     * Checking for existence of application deployment
     *
     * @param result Instance of DeploymentInfoResult with command output
     * @param name   Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name) throws CommandFormatException, IOException {
        check(new CheckArguments(result).setName(name));
    }

    /**
     * Checking for existence of application deployment
     *
     * @param result Instance of DeploymentInfoResult with command output
     * @param name   Represent name of application deployment for testing
     * @param ctx    Represent CommandContext to cli connection and handle management commands
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(new CheckArguments(result).setName(name).setCtx(ctx));
    }

    /**
     * Checking for state and existence of application deployment
     *
     * @param result   Instance of DeploymentInfoResult with command output
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name, DeploymentState expected) throws CommandFormatException, IOException {
        check(new CheckArguments(result).setName(name).setExpectedState(expected));
    }

    /**
     * Checking for state and existence of application deployment
     *
     * @param result   Instance of DeploymentInfoResult with command output
     * @param name     Represent name of application deployment for testing
     * @param expected Expected state of application deployment
     * @param ctx      Represent CommandContext to cli connection and handle management commands
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkExist(DeploymentInfoResult result, String name, DeploymentState expected, CommandContext ctx) throws CommandFormatException, IOException {
        check(new CheckArguments(result).setName(name).setExpectedState(expected).setCtx(ctx));
    }

    /**
     * Checking for non existence of application deployment
     *
     * @param result Instance of DeploymentInfoResult with command output
     * @param name   Represent name of application deployment for testing
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkMissing(DeploymentInfoResult result, String name) throws CommandFormatException, IOException {
        check(new CheckArguments(result).setName(name).setSearchType(CheckArguments.SearchType.MISSING));
    }

    /**
     * Checking for non existence of application deployment.
     *
     * @param result Instance of DeploymentInfoResult with command output
     * @param name   Represent name of application deployment for testing.
     * @param ctx    Represent CommandContext to cli connection and handle management commands
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    public static void checkMissing(DeploymentInfoResult result, String name, CommandContext ctx) throws CommandFormatException, IOException {
        check(new CheckArguments(result).setName(name).setSearchType(CheckArguments.SearchType.MISSING).setCtx(ctx));
    }

    /**
     * Checking for empty installed deployments.
     *
     * @param result Instance of DeploymentInfoResult with command output
     */
    public static void checkEmpty(DeploymentInfoResult result) {
        checkNotNullParam("result", result);
        assertThat("Command output contains some deployments! Checking a void FAILED", result.isOutputEmpty(), is(true));
    }

    /**
     * In case you need know state of application deployment
     *
     * @param result Instance of DeploymentInfoResult with command output
     * @param name   Name of application deployment
     * @return State of application deployment, if not found return UNKNOWN state
     * @throws CommandFormatException
     */
    public static DeploymentState getState(DeploymentInfoResult result, String name) throws CommandFormatException {
        for (String row : result.getRows()) {
            if (row.contains(name)) {
                final DeploymentState[] statuses = DeploymentState.values();

                for (DeploymentState state : statuses) {
                    if (row.contains(state.getTitle())) {
                        log.trace("Application deployment state '" + name + "'->'"
                                + state.getTitle() + " by command '" + result.getCommand() + " Success");
                        return state;
                    }
                }
                log.warn("Status of application deployment not found!\n"
                        + row);
                return UNKNOWN;
            }
        }
        throw new CommandFormatException("No result for " + name + " in \n" + result);
    }
    // #### END   Method for checking without recalling command for applications deployments state

    // #### BEGIN Internal functionality method

    /**
     * Calling command in Cli, processing output
     *
     * @param cli         Open connection into cli
     * @param command     Command for call
     * @param serverGroup Server group name in domain mode
     * @return Instance of DeploymentInfoResult with command output
     */
    private static DeploymentInfoResult callCommand(CLIWrapper cli, String command, String serverGroup) {
        if (cli == null) {
            throw new IllegalStateException("Cli is not connected! Call connectCli method first!");
        }

        cli.sendLine(command);
        log.trace("Called command: '" + command + "'");
        return new DeploymentInfoResult(command, serverGroup, cli.readOutput());
    }

    /**
     * Checking for state or (non)existence of application deployment.
     *
     * @param param Containers of parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void check(CheckArguments param) throws CommandFormatException, IOException {
        if (param.isSearchTypeStatus() && UNKNOWN.equals(param.getExpectedState())) {
            throw new IllegalStateException("Could not verify deployment state " + UNKNOWN + "!");
        }

        if (!param.isOutputEmpty()) {
            for (String row : param.getRows()) {
                if (row.contains(param.getName())) {
                    if (param.isSearchTypeMissing()) {

                        fail("Found non wanted application deployment " +
                                "" + param.getName() + " in \n" + param.getResult());
                    } else if (param.isSearchTypeExist()) {

                        log.trace("Check existence application deployment '" + param.getName() + "' Success");
                        doubleCheck(param);
                        return;
                    } else if (param.isSearchTypeStatus()) {

                        assertThat("Application deployment is not in right state!", row, containsString(param.getExpectedState().getTitle()));
                        log.trace("Check application deployment in right state '" + param.getName() + "'->'"
                                + param.getExpectedState().getTitle() + " by command '" + param.getResult().getCommand() + " Success");
                        doubleCheck(param);
                        return;
                    }

                    fail(param.getName() + " not in right state" + param.getResult().getServerGroupInfo() +
                            "! Expected '" + param.getExpectedState().getTitle() + "' but is\n" + row);
                }
            }
        }
        if (param.isSearchTypeMissing()) {
            log.trace("Check non-existence application deployment '" + param.getName() + "' Success");
            doubleCheck(param);
            return;
        }
        throw new CommandFormatException("No result for " + param.getName() + " in \n" + param.getResult());
    }

    /**
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management operations.
     *
     * @param param Containers of parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void doubleCheck(CheckArguments param) throws CommandFormatException, IOException {
        if (param.getCtx() == null) {
            log.warn("Skip double checking by management trusted commands - CommandContext connection not set!");
            return;
        }
        if (param.isSearchTypeStatus() && UNKNOWN.equals(param.getExpectedState())) {
            throw new IllegalStateException("Could not verify deployment state " + UNKNOWN + "!");
        }

        log.trace("Double checking " + param.getName() + " with management command trusted for " + param.getGoalStr());

        if (param.getCtx().isTerminated()) {
            throw new IllegalStateException("FAILED: Could not double checking " + param.getName() +
                    " with management operations for " + param.getGoalStr() + "!" + "Because connection to cli is closed!");
        }

        if (param.isSearchTypeStatus()) {
            doubleCheckStatus(param);
        } else if (param.isSearchTypeExist()) {
            doubleCheckExist(param);
        } else if (param.isSearchTypeMissing()) {
            doubleCheckMissing(param);
        } else {
            throw new IllegalStateException("Unknown operation selected! Could not verify check!");
        }

        log.trace("Double checking " + param.getName() + " with management operations for " + param.getGoalStr() + " - Success");
    }

    /**
     * In case checking status of application deployment.
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management operations.
     *
     * @param param Containers of parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void doubleCheckStatus(CheckArguments param) throws CommandFormatException, IOException {
        String serverGroupStr = param.getResult().hasServerGroup() ? "/server-group=" + param.getResult().getServerGroup() : "";
        param.getResult().setRequest(serverGroupStr + "/deployment=" + param.getName() + ":read-attribute(name=enabled)");
        ModelNode mn = param.getCtx().buildRequest(param.getResult().getRequest());
        ModelNode response = param.getCtx().getModelControllerClient().execute(mn);
        param.getResult().setResponse(response.asString());

        // State NOT_ADDED is not supported by management command
        if (NOT_ADDED.equals(param.getExpectedState())) {
            // Verify state NOT_ADDED, because is only in Domain mode, for domain mode not-exist deployment in other group
            assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
            assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                    response.get(OUTCOME).asString(), is(FAILED));
            assertThat("No result for " + param.getName(), response.hasDefined(FAILURE_DESCRIPTION), is(true));
            // Verify error message
            assertThat("Wrong error message for missing deployment " + param.getName() + " in server group " + param.getResult().getServerGroup(),
                    response.get(FAILURE_DESCRIPTION).asString(), allOf(
                            containsString("WFLYCTL0216:"),
                            containsString(param.getName())
                    )
            );
        } else {
            // Standard verify with boolean enabled/disabled
            assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
            assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                    response.get(OUTCOME).asString(), is(SUCCESS));
            boolean enable = mapBooleanByDeploymentStatus(param.getExpectedState());
            assertThat("No result for " + param.getName(), response.hasDefined(RESULT), is(true));
            assertThat(param.getName() + " not in right state", response.get(RESULT).asBoolean(), is(enable));
        }
    }

    /**
     * In case checking existence application deployment.
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management operations.
     *
     * @param param Containers of parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void doubleCheckExist(CheckArguments param) throws CommandFormatException, IOException {
        param.getResult().setRequest("/deployment=" + param.getName() + ":read-attribute(name=name)");
        ModelNode mn = param.getCtx().buildRequest(param.getResult().getRequest());
        ModelNode response = param.getCtx().getModelControllerClient().execute(mn);
        param.getResult().setResponse(response.asString());

        assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
        assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                response.get(OUTCOME).asString(), is(SUCCESS));
        assertThat("No result for " + param.getName(), response.hasDefined(RESULT), is(true));
        assertThat(param.getName() + " not in right state", response.get(RESULT).asString(), is(param.getName()));
    }

    /**
     * In case checking non-existence application deployment.
     * Double checking for state and existence of application deployment and verify list and Info result.
     * Verify by management operations.
     *
     * @param param Containers of parameters
     * @throws CommandFormatException Throw in case of assert failure
     * @throws IOException            Throw in case of problem with execute management command
     */
    private static void doubleCheckMissing(CheckArguments param) throws CommandFormatException, IOException {
        param.getResult().setRequest("/deployment=" + param.getName() + ":read-attribute(name=name)");
        ModelNode mn = param.getCtx().buildRequest(param.getResult().getRequest());
        ModelNode response = param.getCtx().getModelControllerClient().execute(mn);
        param.getResult().setResponse(response.asString());

        assertThat("Invalid response for " + param.getName(), response.hasDefined(OUTCOME), is(true));
        assertThat("Verification failed for " + param.getName() + param.getGoalStr() + "!\n" + param,
                response.get(OUTCOME).asString(), is(FAILED));
        assertThat("No result for " + param.getName(), response.hasDefined(FAILURE_DESCRIPTION), is(true));
        // Verify error message
        assertThat("Wrong error message for missing deployment " + param.getName() + " in server group " + param.getResult().getServerGroup(),
                response.get(FAILURE_DESCRIPTION).asString(), allOf(
                        containsString("WFLYCTL0216:"),
                        containsString(param.getName())
                )
        );
    }
    // #### END   Internal functionality method
}
