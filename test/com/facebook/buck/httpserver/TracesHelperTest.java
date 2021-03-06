/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.httpserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.facebook.buck.httpserver.TracesHelper.TraceAttributes;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.InvocationInfo;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.timing.FakeClock;
import com.facebook.buck.timing.SettableFakeClock;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TracesHelperTest {

  @Test
  public void testGetTraceAttributesForId() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem(
        new FakeClock(TimeUnit.MILLISECONDS.toNanos(1000L)));
    projectFilesystem.writeContentsToPath(
        "[" +
            "{\n" +
            "\"cat\" : \"buck\",\n" +
            "\"pid\" : 0,\n" +
            "\"ts\" : 0,\n" +
            "\"ph\" : \"M\",\n" +
            "\"args\" : {\n" +
            "\"name\" : \"buck\"\n" +
            "},\n" +
            "\"name\" : \"process_name\",\n" +
            "\"tid\" : 0\n" +
            "}," +
            "{" +
            "\"cat\":\"buck\"," +
            "\"name\":\"build\"," +
            "\"ph\":\"B\"," +
            "\"pid\":0," +
            "\"tid\":1," +
            "\"ts\":5621911884918," +
            "\"args\":{\"command_args\":\"buck\"}" +
            "}" +
            "]",
        projectFilesystem.getBuckPaths().getBuckOut().resolve("build.a.trace"));

    TracesHelper helper = new TracesHelper(projectFilesystem);
    TraceAttributes traceAttributes = helper.getTraceAttributesFor("a");
    assertEquals(
        "TracesHelper should be able to extract the command.",
        Optional.of("buck build buck"),
        traceAttributes.getCommand());
    assertEquals(1000L, traceAttributes.getLastModifiedTime());

    // We cannot verify the contents of getFormattedDateTime() because they may vary depending on
    // timezone and locale.
    assertNotNull(Strings.emptyToNull(traceAttributes.getFormattedDateTime()));
  }

  @Test
  public void testGetTraceAttributesForJsonWithoutName() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem(
        new FakeClock(TimeUnit.MILLISECONDS.toNanos(2000L)));
    projectFilesystem.writeContentsToPath(
        "[" +
            "{" +
            "\"cat\":\"buck\"," +
            "\"ph\":\"B\"," +
            "\"pid\":0," +
            "\"tid\":1," +
            "\"ts\":5621911884918," +
            "\"args\":{\"command_args\":\"buck\"}" +
            "}" +
            "]",
        BuckConstant.getBuckTraceDir().resolve("build.b.trace"));

    TracesHelper helper = new TracesHelper(projectFilesystem);
    TraceAttributes traceAttributes = helper.getTraceAttributesFor("b");
    assertEquals(
        "TracesHelper should not be able to extract the command because there is no name " +
            "attribute.",
        Optional.empty(),
        traceAttributes.getCommand());
    assertEquals(2000L, traceAttributes.getLastModifiedTime());
  }

  @Test
  public void testGetTraceAttributesForJsonWithoutCommandArgs() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem(
        new FakeClock(TimeUnit.MILLISECONDS.toNanos(2000L)));
    projectFilesystem.writeContentsToPath(
        "[" +
            "{" +
            "\"cat\":\"buck\"," +
            "\"ph\":\"B\"," +
            "\"pid\":0," +
            "\"tid\":1," +
            "\"ts\":5621911884918" +
            "}" +
            "]",
        BuckConstant.getBuckTraceDir().resolve("build.c.trace"));

    TracesHelper helper = new TracesHelper(projectFilesystem);
    TraceAttributes traceAttributes = helper.getTraceAttributesFor("c");
    assertEquals(
        "TracesHelper should not be able to extract the command because there is no " +
            "command_args attribute.",
        Optional.empty(),
        traceAttributes.getCommand());
    assertEquals(2000L, traceAttributes.getLastModifiedTime());
  }

  @Test
  public void testSortByLastModified() throws IOException {
    SettableFakeClock clock = new SettableFakeClock(0L, 0L);
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem(clock);
    clock.setCurrentTimeMillis(1);
    projectFilesystem.touch(BuckConstant.getBuckTraceDir().resolve("build.1.trace"));
    clock.setCurrentTimeMillis(4);
    projectFilesystem.touch(BuckConstant.getBuckTraceDir().resolve("build.4.trace"));
    clock.setCurrentTimeMillis(2);
    projectFilesystem.touch(BuckConstant.getBuckTraceDir().resolve("build.2.trace"));
    clock.setCurrentTimeMillis(5);
    projectFilesystem.touch(BuckConstant.getBuckTraceDir().resolve("build.5.trace"));
    clock.setCurrentTimeMillis(3);
    projectFilesystem.touch(BuckConstant.getBuckTraceDir().resolve("build.3.trace"));
    projectFilesystem.touch(BuckConstant.getBuckTraceDir().resolve("build.3b.trace"));

    TracesHelper helper = new TracesHelper(projectFilesystem);
    assertEquals(
        ImmutableList.of(
            BuckConstant.getBuckTraceDir().resolve("build.5.trace"),
            BuckConstant.getBuckTraceDir().resolve("build.4.trace"),
            BuckConstant.getBuckTraceDir().resolve("build.3b.trace"),
            BuckConstant.getBuckTraceDir().resolve("build.3.trace"),
            BuckConstant.getBuckTraceDir().resolve("build.2.trace"),
            BuckConstant.getBuckTraceDir().resolve("build.1.trace")),
        helper.listTraceFilesByLastModified());
  }

  @Test(expected = HumanReadableException.class)
  public void testInputsForTracesThrowsWhenEmpty() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem(
        new FakeClock(TimeUnit.MILLISECONDS.toNanos(2000L)));
    projectFilesystem.mkdirs(BuckConstant.getBuckTraceDir());
    TracesHelper helper = new TracesHelper(projectFilesystem);
    helper.getInputsForTraces("nonexistent");
  }

  @Test(expected = HumanReadableException.class)
  public void testTraceAttributesThrowsWhenEmpty() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem(
        new FakeClock(TimeUnit.MILLISECONDS.toNanos(2000L)));
    projectFilesystem.mkdirs(BuckConstant.getBuckTraceDir());
    TracesHelper helper = new TracesHelper(projectFilesystem);
    helper.getTraceAttributesFor("nonexistent");
  }

  @Test
  public void testFindingTracesInNewPerCommandDirectories() throws IOException {
    SettableFakeClock clock = new SettableFakeClock(0L, 0L);
    FakeProjectFilesystem fs = new FakeProjectFilesystem(clock);
    fs.touch(getNewTraceFilePath(fs, "build", "1", 1));
    fs.touch(getNewTraceFilePath(fs, "audit", "4", 4));
    fs.touch(getNewTraceFilePath(fs, "query", "2", 2));
    fs.touch(getNewTraceFilePath(fs, "targets", "5", 5));
    fs.touch(getNewTraceFilePath(fs, "test", "3", 3));
    fs.touch(getNewTraceFilePath(fs, "test", "3b", 3));

    TracesHelper helper = new TracesHelper(fs);
    assertEquals(
        ImmutableList.of(
            fs.getBuckPaths().getLogDir().resolve("1970-01-01_00h00m05s_targets_5/build.5.trace"),
            fs.getBuckPaths().getLogDir().resolve("1970-01-01_00h00m04s_audit_4/build.4.trace"),
            fs.getBuckPaths().getLogDir().resolve("1970-01-01_00h00m03s_test_3b/build.3b.trace"),
            fs.getBuckPaths().getLogDir().resolve("1970-01-01_00h00m03s_test_3/build.3.trace"),
            fs.getBuckPaths().getLogDir().resolve("1970-01-01_00h00m02s_query_2/build.2.trace"),
            fs.getBuckPaths().getLogDir().resolve("1970-01-01_00h00m01s_build_1/build.1.trace")),
        helper.listTraceFilesByLastModified());
  }

  public Path getNewTraceFilePath(
      ProjectFilesystem fs,
      String commandName,
      String buildId,
      int seconds) {
    InvocationInfo info = InvocationInfo.of(
        new BuildId(buildId),
        false,
        false,
        commandName,
        fs.getBuckPaths().getLogDir())
        .withTimestampMillis(TimeUnit.SECONDS.toMillis(seconds));
    return info.getLogDirectoryPath().resolve("build." + buildId + ".trace");
  }
}
