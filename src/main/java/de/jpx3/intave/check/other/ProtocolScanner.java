package de.jpx3.intave.check.other;

import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.other.protocolscanner.*;

public final class ProtocolScanner extends Check {
  public ProtocolScanner() {
    super("ProtocolScanner", "protocolscanner");

    appendCheckParts(
      new SentSlotTwice(this),
      new InvalidPitch(this),
      new SkinBlinker(this),
      new InvalidRelease(this)
    );

    appendPlayerCheckPart(InfeasibleDependencies.class);
  }
}