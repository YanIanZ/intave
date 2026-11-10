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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DetectionManifestTest {
  private static final Path BASELINE =
    Path.of("src/test/resources/detection_parity/baseline-units.txt");
  private static final Set<String> ALWAYS_ENABLED = new LinkedHashSet<>(Arrays.asList(
    "physics",
    "timer"
  ));

  @Test
  void activeDetectionInventoryMatchesBaseline() throws IOException {
    Path projectRoot = DetectionManifest.projectRoot();
    SortedSet<String> actual = DetectionManifest.collect(projectRoot);
    if (Boolean.getBoolean("intave.parity.update")) {
      Files.write(
        projectRoot.resolve(BASELINE),
        String.join("\n", actual).concat("\n").getBytes(StandardCharsets.UTF_8)
      );
    }
    List<String> expectedLines = Files.readAllLines(
      projectRoot.resolve(BASELINE),
      StandardCharsets.UTF_8
    );
    List<String> expected = new ArrayList<>();
    for (String line : expectedLines) {
      if (!line.trim().isEmpty()) {
        expected.add(line.trim());
      }
    }

    assertEquals(
      expected,
      new ArrayList<>(actual),
      "Detection inventory changed. Review the diff and update "
        + BASELINE + " only when detection is intentionally changed."
    );
    assertTrue(
      actual.stream().anyMatch(line -> line.startsWith("CHECK|")),
      "Detection manifest must contain registered checks"
    );
    assertTrue(
      actual.stream().anyMatch(line -> line.startsWith("ACTIVE|")),
      "Detection manifest must contain active check parts"
    );
  }

  @Test
  void everyDefaultEnabledCheckStaysEnabled() throws IOException {
    Path projectRoot = DetectionManifest.projectRoot();
    Path advanced = projectRoot.resolve("src/main/resources/advanced.yml");
    List<String> lines = Files.readAllLines(advanced, StandardCharsets.UTF_8);

    Set<String> checkDefaults = new LinkedHashSet<>();
    boolean inCheckSection = false;
    String currentKey = null;
    for (String line : lines) {
      if (line.equals("check:")) {
        inCheckSection = true;
        continue;
      }
      if (!inCheckSection) {
        continue;
      }
      if (!line.isEmpty() && !line.startsWith(" ")) {
        break;
      }
      if (line.startsWith("  ") && !line.startsWith("   ") && line.trim().endsWith(":")) {
        currentKey = line.trim().substring(0, line.trim().length() - 1);
        checkDefaults.add(currentKey + "=false");
        continue;
      }
      if (currentKey != null && line.trim().equals("enabled: true")) {
        checkDefaults.remove(currentKey + "=false");
        checkDefaults.add(currentKey + "=true");
      }
    }

    for (String entry : checkDefaults) {
      String key = entry.substring(0, entry.indexOf('='));
      boolean enabled = entry.endsWith("=true");
      if (ALWAYS_ENABLED.contains(key)) {
        assertTrue(
          enabled || alwaysEnabledSource(projectRoot, key),
          "Check '" + key + "' must stay enabled"
        );
      } else {
        assertTrue(enabled, "Check '" + key + "' must have enabled: true in advanced.yml");
      }
    }
  }

  private boolean alwaysEnabledSource(Path projectRoot, String key) throws IOException {
    String simpleName = Character.toUpperCase(key.charAt(0)) + key.substring(1);
    Path source = projectRoot.resolve(
      "src/main/java/de/jpx3/intave/check/movement/" + simpleName + ".java"
    );
    if (!Files.isRegularFile(source)) {
      return false;
    }
    String content = DetectionManifest.stripComments(
      new String(Files.readAllBytes(source), StandardCharsets.UTF_8)
    );
    return content.contains("public boolean enabled()")
      && content.contains("return true;");
  }
}
