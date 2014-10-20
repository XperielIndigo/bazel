// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.testutiltests;

import static com.google.devtools.build.lib.testutil.JunitTestUtils.assertContainsSublist;
import static com.google.devtools.build.lib.testutil.JunitTestUtils.assertDoesNotContainSublist;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import junit.framework.AssertionFailedError;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link com.google.devtools.build.lib.testutil.JunitTestUtils}.
 */
@RunWith(JUnit4.class)
public class JunitTestUtilsTest {

  @Test
  public void testAssertContainsSublistSuccess() {
    List<String> actual = Arrays.asList("a", "b", "c");

    // All single-string combinations.
    assertContainsSublist(actual, "a");
    assertContainsSublist(actual, "b");
    assertContainsSublist(actual, "c");

    // All two-string combinations.
    assertContainsSublist(actual, "a", "b");
    assertContainsSublist(actual, "b", "c");

    // The whole list.
    assertContainsSublist(actual, "a", "b", "c");
  }

  @Test
  public void testAssertContainsSublistFailure() {
    List<String> actual = Arrays.asList("a", "b", "c");

    try {
      assertContainsSublist(actual, "d");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Did not find [d] as a sublist of [a, b, c]",
                   e.getMessage());
    }

    try {
      assertContainsSublist(actual, "a", "c");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Did not find [a, c] as a sublist of [a, b, c]",
                   e.getMessage());
    }

    try {
      assertContainsSublist(actual, "b", "c", "d");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Did not find [b, c, d] as a sublist of [a, b, c]",
                   e.getMessage());
    }
  }

  @Test
  public void testAssertDoesNotContainSublistSuccess() {
    List<String> actual = Arrays.asList("a", "b", "c");
    assertDoesNotContainSublist(actual, "d");
    assertDoesNotContainSublist(actual, "a", "c");
    assertDoesNotContainSublist(actual, "b", "c", "d");
  }

  @Test
  public void testAssertDoesNotContainSublistFailure() {
    List<String> actual = Arrays.asList("a", "b", "c");

    // All single-string combinations.
    try {
      assertDoesNotContainSublist(actual, "a");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Found [a] as a sublist of [a, b, c]",
                   e.getMessage());
    }
    try {
      assertDoesNotContainSublist(actual, "b");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Found [b] as a sublist of [a, b, c]",
                   e.getMessage());
    }
    try {
      assertDoesNotContainSublist(actual, "c");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Found [c] as a sublist of [a, b, c]",
                   e.getMessage());
    }

    // All two-string combinations.
    try {
      assertDoesNotContainSublist(actual, "a", "b");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Found [a, b] as a sublist of [a, b, c]",
                   e.getMessage());
    }
    try {
      assertDoesNotContainSublist(actual, "b", "c");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Found [b, c] as a sublist of [a, b, c]",
                   e.getMessage());
    }

    // The whole list.
    try {
      assertDoesNotContainSublist(actual, "a", "b", "c");
      fail("no exception thrown");
    } catch (AssertionFailedError e) {
      assertEquals("failure message generated by JunitTestUtils",
                   "Found [a, b, c] as a sublist of [a, b, c]",
                   e.getMessage());
    }
  }

}
