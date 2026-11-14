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

/** How backends exchange frames: plugin messages through WuretaProxy, or Redis pub/sub. */
public interface NetworkTransport extends FrameSender {
  /** Prepares the transport; false (after logging why) when it cannot run. */
  boolean register();

  void cleanup();

  void close();

  /** Short name for logs. */
  String describe();

  int queuedFrames();

  long sentFrames();

  long receivedFrames();

  long droppedFrames();
}
