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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    Map<String, Boolean> enabledByKey = checkDefaults(projectRoot);
    Map<String, String> keysByName = DetectionManifest.checkConfigurationKeys(projectRoot);
    SortedSet<String> manifest = DetectionManifest.collect(projectRoot);

    boolean foundCheck = false;
    for (String line : manifest) {
      if (!line.startsWith("CHECK|")) {
        continue;
      }
      foundCheck = true;
      String name = line.substring("CHECK|".length());
      String key = keysByName.getOrDefault(name, name.toLowerCase(Locale.ROOT));
      Boolean enabled = enabledByKey.get(key);
      assertTrue(
        enabled != null,
        "Check '" + name + "' has no check." + key + " section in advanced.yml"
      );
      if (ALWAYS_ENABLED.contains(key)) {
        assertTrue(
          enabled || alwaysEnabledSource(projectRoot, key),
          "Check '" + name + "' must stay enabled"
        );
      } else {
        assertTrue(
          enabled,
          "Check '" + name + "' must have enabled: true in advanced.yml"
        );
      }
    }
    assertTrue(foundCheck, "Detection manifest must contain registered checks");
  }

  private Map<String, Boolean> checkDefaults(Path projectRoot) throws IOException {
    Path advanced = projectRoot.resolve("src/main/resources/advanced.yml");
    List<String> lines = Files.readAllLines(advanced, StandardCharsets.UTF_8);
    Map<String, Boolean> defaults = new HashMap<>();
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
        defaults.put(currentKey, false);
        continue;
      }
      if (currentKey != null && line.trim().equals("enabled: true")) {
        defaults.put(currentKey, true);
      }
    }
    return defaults;
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
