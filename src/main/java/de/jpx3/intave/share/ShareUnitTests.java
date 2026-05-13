package de.jpx3.intave.share;

import de.jpx3.intave.test.unit.Severity;
import de.jpx3.intave.test.unit.UnitTest;
import de.jpx3.intave.test.unit.UnitTests;

public final class ShareUnitTests extends UnitTests {
  public ShareUnitTests() {
    super("SHR");
  }

  @UnitTest(severity = Severity.ERROR)
  public void testHistoryWindow() {
    HistoryWindow<Integer> historyWindow = new HistoryWindow<>(10);
    for (int i = 0; i <= 40; i++) {
      historyWindow.add(i);
    }
    for (int i = 0; i < 10; i++) {
      if (historyWindow.back(i) != 40 - i) {
        fail(historyWindow.back(i) + " != " + (40 - i));
      }
    }
  }
}
