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

package de.jpx3.intave.adapter;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.connect.IntaveDomains;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ComponentLoader {
  private static final int DOWNLOAD_RETRIES = 4;
  private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000L;

  public Map<String, String> essentialComponents = new HashMap<>();
  private final IntavePlugin plugin;

  public ComponentLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void prepareComponents() {
    String version = Bukkit.getVersion();
    if (version.contains("MC: 1.21.11") || version.contains("MC: 1.21.10") || version.contains("MC: 26.1") || version.contains("MC: 26.2") || version.contains("MC: 26.3")) {
      essentialComponents.put("ProtocolLib", "https://github.com/dmulloy2/ProtocolLib/releases/download/dev-build/ProtocolLib-Spigot.jar");
    } else if (version.contains("MC: 1.19") || version.contains("MC: 1.20") || version.contains("MC: 1.21")) {
      essentialComponents.put("ProtocolLib", "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar");
    } else {
      essentialComponents.put("ProtocolLib", "https://" + IntaveDomains.primaryServiceDomain() + "/resource/ProtocolLib-4-8-0.jar");
    }
  }

  public void loadComponents() {
    for (String component : essentialComponents.keySet()) {
      try {
	      loadComponent(component);
      } catch (Exception exception) {
        throw new IntaveInternalException("Unable to load library " + component, exception);
      }
    }
  }

  private boolean loadComponent(String componentName) {
    Plugin componentPlugin = Bukkit.getPluginManager().getPlugin(componentName);
    if (componentPlugin != null) {
      if (!componentPlugin.isEnabled()) {
        Bukkit.getPluginManager().enablePlugin(componentPlugin);
      }
      return false;
    }

    File componentPluginFile = new File(plugin.dataFolder().getParentFile().getAbsolutePath() + "/" + componentName + ".jar");
    if (!componentPluginFile.exists()) {
      String downloadURL = this.essentialComponents.get(componentName);
      try {
        downloadComponentPlugin(componentPluginFile, componentName, downloadURL);
        return true;
      } catch (Exception exception) {
        throw new IntaveInternalException("Unable to download library " + componentName, exception);
      }
    }
    return false;
  }

  private void downloadComponentPlugin(File componentPluginFile, String componentName, String downloadURL) throws IOException, InvalidPluginException, InvalidDescriptionException {
    URL website = new URL(downloadURL);
    System.out.println("[debug] Downloading " + componentName + " from " + downloadURL);
    downloadWithRetries(website, componentPluginFile.toPath(), componentName);
    plugin.logger().info(ChatColor.GREEN + "Downloaded " + componentName);
    Plugin componentPlugin = this.plugin.getServer().getPluginManager().loadPlugin(componentPluginFile);

    try {
      ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
      if (protocolManager == null) {
        componentPlugin.onLoad();
      }
    } catch (Throwable throwable) {
      // ProtocolLib Moment
    }
    this.plugin.getServer().getPluginManager().enablePlugin(componentPlugin);
  }

  private void downloadWithRetries(URL website, Path target, String componentName) throws IOException {
    for (int retry = 0; ; retry++) {
      try {
        download(website, target);
        return;
      } catch (IOException exception) {
        if (retry == DOWNLOAD_RETRIES) {
          throw exception;
        }
        long delayMillis = INITIAL_RETRY_DELAY_MILLIS << retry;
        plugin.logger().warn("Unable to download " + componentName + ": " + exception.getMessage()
          + ". Retrying in " + delayMillis / 1_000L + " seconds (retry " + (retry + 1) + "/" + DOWNLOAD_RETRIES + ")");
        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while retrying download of " + componentName, interruptedException);
        }
      }
    }
  }

  private void download(URL website, Path target) throws IOException {
    Path temporaryFile = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName().toString(), ".tmp");
    try {
      try (InputStream in = website.openStream(); OutputStream out = Files.newOutputStream(temporaryFile)) {
        copy(in, out);
      }
      Files.move(temporaryFile, target);
    } finally {
      Files.deleteIfExists(temporaryFile);
    }
  }

  private void copy(InputStream source, OutputStream sink) throws IOException {
    byte[] buf = new byte[4096];
    int n, nStart = -1;
    while ((n = source.read(buf)) > 0) {
      if (nStart < 0) {
        nStart = n;
      }
      sink.write(buf, 0, n);
    }
  }
}
