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

public final class NetworkLimits {
  public static final int FRAME_MAGIC = 0x57545331; // WTS1
  public static final int CHUNK_MAGIC = 0x57544331; // WTC1
  public static final int VERSION = 1;

  public static final int MAX_SERVER_NAME_BYTES = 64;
  public static final int MAX_PAYLOAD_BYTES = 262_144;
  public static final int MAX_CHUNK_BYTES = 30_000;
  public static final int MAX_CHUNKS = 32;
  public static final int HMAC_BYTES = 32;
  public static final long MAX_CLOCK_SKEW_MILLIS = 300_000L;
  public static final long ASSEMBLY_TIMEOUT_MILLIS = 30_000L;

  private NetworkLimits() {
  }
}
