package de.jpx3.intave.module;

import com.comphenix.protocol.ProtocolLibrary;

import java.util.Arrays;

final class Requirements {
  public static Requirement none() {
    return new NoRequirement();
  }

  public static Requirement protocolLib() {
    return requiresPlugin("ProtocolLib").and(() -> ProtocolLibrary.getProtocolManager() != null);
  }

  public static Requirement protocolLib4() {
    return protocolLib().and(() -> ProtocolLibrary.getPlugin().getDescription().getVersion().startsWith("4"));
  }

  public static Requirement intaveEnabled() {
    return requiresPlugin(de.jpx3.intave.IntaveBuildConfig.BRAND_NAME);
  }

  public static Requirement requiresPlugin(String plugin) {
    return new PluginRequirement(plugin);
  }

  public static Requirement requiresPlugins(String... plugins) {
    return new PluginRequirement(plugins);
  }

  public static Requirement mergeAnd(Requirement... requirements) {
    return () -> Arrays.stream(requirements).allMatch(Requirement::fulfilled);
  }

  public static Requirement mergeOr(Requirement... requirements) {
    return () -> Arrays.stream(requirements).anyMatch(Requirement::fulfilled);
  }
}
