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

import org.aesh.readline.terminal.Key;
import org.jboss.as.cli.Util;

/**
 * This is a tool for cursor manipulation in BracketsHighlightingTestCase.
 *
 * @author Tomas Terem tterem@redhat.com
 */
public class CursorMovement {

   public static class Builder {

      private StringBuilder instructionsBuilder = new StringBuilder();

      public Builder() { }

      public Builder left(int left){
         for (int i = 0; i < left; i++) {
             if (Util.isWindows()) {
                 this.instructionsBuilder.append(Key.LEFT_2.getKeyValuesAsString());
             } else {
                 this.instructionsBuilder.append(Key.LEFT.getKeyValuesAsString());
             }
         }
         return this;
      }

      public Builder right(int right){
         for (int i = 0; i < right; i++) {
             if (Util.isWindows()) {
                 this.instructionsBuilder.append(Key.RIGHT_2.getKeyValuesAsString());
             } else {
                 this.instructionsBuilder.append(Key.RIGHT.getKeyValuesAsString());
             }

         }
         return this;

      }

      public Builder up(int up){
         for (int i = 0; i < up; i++) {
             if (Util.isWindows()) {
                 this.instructionsBuilder.append(Key.UP_2.getKeyValuesAsString());
             } else {
                 this.instructionsBuilder.append(Key.UP.getKeyValuesAsString());
             }

         }
         return this;

      }

      public Builder down(int down){
         for (int i = 0; i < down; i++) {
             if (Util.isWindows()) {
                 this.instructionsBuilder.append(Key.DOWN_2.getKeyValuesAsString());
             } else {
                 this.instructionsBuilder.append(Key.DOWN.getKeyValuesAsString());
             }

         }
         return this;

      }

      public CursorMovement build() {
         return new CursorMovement(instructionsBuilder.toString());
      }

   }

   private String instructions;

   private CursorMovement(String instructions) {
      this.instructions = instructions;
   }

   @Override
   public String toString() {
      return instructions;
   }
}
