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

package de.jpx3.wureta.proxy.protocol;

import java.util.Set;

public interface ProxyPlatform {
  void sendToServer(String serverName, byte[] data);

  void broadcastExcept(String sourceServer, byte[] data);

  Set<String> serverNames();

  void log(String message);

  void warn(String message);
}
