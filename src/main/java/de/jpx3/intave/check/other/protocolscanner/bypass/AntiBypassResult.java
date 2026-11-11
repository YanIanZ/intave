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

package de.jpx3.intave.check.other.protocolscanner.bypass;

public final class AntiBypassResult {
  enum Status {
    CLEAN,
    OBSERVE,
    SUSPICIOUS,
    VIOLATION,
    UNKNOWN
  }

  private final String layerId;
  private final Status status;
  private final double confidence;
  private final String details;

  private AntiBypassResult(String layerId, Status status, double confidence, String details) {
    this.layerId = layerId;
    this.status = status;
    this.confidence = confidence;
    this.details = details;
  }

  static AntiBypassResult clean(String layerId, String details) {
    return new AntiBypassResult(layerId, Status.CLEAN, 0.0D, details);
  }

  static AntiBypassResult observe(String layerId, double confidence, String details) {
    return new AntiBypassResult(layerId, Status.OBSERVE, confidence, details);
  }

  static AntiBypassResult suspicious(String layerId, double confidence, String details) {
    return new AntiBypassResult(layerId, Status.SUSPICIOUS, confidence, details);
  }

  static AntiBypassResult violation(String layerId, double confidence, String details) {
    return new AntiBypassResult(layerId, Status.VIOLATION, confidence, details);
  }

  static AntiBypassResult unknown(String layerId, String details) {
    return new AntiBypassResult(layerId, Status.UNKNOWN, 0.0D, details);
  }

  String layerId() {
    return layerId;
  }

  Status status() {
    return status;
  }

  double confidence() {
    return confidence;
  }

  String details() {
    return details;
  }

  boolean signalsMisbehaviour() {
    return status == Status.SUSPICIOUS || status == Status.VIOLATION;
  }
}
