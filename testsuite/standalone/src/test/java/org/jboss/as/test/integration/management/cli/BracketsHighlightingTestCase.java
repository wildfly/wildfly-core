/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * These tests check highlighting of matching open/close brackets in cli
 *
 * Structure of each test is:
 * - Prepare command
 * - Prepare instructions for cursor movement using CursorMovement.Builder
 * - Push command and instructions to cli
 * - Prepare ANSI sequence describing expected cursor movement and character highlighting using AnsiSequence.Builder
 * - Check cli output for expected ANSI sequence
 *
 * @author Tomas Terem tterem@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class BracketsHighlightingTestCase {

   private static CliProcessWrapper cli;
   private static String hostAndPort = TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();

   /*
    * Usually, when moving a cursor, relative numbers are not used, it rather
    * move to the start of the line and then move back to the desired position
    * For example: 2 to the left == 50 to the left, and then 48 to the right
    * Host address is part of the line and its length can vary, which is affecting these hardcoded numbers
    * This attribute adjust this difference
    */
   private static int diff = hostAndPort.length() - "127.0.0.1:9990".length();

   /**
    * Initialize CommandContext before all tests
    */
   @BeforeClass
   public static void init() {
      cli = new CliProcessWrapper()
            .addCliArgument("--connect")
            .addCliArgument("--controller=" + hostAndPort);
      cli.executeInteractive();
   }

   @Before
   public void clearOutput() {
      cli.clearOutput();
   }

   /**
    * Terminate CommandContext after all tests are executed
    */
   @AfterClass
   public static void close() {
      cli.destroyProcess();
   }

   /**
    * Write expression '()' and move cursor 1 to the left
    * Cursor will be on ')', so '(' will be highlighted
    * Then highlighting will be removed and cursor will be moved to the end of the line
    * @throws Exception
    */
   @Test
   public void testBasic() throws Exception {
      // prepare cli command
      String command = "()";

      // prepare instructions to move cursor
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(1)
            .build();

      // prepare expected ansi sequence
      AnsiSequence expectedSequence = new AnsiSequence.Builder(diff)
            .leftAbsolute(1)
            .saveCursor()
            .left(31)
            .right(30)
            .undoHighlight('(')
            .leftRestoreSave()
            .left(31)
            .right(31)
            .undoHighlight(')')
            .leftRestoreSave()
            .left(31)
            .right(30)
            .greenHighlight('(')
            .leftAndRestore()
            .rightAbsolute(1)
            .saveCursor()
            .left(32)
            .right(30)
            .undoHighlight('(')
            .leftAndRestore()
            .build();

      // execute cli
      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());

      // get output
      String out = cli.getOutput();

      // check if expected sequence is present on output, if no, show the diff
      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }

   /**
    * Write expression '()' and move cursor 2 to the left
    * First cursor will be on ')', so '(' will be highlighted
    * Then it moves to '(', so its highlighting will be removed and ')' will be highlighted
    * Then ')' highlighting will be removed as well and cursor will be moved to the end of the line
    * @throws Exception
    */
   @Test
   public void testBasicTwoLeft() throws Exception {
      String command = "()";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(2)
            .build();

      AnsiSequence expectedSequence = new AnsiSequence.Builder(diff)
            .leftAbsolute(1)
            .saveCursor()
            .left(31)
            .right(30)
            .undoHighlight('(')
            .leftRestoreSave()
            .left(31)
            .right(31)
            .undoHighlight(')')
            .leftRestoreSave()
            .left(31)
            .right(30)
            .greenHighlight('(')
            .leftAndRestore()
            .leftAbsolute(1)
            .saveCursor()
            .left(30)
            .right(30)
            .undoHighlight('(')
            .leftRestoreSave()
            .left(30)
            .right(30)
            .undoHighlight('(')
            .leftRestoreSave()
            .left(30)
            .right(31)
            .undoHighlight(')')
            .leftRestoreSave()
            .left(30)
            .right(31)
            .greenHighlight(')')
            .leftAndRestore()
            .rightAbsolute(2)
            .saveCursor()
            .left(32)
            .right(31)
            .undoHighlight(')')
            .leftAndRestore()
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());
      String out = cli.getOutput();

      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }

   /**
    * Write expression '([){]}' and move cursor through the whole expression to the left and then back to the right
    * Check output for expected ANSI sequence describing cursor movement and highlighting
    * @throws Exception
    */
   @Test
   public void testLeftAndRightComplexExpression() throws Exception {
      String command = "([){]}";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(6)
            .right(6)
            .build();

      /*
       * There is visualisation of how text in console looks like next to each line
       * _x means that cursor is on character x
       * *x* means that x is highlighted with green color
       * ^x^ means that x is highlighted with red color
       */
      AnsiSequence expectedSequence = new AnsiSequence.Builder(diff)                                  // ([){]}_
            .leftAbsolute(1).saveCursor().left(35).right(33).undoHighlight('{')                       // ([){]_}
            .leftRestoreSave().left(35).right(35).undoHighlight('}')                                  // ([){]_}
            .leftRestoreSave().left(35).right(34).redHighlight(']')                                   // ([){^]^_}
            .leftRestoreSave().left(35).right(33).greenHighlight('{')                                 // ([)*{*^]^_}
            .leftAndRestore().leftAbsolute(1).saveCursor().left(34).right(33).undoHighlight('{')      // ([){_^]^}
            .leftRestoreSave().left(34).right(34).undoHighlight(']')                                  // ([){_]}
            .leftRestoreSave().left(34).right(31).undoHighlight('[')                                  // ([){_]}
            .leftRestoreSave().left(34).right(34).undoHighlight(']')                                  // ([){_]}
            .leftRestoreSave().left(34).right(32).redHighlight(')')                                   // ([^)^{_]}
            .leftRestoreSave().left(34).right(33).redHighlight('{')                                   // ([^)^^{^_]}
            .leftRestoreSave().left(34).right(31).greenHighlight('[')                                 // (*[*^)^^{^_]}
            .leftAndRestore().leftAbsolute(1).saveCursor().left(33).right(31).undoHighlight('[')      // ([^)^_^{^]}
            .leftRestoreSave().left(33).right(32).undoHighlight(')')                                  // ([)^_{^]}
            .leftRestoreSave().left(33).right(33).undoHighlight('{')                                  // ([)_{]}
            .leftRestoreSave().left(33).right(33).undoHighlight('{')                                  // ([)_{]}
            .leftRestoreSave().left(33).right(35).undoHighlight('}')                                  // ([)_{]}
            .leftRestoreSave().left(33).right(34).redHighlight(']')                                   // ([)_{^]^}
            .leftRestoreSave().left(33).right(35).greenHighlight('}')                                 // ([)_{^]^*}*
            .leftAndRestore().leftAbsolute(1).saveCursor().left(32).right(35).undoHighlight('}')      // ([_){^]^}
            .leftRestoreSave().left(32).right(34).undoHighlight(']')                                  // ([_){]}
            .leftRestoreSave().left(32).right(30).undoHighlight('(')                                  // ([_){]}
            .leftRestoreSave().left(32).right(32).undoHighlight(')')                                  // ([_){]}
            .leftRestoreSave().left(32).right(31).redHighlight('[')                                   // (^[^_){]}
            .leftRestoreSave().left(32).right(30).greenHighlight('(')                                 // *(*^[^_){]}
            .leftAndRestore().leftAbsolute(1).saveCursor().left(31).right(30).undoHighlight('(')      // (_^[^){]}
            .leftRestoreSave().left(31).right(31).undoHighlight('[')                                  // (_[){]}
            .leftRestoreSave().left(31).right(31).undoHighlight('[')                                  // (_[){]}
            .leftRestoreSave().left(31).right(34).undoHighlight(']')                                  // (_[){]}
            .leftRestoreSave().left(31).right(32).redHighlight(')')                                   // (_[^)^{]}
            .leftRestoreSave().left(31).right(33).redHighlight('{')                                   // (_[^)^^{^]}
            .leftRestoreSave().left(31).right(34).greenHighlight(']')                                 // (_[^)^^{^*]*}
            .leftAndRestore().leftAbsolute(1).saveCursor().left(30).right(34).undoHighlight(']')      // _([^)^^{^]}
            .leftRestoreSave().left(30).right(32).undoHighlight(')')                                  // _([)^{^]}
            .leftRestoreSave().left(30).right(33).undoHighlight('{')                                  // _([){]}
            .leftRestoreSave().left(30).right(30).undoHighlight('(')                                  // _([){]}
            .leftRestoreSave().left(30).right(32).undoHighlight(')')                                  // _([){]}
            .leftRestoreSave().left(30).right(31).redHighlight('[')                                   // _(^[^){]}
            .leftRestoreSave().left(30).right(32).greenHighlight(')')                                 // _(^[^*)*{]}
            .leftAndRestore().rightAbsolute(1).saveCursor().left(31).right(32).undoHighlight(')')     // (_^[^){]}
            .leftRestoreSave().left(31).right(31).undoHighlight('[')                                  // (_[){]}
            .leftRestoreSave().left(31).right(31).undoHighlight('[')                                  // (_[){]}
            .leftRestoreSave().left(31).right(34).undoHighlight(']')                                  // (_[){]}
            .leftRestoreSave().left(31).right(32).redHighlight(')')                                   // (_[^)^{]}
            .leftRestoreSave().left(31).right(33).redHighlight('{')                                   // (_[^)^^{^]}
            .leftRestoreSave().left(31).right(34).greenHighlight(']')                                 // (_[^)^^{^*]*}
            .leftAndRestore().rightAbsolute(1).saveCursor().left(32).right(34).undoHighlight(']')     // ([_^)^^{^]}
            .leftRestoreSave().left(32).right(32).undoHighlight(')')                                  // ([_)^{^]}
            .leftRestoreSave().left(32).right(33).undoHighlight('{')                                  // ([_){]}
            .leftRestoreSave().left(32).right(30).undoHighlight('(')                                  // ([_){]}
            .leftRestoreSave().left(32).right(32).undoHighlight(')')                                  // ([_){]}
            .leftRestoreSave().left(32).right(31).redHighlight('[')                                   // (^[^_){]}
            .leftRestoreSave().left(32).right(30).greenHighlight('(')                                 // *(*^[^_){]}
            .leftAndRestore().rightAbsolute(1).saveCursor().left(33).right(30).undoHighlight('(')     // (^[^)_{]}
            .leftRestoreSave().left(33).right(31).undoHighlight('[')                                  // ([)_{]}
            .leftRestoreSave().left(33).right(33).undoHighlight('{')                                  // ([)_{]}
            .leftRestoreSave().left(33).right(35).undoHighlight('}')                                  // ([)_{]}
            .leftRestoreSave().left(33).right(34).redHighlight(']')                                   // ([)_{^]^}
            .leftRestoreSave().left(33).right(35).greenHighlight('}')                                 // ([)_{^]^*}*
            .leftAndRestore().rightAbsolute(1).saveCursor().left(34).right(35).undoHighlight('}')     // ([){_^]^}
            .leftRestoreSave().left(34).right(34).undoHighlight(']')                                  // ([){_]}
            .leftRestoreSave().left(34).right(31).undoHighlight('[')                                  // ([){_]}
            .leftRestoreSave().left(34).right(34).undoHighlight(']')                                  // ([){_]}
            .leftRestoreSave().left(34).right(32).redHighlight(')')                                   // ([^)^{_]}
            .leftRestoreSave().left(34).right(33).redHighlight('{')                                   // ([^)^^{^_]}
            .leftRestoreSave().left(34).right(31).greenHighlight('[')                                 // (*[*^)^^{^_]}
            .leftAndRestore().rightAbsolute(1).saveCursor().left(35).right(31).undoHighlight('[')     // ([^)^^{^]_}
            .leftRestoreSave().left(35).right(32).undoHighlight(')')                                  // ([)^{^]_}
            .leftRestoreSave().left(35).right(33).undoHighlight('{')                                  // ([){]_}
            .leftRestoreSave().left(35).right(33).undoHighlight('{')                                  // ([){]_}
            .leftRestoreSave().left(35).right(35).undoHighlight('}')                                  // ([){]_}
            .leftRestoreSave().left(35).right(34).redHighlight(']')                                   // ([){^]^_}
            .leftRestoreSave().left(35).right(33).greenHighlight('{')                                 // ([)*{*^]^_}
            .leftAndRestore().rightAbsolute(1).saveCursor().left(36).right(33).undoHighlight('{')     // ([){^]^}_
            .leftRestoreSave().left(36).right(34).undoHighlight(']')                                  // ([){]}_
            .leftAndRestore()
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());
      String out = cli.getOutput();

      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }

   /**
    * Write '/abc:add({a=[], b=[], c={[],()}})' and go through the whole expression by moving cursor to the left
    * Check output for expected ANSI sequence describing cursor movement and highlighting
    * @throws Exception
    */
   @Test
   public void testWellFormedExpression() throws Exception {
      String command = "/abc:add({a=[], b=[], c={[],()}})";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(33)
            .build();

      AnsiSequence expectedSequence = new AnsiSequence.Builder(diff)
            .leftAbsolute(1).saveCursor().left(62).right(38).undoHighlight('(')
            .leftRestoreSave().left(62).right(59).undoHighlight(')')
            .leftRestoreSave().left(62).right(62).undoHighlight(')')
            .leftRestoreSave().left(62).right(39).undoHighlight('{')
            .leftRestoreSave().left(62).right(60).undoHighlight('}')
            .leftRestoreSave().left(62).right(61).undoHighlight('}')
            .leftRestoreSave().left(62).right(42).undoHighlight('[')
            .leftRestoreSave().left(62).right(43).undoHighlight(']')
            .leftRestoreSave().left(62).right(49).undoHighlight(']')
            .leftRestoreSave().left(62).right(56).undoHighlight(']')
            .leftRestoreSave().left(62).right(48).undoHighlight('[')
            .leftRestoreSave().left(62).right(55).undoHighlight('[')
            .leftRestoreSave().left(62).right(54).undoHighlight('{')
            .leftRestoreSave().left(62).right(58).undoHighlight('(')
            .leftRestoreSave().left(62).right(38).greenHighlight('(')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(61).right(38).undoHighlight('(')
            .leftRestoreSave().left(61).right(39).undoHighlight('{')
            .leftRestoreSave().left(61).right(60).undoHighlight('}')
            .leftRestoreSave().left(61).right(61).undoHighlight('}')
            .leftRestoreSave().left(61).right(42).undoHighlight('[')
            .leftRestoreSave().left(61).right(43).undoHighlight(']')
            .leftRestoreSave().left(61).right(49).undoHighlight(']')
            .leftRestoreSave().left(61).right(56).undoHighlight(']')
            .leftRestoreSave().left(61).right(48).undoHighlight('[')
            .leftRestoreSave().left(61).right(55).undoHighlight('[')
            .leftRestoreSave().left(61).right(54).undoHighlight('{')
            .leftRestoreSave().left(61).right(58).undoHighlight('(')
            .leftRestoreSave().left(61).right(59).undoHighlight(')')
            .leftRestoreSave().left(61).right(39).greenHighlight('{')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(60).right(39).undoHighlight('{')
            .leftRestoreSave().left(60).right(54).undoHighlight('{')
            .leftRestoreSave().left(60).right(60).undoHighlight('}')
            .leftRestoreSave().left(60).right(55).undoHighlight('[')
            .leftRestoreSave().left(60).right(56).undoHighlight(']')
            .leftRestoreSave().left(60).right(58).undoHighlight('(')
            .leftRestoreSave().left(60).right(59).undoHighlight(')')
            .leftRestoreSave().left(60).right(54).greenHighlight('{')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(59).right(54).undoHighlight('{')
            .leftRestoreSave().left(59).right(58).undoHighlight('(')
            .leftRestoreSave().left(59).right(59).undoHighlight(')')
            .leftRestoreSave().left(59).right(58).greenHighlight('(')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(58).right(58).undoHighlight('(')
            .leftRestoreSave().left(58).right(58).undoHighlight('(')
            .leftRestoreSave().left(58).right(59).undoHighlight(')')
            .leftRestoreSave().left(58).right(59).greenHighlight(')')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(57).right(59).undoHighlight(')')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(56).right(55).undoHighlight('[')
            .leftRestoreSave().left(56).right(56).undoHighlight(']')
            .leftRestoreSave().left(56).right(55).greenHighlight('[')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(55).right(55).undoHighlight('[')
            .leftRestoreSave().left(55).right(55).undoHighlight('[')
            .leftRestoreSave().left(55).right(56).undoHighlight(']')
            .leftRestoreSave().left(55).right(56).greenHighlight(']')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(54).right(56).undoHighlight(']')
            .leftRestoreSave().left(54).right(54).undoHighlight('{')
            .leftRestoreSave().left(54).right(60).undoHighlight('}')
            .leftRestoreSave().left(54).right(55).undoHighlight('[')
            .leftRestoreSave().left(54).right(56).undoHighlight(']')
            .leftRestoreSave().left(54).right(58).undoHighlight('(')
            .leftRestoreSave().left(54).right(59).undoHighlight(')')
            .leftRestoreSave().left(54).right(60).greenHighlight('}')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(53).right(60).undoHighlight('}')
            .leftAndRestore().leftAbsolute(1).leftAbsolute(1).leftAbsolute(1).leftAbsolute(1).saveCursor().left(49).right(48).undoHighlight('[')
            .leftRestoreSave().left(49).right(49).undoHighlight(']')
            .leftRestoreSave().left(49).right(48).greenHighlight('[')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(48).right(48).undoHighlight('[')
            .leftRestoreSave().left(48).right(48).undoHighlight('[')
            .leftRestoreSave().left(48).right(49).undoHighlight(']')
            .leftRestoreSave().left(48).right(49).greenHighlight(']')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(47).right(49).undoHighlight(']')
            .leftAndRestore().leftAbsolute(1).leftAbsolute(1).leftAbsolute(1).leftAbsolute(1).saveCursor().left(43).right(42).undoHighlight('[')
            .leftRestoreSave().left(43).right(43).undoHighlight(']')
            .leftRestoreSave().left(43).right(42).greenHighlight('[')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(42).right(42).undoHighlight('[')
            .leftRestoreSave().left(42).right(42).undoHighlight('[')
            .leftRestoreSave().left(42).right(43).undoHighlight(']')
            .leftRestoreSave().left(42).right(43).greenHighlight(']')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(41).right(43).undoHighlight(']')
            .leftAndRestore().leftAbsolute(1).leftAbsolute(1).saveCursor().left(39).right(39).undoHighlight('{')
            .leftRestoreSave().left(39).right(60).undoHighlight('}')
            .leftRestoreSave().left(39).right(61).undoHighlight('}')
            .leftRestoreSave().left(39).right(42).undoHighlight('[')
            .leftRestoreSave().left(39).right(43).undoHighlight(']')
            .leftRestoreSave().left(39).right(49).undoHighlight(']')
            .leftRestoreSave().left(39).right(56).undoHighlight(']')
            .leftRestoreSave().left(39).right(48).undoHighlight('[')
            .leftRestoreSave().left(39).right(55).undoHighlight('[')
            .leftRestoreSave().left(39).right(54).undoHighlight('{')
            .leftRestoreSave().left(39).right(58).undoHighlight('(')
            .leftRestoreSave().left(39).right(59).undoHighlight(')')
            .leftRestoreSave().left(39).right(61).greenHighlight('}')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(38).right(61).undoHighlight('}')
            .leftRestoreSave().left(38).right(38).undoHighlight('(')
            .leftRestoreSave().left(38).right(59).undoHighlight(')')
            .leftRestoreSave().left(38).right(62).undoHighlight(')')
            .leftRestoreSave().left(38).right(39).undoHighlight('{')
            .leftRestoreSave().left(38).right(60).undoHighlight('}')
            .leftRestoreSave().left(38).right(61).undoHighlight('}')
            .leftRestoreSave().left(38).right(42).undoHighlight('[')
            .leftRestoreSave().left(38).right(43).undoHighlight(']')
            .leftRestoreSave().left(38).right(49).undoHighlight(']')
            .leftRestoreSave().left(38).right(56).undoHighlight(']')
            .leftRestoreSave().left(38).right(48).undoHighlight('[')
            .leftRestoreSave().left(38).right(55).undoHighlight('[')
            .leftRestoreSave().left(38).right(54).undoHighlight('{')
            .leftRestoreSave().left(38).right(58).undoHighlight('(')
            .leftRestoreSave().left(38).right(62).greenHighlight(')')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(37).right(62).undoHighlight(')')
            .leftAndRestore().leftAbsolute(1).leftAbsolute(1).leftAbsolute(1).leftAbsolute(1)
            .leftAbsolute(1).leftAbsolute(1).leftAbsolute(1)
            .rightAbsolute(33)
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());
      String out = cli.getOutput();

      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }

   /**
    * Write long multiline expression and move the cursor left through the whole expression
    * Check output for expected ANSI sequence describing cursor movement and highlighting
    * @throws Exception
    */
   @Test
   public void testMultilineExpression() throws Exception {
      String command = "[{()123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_()}]";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(188)
            .build();

      AnsiSequence.Builder builder = new AnsiSequence.Builder(diff)
            .leftAbsolute(1).saveCursor().up(1).left(57).right(30).undoHighlight('[')
            .leftRestoreSave().left(57).right(57).undoHighlight(']')
            .leftRestoreSave().up(1).left(57).right(31).undoHighlight('{')
            .leftRestoreSave().left(57).right(56).undoHighlight('}')
            .leftRestoreSave().up(1).left(57).right(32).undoHighlight('(')
            .leftRestoreSave().up(1).left(57).right(33).undoHighlight(')')
            .leftRestoreSave().left(57).right(55).undoHighlight(')')
            .leftRestoreSave().left(57).right(54).undoHighlight('(')
            .leftRestoreSave().up(1).left(57).right(30).greenHighlight('[')
            .leftAndRestore().leftAbsolute(1).saveCursor().up(1).left(56).right(30).undoHighlight('[')
            .leftRestoreSave().up(1).left(56).right(31).undoHighlight('{')
            .leftRestoreSave().left(56).right(56).undoHighlight('}')
            .leftRestoreSave().up(1).left(56).right(32).undoHighlight('(')
            .leftRestoreSave().up(1).left(56).right(33).undoHighlight(')')
            .leftRestoreSave().left(56).right(55).undoHighlight(')')
            .leftRestoreSave().left(56).right(54).undoHighlight('(')
            .leftRestoreSave().up(1).left(56).right(31).greenHighlight('{')
            .leftAndRestore().leftAbsolute(1).saveCursor().up(1).left(55).right(31).undoHighlight('{')
            .leftRestoreSave().left(55).right(54).undoHighlight('(')
            .leftRestoreSave().left(55).right(55).undoHighlight(')')
            .leftRestoreSave().left(55).right(54).greenHighlight('(')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(54).right(54).undoHighlight('(')
            .leftRestoreSave().left(54).right(54).undoHighlight('(')
            .leftRestoreSave().left(54).right(55).undoHighlight(')')
            .leftRestoreSave().left(54).right(55).greenHighlight(')')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(53).right(55).undoHighlight(')')
            .leftAndRestore();

      for (int i = 0; i < 53 + diff; i++) {
         builder = builder.leftAbsolute(1);
      }

      builder.up(1).rightAbsolute(159);

      for (int i = 0; i < 126 - diff; i++) {
         builder = builder.leftAbsolute(1);
      }

      builder.saveCursor().left(33).right(32).undoHighlight('(')
            .leftRestoreSave().left(33).right(33).undoHighlight(')')
            .leftRestoreSave().left(33).right(32).greenHighlight('(')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(32).right(32).undoHighlight('(')
            .leftRestoreSave().left(32).right(32).undoHighlight('(')
            .leftRestoreSave().left(32).right(33).undoHighlight(')')
            .leftRestoreSave().left(32).right(33).greenHighlight(')')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(31).right(33).undoHighlight(')')
            .leftRestoreSave().left(31).right(31).undoHighlight('{')
            .leftRestoreSave().down(1).left(31).right(56).undoHighlight('}')
            .leftRestoreSave().left(31).right(32).undoHighlight('(')
            .leftRestoreSave().left(31).right(33).undoHighlight(')')
            .leftRestoreSave().down(1).left(31).right(55).undoHighlight(')')
            .leftRestoreSave().down(1).left(31).right(54).undoHighlight('(')
            .leftRestoreSave().down(1).left(31).right(56).greenHighlight('}')
            .leftAndRestore().leftAbsolute(1).saveCursor().down(1).left(30).right(56).undoHighlight('}')
            .leftRestoreSave().left(30).right(30).undoHighlight('[')
            .leftRestoreSave().down(1).left(30).right(57).undoHighlight(']')
            .leftRestoreSave().left(30).right(31).undoHighlight('{')
            .leftRestoreSave().down(1).left(30).right(56).undoHighlight('}')
            .leftRestoreSave().left(30).right(32).undoHighlight('(')
            .leftRestoreSave().left(30).right(33).undoHighlight(')')
            .leftRestoreSave().down(1).left(30).right(55).undoHighlight(')')
            .leftRestoreSave().down(1).left(30).right(54).undoHighlight('(')
            .leftRestoreSave().down(1).left(30).right(57).greenHighlight(']')
            .leftAndRestore().down(1).rightAbsolute(28).saveCursor().left(58).right(57).undoHighlight(']')
            .leftAndRestore();

      AnsiSequence expectedSequence = builder.build();

      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());
      String out = cli.getOutput();

      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }

   /**
    * Start cli with '--no-character-highlight' option
    * Write expression '()' and move cursor 2 to the left
    * Check output for expected ANSI sequence describing only cursor movement and no highlighting
    * @throws Exception
    */
   @Test
   public void testDisableHighlighting() throws Exception {
      CliProcessWrapper cli = new CliProcessWrapper()
            .addCliArgument("--connect")
            .addCliArgument("--controller=" + hostAndPort)
            .addCliArgument("--no-character-highlight");
      cli.executeInteractive();

      String command = "()";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(2)
            .build();

      AnsiSequence expectedSequence = new AnsiSequence.Builder(diff)
            .leftAbsolute(1).leftAbsolute(1).rightAbsolute(2)
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());
      String out = cli.getOutput();

      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }

   /**
    * Start cli with '--no-color-output' option
    * Write expression '()' and move cursor 2 to the left
    * Check output for expected ANSI sequence describing cursor movement and highlighting only with white instead of red/green
    * @throws Exception
    */
   @Test
   public void testDisableColorOutput() throws Exception {
      CliProcessWrapper cli = new CliProcessWrapper()
            .addCliArgument("--connect")
            .addCliArgument("--controller=" + hostAndPort)
            .addCliArgument("--no-color-output");
      cli.executeInteractive();

      String command = "([)";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(3)
            .build();

      AnsiSequence expectedSequence = new AnsiSequence.Builder(diff)
            .leftAbsolute(1).saveCursor().left(32).right(30).undoHighlight('(')
            .leftRestoreSave().left(32).right(32).undoHighlight(')')
            .leftRestoreSave().left(32).right(31).whiteHighlight('[')
            .leftRestoreSave().left(32).right(30).whiteHighlight('(')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(31).right(30).undoHighlight('(')
            .leftRestoreSave().left(31).right(31).undoHighlight('[')
            .leftAndRestore().leftAbsolute(1).saveCursor().left(30).right(30).undoHighlight('(')
            .leftRestoreSave().left(30).right(30).undoHighlight('(')
            .leftRestoreSave().left(30).right(32).undoHighlight(')')
            .leftRestoreSave().left(30).right(31).whiteHighlight('[')
            .leftRestoreSave().left(30).right(32).whiteHighlight(')')
            .leftAndRestore().rightAbsolute(3).saveCursor().left(33).right(32).undoHighlight(')')
            .leftRestoreSave().left(33).right(31).undoHighlight('[')
            .leftAndRestore()
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement, expectedSequence.toString());
      String out = cli.getOutput();

      if (!out.contains(expectedSequence.toString())) {
         Assert.assertEquals(expectedSequence, out);
      }
   }
}
