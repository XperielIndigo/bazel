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
package com.google.devtools.build.lib.vfs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.devtools.build.lib.testutil.MoreAsserts;
import com.google.devtools.build.lib.testutil.TestUtils;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests {@link UnixGlob}
 */
@RunWith(JUnit4.class)
public class GlobTest {

  private Path tmpPath;
  private FileSystem fs;
  @Before
  public void setUp() throws Exception {
    fs = new InMemoryFileSystem();
    tmpPath = fs.getPath("/globtmp");
    for (String dir : ImmutableList.of("foo/bar/wiz",
                         "foo/barnacle/wiz",
                         "food/barnacle/wiz",
                         "fool/barnacle/wiz")) {
      FileSystemUtils.createDirectoryAndParents(tmpPath.getRelative(dir));
    }
    FileSystemUtils.createEmptyFile(tmpPath.getRelative("foo/bar/wiz/file"));
  }

  @Test
  public void testQuestionMarkMatch() throws Exception {
    assertGlobMatches("foo?", /* => */"food", "fool");
  }

  @Test
  public void testQuestionMarkNoMatch() throws Exception {
    assertGlobMatches("food/bar?" /* => nothing */);
  }

  @Test
  public void testStartsWithStar() throws Exception {
    assertGlobMatches("*oo", /* => */"foo");
  }

  @Test
  public void testStartsWithStarWithMiddleStar() throws Exception {
    assertGlobMatches("*f*o", /* => */"foo");
  }

  @Test
  public void testEndsWithStar() throws Exception {
    assertGlobMatches("foo*", /* => */"foo", "food", "fool");
  }

  @Test
  public void testEndsWithStarWithMiddleStar() throws Exception {
    assertGlobMatches("f*oo*", /* => */"foo", "food", "fool");
  }

  @Test
  public void testMiddleStar() throws Exception {
    assertGlobMatches("f*o", /* => */"foo");
  }

  @Test
  public void testTwoMiddleStars() throws Exception {
    assertGlobMatches("f*o*o", /* => */"foo");
  }

  @Test
  public void testSingleStarPatternWithNamedChild() throws Exception {
    assertGlobMatches("*/bar", /* => */"foo/bar");
  }

  @Test
  public void testSingleStarPatternWithChildGlob() throws Exception {
    assertGlobMatches("*/bar*", /* => */
        "foo/bar", "foo/barnacle", "food/barnacle", "fool/barnacle");
  }

  @Test
  public void testSingleStarAsChildGlob() throws Exception {
    assertGlobMatches("foo/*/wiz", /* => */"foo/bar/wiz", "foo/barnacle/wiz");
  }

  @Test
  public void testNoAsteriskAndFilesDontExist() throws Exception {
    // Note un-UNIX like semantics:
    assertGlobMatches("ceci/n'est/pas/une/globbe" /* => nothing */);
  }

  @Test
  public void testSingleAsteriskUnderNonexistentDirectory() throws Exception {
    // Note un-UNIX like semantics:
    assertGlobMatches("not-there/*" /* => nothing */);
  }

  @Test
  public void testGlobWithNonExistentBase() throws Exception {
    Collection<Path> globResult = UnixGlob.forPath(fs.getPath("/does/not/exist"))
        .addPattern("*.txt")
        .globInterruptible();
    assertThat(globResult).isEmpty();
  }

  @Test
  public void testGlobUnderFile() throws Exception {
    assertGlobMatches("foo/bar/wiz/file/*" /* => nothing */);
  }

  @Test
  public void testSingleFileExclude() throws Exception {
    assertGlobWithExcludeMatches("*", "food", "foo", "fool");
  }

  @Test
  public void testExcludeAll() throws Exception {
    assertGlobWithExcludeMatches("*", "*");
  }

  @Test
  public void testExcludeAllButNoMatches() throws Exception {
    assertGlobWithExcludeMatches("not-there", "*");
  }

  @Test
  public void testSingleFileExcludeDoesntMatch() throws Exception {
    assertGlobWithExcludeMatches("food", "foo", "food");
  }

  @Test
  public void testSingleFileExcludeForDirectoryWithChildGlob()
      throws Exception {
    assertGlobWithExcludeMatches("foo/*", "foo", "foo/bar", "foo/barnacle");
  }

