package io.rouz.flo.processor;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.rouz.flo.Task;
import io.rouz.flo.cli.Cli;
import org.junit.Test;

public class CliTest {

  private static int firstInt;
  private static String secondString;
  private static boolean firstFlag = false;
  private static boolean secondFlag = true;
  private static CustomEnum parsedEnum;
  private static CustomType parsedType;

  @Test
  public void shouldParseStandardArgTypes() throws Exception {
    Cli.forFactories(FloRootTaskFactory.CliTest_StandardArgs())
        .run("create", "CliTest.standardArgs", "--first", "22", "--second", "hello");

    assertThat(firstInt, is(22));
    assertThat(secondString, is("hello"));
  }

  @RootTask
  public static Task<String> standardArgs(int first, String second) {
    firstInt = first;
    secondString = second;
    return Task.named("StandardArgs", first, second).ofType(String.class)
        .process(() -> second + " " + first * 100);
  }

  @Test
  public void shouldParseFlags() throws Exception {
    Cli.forFactories(FloRootTaskFactory.CliTest_Flags())
        .run("create", "CliTest.flags", "--flag1");

    assertTrue(firstFlag); // toggled
    assertFalse(secondFlag);  // toggled
  }

  @RootTask
  public static Task<String> flags(boolean flag1, boolean flag2) {
    firstFlag = flag1;
    secondFlag = flag2;
    return Task.named("Flags", flag1, flag2).ofType(String.class)
        .process(() -> flag1 + " " + flag2);
  }

  @Test
  public void shouldParseEnums() throws Exception {
    Cli.forFactories(FloRootTaskFactory.CliTest_Enums())
        .run("create", "CliTest.enums", "--enm", "BAR");

    assertThat(parsedEnum, is(CustomEnum.BAR));
  }

  @RootTask
  public static Task<String> enums(CustomEnum enm) {
    parsedEnum = enm;
    return Task.named("Enums", enm).ofType(String.class)
        .process(enm::toString);
  }

  @Test
  public void shouldParseCustomTypes() throws Exception {
    Cli.forFactories(FloRootTaskFactory.CliTest_CustomType())
        .run("create", "CliTest.customType", "--myType", "blarg");

    assertThat(parsedType.content, is("blarg parsed for you!"));
  }

  @RootTask
  public static Task<String> customType(CustomType myType) {
    parsedType = myType;
    return Task.named("Types", myType.content).ofType(String.class)
        .process(() -> myType.content);
  }

  public enum CustomEnum {
    BAR
  }

  public static class CustomType {
    final String content;

    CustomType(String content) {
      this.content = content;
    }

    /**
     * String parser that JOpt Simple will use to parse values of {@link CustomType}
     *
     * @param string  The command line string to be parsed
     * @return An instance of {@link CustomType}
     */
    public static CustomType valueOf(String string) {
      return new CustomType(string + " parsed for you!");
    }
  }
}
