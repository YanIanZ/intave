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

package de.jpx3.intave.module.network;

import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;

/** Cross-server player storage: requests, saves, and the storage frames other servers send. */
public interface NetworkStorageGateway extends StorageGateway {
  void handle(NetworkFrame frame);

  void tick(long now);

  void failAll();

  int pendingCount();
}