  @Test
  public void testChildGlobWithChildExclude()
      throws Exception {
    assertGlobWithExcludeMatches("foo/*", "foo/*");
    assertGlobWithExcludeMatches("foo/bar", "foo/*");
    assertGlobWithExcludeMatches("foo/bar", "foo/bar");
    assertGlobWithExcludeMatches("foo/bar", "*/bar");
    assertGlobWithExcludeMatches("foo/bar", "*/*");
    assertGlobWithExcludeMatches("foo/bar/wiz", "*/*/*");
    assertGlobWithExcludeMatches("foo/bar/wiz", "foo/*/*");
    assertGlobWithExcludeMatches("foo/bar/wiz", "foo/bar/*");
    assertGlobWithExcludeMatches("foo/bar/wiz", "foo/bar/wiz");
    assertGlobWithExcludeMatches("foo/bar/wiz", "*/bar/wiz");
    assertGlobWithExcludeMatches("foo/bar/wiz", "*/*/wiz");
    assertGlobWithExcludeMatches("foo/bar/wiz", "foo/*/wiz");
  }

  private void assertGlobMatches(String pattern, String... expecteds)
      throws Exception {
    assertGlobWithExcludesMatches(
        Collections.singleton(pattern), Collections.<String>emptyList(),
        expecteds);
  }

  private void assertGlobMatches(Collection<String> pattern,
                                 String... expecteds)
      throws Exception {
    assertGlobWithExcludesMatches(pattern, Collections.<String>emptyList(),
        expecteds);
  }

  private void assertGlobWithExcludeMatches(String pattern, String exclude,
                                            String... expecteds)
      throws Exception {
    assertGlobWithExcludesMatches(
        Collections.singleton(pattern), Collections.singleton(exclude),
        expecteds);
  }

  private void assertGlobWithExcludesMatches(Collection<String> pattern,
                                             Collection<String> excludes,
                                             String... expecteds)
      throws Exception {
    MoreAsserts.assertSameContents(resolvePaths(expecteds),
        new UnixGlob.Builder(tmpPath)
            .addPatterns(pattern)
            .addExcludes(excludes)
            .globInterruptible());
  }

  private Set<Path> resolvePaths(String... relativePaths) {
    Set<Path> expectedFiles = new HashSet<>();
    for (String expected : relativePaths) {
      Path file = expected.equals(".")
          ? tmpPath
          : tmpPath.getRelative(expected);
      expectedFiles.add(file);
    }
    return expectedFiles;
  }

  @Test
  public void testGlobWithoutWildcardsDoesNotCallReaddir() throws Exception {
    UnixGlob.FilesystemCalls syscalls = new UnixGlob.FilesystemCalls() {
      @Override
      public FileStatus statNullable(Path path, Symlinks symlinks) {
        return UnixGlob.DEFAULT_SYSCALLS.statNullable(path, symlinks);
      }

      @Override
      public Collection<Dirent> readdir(Path path, Symlinks symlinks) {
        throw new IllegalStateException();
      }
    };

    MoreAsserts.assertSameContents(ImmutableList.of(tmpPath.getRelative("foo/bar/wiz/file")),
        new UnixGlob.Builder(tmpPath)
            .addPattern("foo/bar/wiz/file")
            .setFilesystemCalls(new AtomicReference<>(syscalls))
            .glob());
  }

  @Test
  public void testIllegalPatterns() throws Exception {
    assertIllegalPattern("(illegal) pattern");
    assertIllegalPattern("[illegal pattern");
    assertIllegalPattern("}illegal pattern");
    assertIllegalPattern("foo**bar");
    assertIllegalPattern("");
    assertIllegalPattern(".");
    assertIllegalPattern("/foo");
    assertIllegalPattern("./foo");
    assertIllegalPattern("foo/");
    assertIllegalPattern("foo/./bar");
    assertIllegalPattern("../foo/bar");
    assertIllegalPattern("foo//bar");
  }

  /**
   * Tests that globs can contain Java regular expression special characters
   */
  @Test
  public void testSpecialRegexCharacter() throws Exception {
    Path tmpPath2 = fs.getPath("/globtmp2");
    FileSystemUtils.createDirectoryAndParents(tmpPath2);
    Path aDotB = tmpPath2.getChild("a.b");
    FileSystemUtils.createEmptyFile(aDotB);
    FileSystemUtils.createEmptyFile(tmpPath2.getChild("aab"));
    // Note: this contains two asterisks because otherwise a RE is not built,
    // as an optimization.
    assertThat(UnixGlob.forPath(tmpPath2).addPattern("*a.b*").globInterruptible()).containsExactly(
        aDotB);
  }

  @Test
  public void testMatchesCallWithNoCache() {
    assertTrue(UnixGlob.matches("*a*b", "CaCb", null));
  }

  @Test
  public void testMultiplePatterns() throws Exception {
    assertGlobMatches(Lists.newArrayList("foo", "fool"), "foo", "fool");
  }

  @Test
  public void testMultiplePatternsWithExcludes() throws Exception {
    assertGlobWithExcludesMatches(Lists.newArrayList("foo", "foo?"),
        Lists.newArrayList("fool"), "foo", "food");
  }

