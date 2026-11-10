/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.parity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Source-based detection inventory. The test source set has no live server, so the manifest is
 * built from {@code CheckService.addCheck(...)} and the {@code appendCheckPart(...)} wiring in the
 * check sources. Comments are stripped before matching so commented-out wiring stays inactive.
 */
final class DetectionManifest {
  private static final Pattern ADD_CHECK =
    Pattern.compile("addCheck\\(([A-Za-z0-9_]+)\\.class\\)");
  private static final Pattern CHECK_CLASS =
    Pattern.compile("(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z0-9_]+)\\s+extends\\s+(?:MetaCheck|Check)\\b");
  private static final Pattern APPEND_NEW_PART =
    Pattern.compile("appendCheckPart\\s*\\(\\s*new\\s+([A-Za-z0-9_]+)\\s*\\(");
  private static final Pattern APPEND_PLAYER_PART =
    Pattern.compile("appendPlayerCheckPart\\s*\\(\\s*([A-Za-z0-9_]+)\\.class\\s*\\)");
  private static final Pattern APPEND_VARIABLE =
    Pattern.compile("appendCheckPart\\s*\\(\\s*([A-Za-z0-9_]+)\\s*\\)");
  private static final Pattern APPEND_PARTS_BLOCK =
    Pattern.compile("appendCheckParts\\s*\\((.*?)\\)\\s*;", Pattern.DOTALL);
  private static final Pattern NEW_PART =
    Pattern.compile("new\\s+([A-Za-z0-9_]+)\\s*\\(");
  private static final Pattern VARIABLE_ASSIGNMENT =
    Pattern.compile("([A-Za-z0-9_]+)\\s*=\\s*new\\s+([A-Za-z0-9_]+)\\s*\\(");

  private DetectionManifest() {
  }

  static SortedSet<String> collect(Path projectRoot) throws IOException {
    Path checkRoot = projectRoot.resolve("src/main/java/de/jpx3/intave/check");
    Path checkService = checkRoot.resolve("CheckService.java");
    SortedSet<String> manifest = new TreeSet<>();

    String serviceSource = stripComments(read(checkService));
    Matcher addCheck = ADD_CHECK.matcher(serviceSource);
    while (addCheck.find()) {
      manifest.add("CHECK|" + addCheck.group(1));
    }

    List<Path> sources = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(checkRoot)) {
      paths.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
    }

    for (Path source : sources) {
      String content = stripComments(read(source));
      Matcher checkClass = CHECK_CLASS.matcher(content);
      if (!checkClass.find()) {
        continue;
      }
      String parent = checkClass.group(1);
      if (parent.equals("Check") || parent.equals("MetaCheck")) {
        continue;
      }

      Matcher assignment = VARIABLE_ASSIGNMENT.matcher(content);
      java.util.Map<String, String> variables = new java.util.HashMap<>();
      while (assignment.find()) {
        variables.put(assignment.group(1), assignment.group(2));
      }

      Matcher directPart = APPEND_NEW_PART.matcher(content);
      while (directPart.find()) {
        manifest.add("ACTIVE|" + parent + "|" + directPart.group(1));
      }

      Matcher playerPart = APPEND_PLAYER_PART.matcher(content);
      while (playerPart.find()) {
        manifest.add("ACTIVE|" + parent + "|" + playerPart.group(1));
      }

      Matcher variablePart = APPEND_VARIABLE.matcher(content);
      while (variablePart.find()) {
        String variable = variablePart.group(1);
        String part = variables.get(variable);
        if (part != null) {
          manifest.add("ACTIVE|" + parent + "|" + part);
        }
      }

      Matcher partBlock = APPEND_PARTS_BLOCK.matcher(content);
      while (partBlock.find()) {
        Matcher part = NEW_PART.matcher(partBlock.group(1));
        while (part.find()) {
          manifest.add("ACTIVE|" + parent + "|" + part.group(1));
        }
      }
    }
    return manifest;
  }

  static Path projectRoot() {
    Path candidate = Path.of("").toAbsolutePath();
    while (candidate != null) {
      if (Files.isDirectory(candidate.resolve("src/main/java/de/jpx3/intave/check"))) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException("Unable to locate the Intave project root");
  }

  private static String read(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  static String stripComments(String source) {
    StringBuilder out = new StringBuilder(source.length());
    int index = 0;
    State state = State.CODE;
    while (index < source.length()) {
      char current = source.charAt(index);
      switch (state) {
        case CODE:
          if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
            state = State.LINE_COMMENT;
            index += 2;
          } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
            state = State.BLOCK_COMMENT;
            index += 2;
          } else if (current == '"') {
            state = State.STRING;
            out.append(current);
            index++;
          } else if (current == '\'') {
            state = State.CHARACTER;
            out.append(current);
            index++;
          } else {
            out.append(current);
            index++;
          }
          break;
        case LINE_COMMENT:
          if (current == '\n') {
            state = State.CODE;
            out.append(current);
          }
          index++;
          break;
        case BLOCK_COMMENT:
          if (current == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
            state = State.CODE;
            index += 2;
          } else {
            if (current == '\n') {
              out.append(current);
            }
            index++;
          }
          break;
        case STRING:
        case CHARACTER:
          out.append(current);
          if (current == '\\' && index + 1 < source.length()) {
            out.append(source.charAt(index + 1));
            index += 2;
          } else {
            if ((state == State.STRING && current == '"') || (state == State.CHARACTER && current == '\'')) {
              state = State.CODE;
            }
            index++;
          }
          break;
        default:
          throw new IllegalStateException("Unknown state " + state);
      }
    }
    return out.toString();
  }

  private enum State {
    CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHARACTER
  }
}
