/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link ControlledProcessState}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ControlledProcessStateUnitTestCase {

    /** Test the AS7-1103 scenario */
    @Test
    public void testSetRunningRequiresStarting() {

        ControlledProcessState state = new ControlledProcessState(true);
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setRunning(); // in AS7-1103 bug, another thread did this
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());

        state = new ControlledProcessState(true);
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    /** Test the AS7-5929 scenario -- a reload should not clear RESTART_REQUIRED state */
    @Test
    public void testRestartRequiredRequiresRestart() {

        ControlledProcessState state = new ControlledProcessState(true);
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

        // Now simulate a :reload
        state.setStopping();
        state.setStarting();
        state.setRunning();

        // Validate the RESTART_REQUIRED state still pertains
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

    }

    @Test
    public void test_restartRequiredRequiresRestart_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

        // Now simulate a :reload
        state.setStopping();
        state.setStarting();
        state.setRunning();

        // Validate the RESTART_REQUIRED state still pertains
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_reloadRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        // Check it holds the status
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());

        state.setStopping();
        state.setStarting();
        state.setRunning();

        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        // Check it holds the status
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());

        state.setStopping();
        state.setStarting();
        state.setRunning();

        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_restartRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        // Check it holds the status
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_restartRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        // Check it holds the status
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_revert_onStarting_restartRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onRunning_restartRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onRunning_restartRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onStarting_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onRunning_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onRunning_reloadRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }


    @Test
    public void test_notSupported_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_notSupported_reloadRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }






    @Test
    public void test_revert_onStarting_notSupported_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onRunning_notSupported_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_onRunning_notSupported_reloadRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void testStandardStartStop() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setStopping();
        Assert.assertEquals(ControlledProcessState.State.STOPPING, state.getState());
        state.setStopped();
        Assert.assertEquals(ControlledProcessState.State.STOPPED, state.getState());
    }

    @Test
    public void test_reloadRequired_onRunning_after_restartRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_reloadRequired_onRunning_after_restartRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_reloadRequired_onStarting_after_restartRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }









    @Test
    public void test_restartRequired_onRunning_after_reloadRequired_onRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_restartRequired_onRunning_after_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_restartRequired_onStarting_after_reloadRequired_onStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }
}