  @Test
  public void testMatcherMethodRecursiveBelowDir() throws Exception {
    FileSystemUtils.createEmptyFile(tmpPath.getRelative("foo/file"));
    String pattern = "foo/**/*";
    assertTrue(UnixGlob.matches(pattern, "foo/bar"));
    assertTrue(UnixGlob.matches(pattern, "foo/bar/baz"));
    assertFalse(UnixGlob.matches(pattern, "foo"));
    assertFalse(UnixGlob.matches(pattern, "foob"));
    assertTrue(UnixGlob.matches("**/foo", "foo"));
  }

  @Test
  public void testMultiplePatternsWithOverlap() throws Exception {
    assertGlobMatchesAnyOrder(Lists.newArrayList("food", "foo?"),
                              "food", "fool");
    assertGlobMatchesAnyOrder(Lists.newArrayList("food", "?ood", "f??d"),
                              "food");
    assertThat(resolvePaths("food", "fool", "foo")).containsExactlyElementsIn(
        new UnixGlob.Builder(tmpPath).addPatterns("food", "xxx", "*").glob());

  }

  private void assertGlobMatchesAnyOrder(ArrayList<String> patterns,
                                         String... paths) throws Exception {
    assertThat(resolvePaths(paths)).containsExactlyElementsIn(
        new UnixGlob.Builder(tmpPath).addPatterns(patterns).globInterruptible());
  }

  /**
   * Tests that a glob returns files in sorted order.
   */
  @Test
  public void testGlobEntriesAreSorted() throws Exception {
    Collection<Path> directoryEntries = tmpPath.getDirectoryEntries();
    List<Path> globResult = new UnixGlob.Builder(tmpPath)
        .addPattern("*")
        .setExcludeDirectories(false)
        .globInterruptible();
    assertThat(Ordering.natural().sortedCopy(directoryEntries)).containsExactlyElementsIn(
        globResult).inOrder();
  }

  private void assertIllegalPattern(String pattern) throws Exception {
    try {
      new UnixGlob.Builder(tmpPath)
          .addPattern(pattern)
          .globInterruptible();
      fail();
    } catch (IllegalArgumentException e) {
      MoreAsserts.assertContainsRegex("in glob pattern", e.getMessage());
    }
  }

  @Test
  public void testHiddenFiles() throws Exception {
    for (String dir : ImmutableList.of(".hidden", "..also.hidden", "not.hidden")) {
      FileSystemUtils.createDirectoryAndParents(tmpPath.getRelative(dir));
    }
    // Note that these are not in the result: ".", ".."
    assertGlobMatches("*", "not.hidden", "foo", "fool", "food", ".hidden", "..also.hidden");
    assertGlobMatches("*.hidden", "not.hidden");
  }

  @Test
  public void testCheckCanBeInterrupted() throws Exception {
    final Thread mainThread = Thread.currentThread();
    final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

    Predicate<Path> interrupterPredicate = new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        mainThread.interrupt();
        return true;
      }
    };

    try {
      new UnixGlob.Builder(tmpPath)
          .addPattern("**")
          .setDirectoryFilter(interrupterPredicate)
          .setThreadPool(executor)
          .globInterruptible();
      fail();  // Should have received InterruptedException
    } catch (InterruptedException e) {
      // good
    }

    assertFalse(executor.isShutdown());
    executor.shutdown();
    assertTrue(executor.awaitTermination(TestUtils.WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  public void testCheckCannotBeInterrupted() throws Exception {
    final Thread mainThread = Thread.currentThread();
    final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    final AtomicBoolean sentInterrupt = new AtomicBoolean(false);

    Predicate<Path> interrupterPredicate = new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        if (!sentInterrupt.getAndSet(true)) {
          mainThread.interrupt();
        }
        return true;
      }
    };

    List<Path> result = new UnixGlob.Builder(tmpPath)
        .addPatterns("**", "*")
        .setDirectoryFilter(interrupterPredicate).setThreadPool(executor).glob();

    // In the non-interruptible case, the interrupt bit should be set, but the
    // glob should return the correct set of full results.
    assertTrue(Thread.interrupted());
    MoreAsserts.assertSameContents(resolvePaths(".", "foo", "foo/bar", "foo/bar/wiz",
        "foo/bar/wiz/file", "foo/barnacle", "foo/barnacle/wiz", "food", "food/barnacle",
        "food/barnacle/wiz", "fool", "fool/barnacle", "fool/barnacle/wiz"), result);

    assertFalse(executor.isShutdown());
    executor.shutdown();
    assertTrue(executor.awaitTermination(TestUtils.WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }
}
