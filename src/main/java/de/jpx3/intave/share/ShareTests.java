package de.jpx3.intave.share;

import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.test.Tests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ShareTests extends Tests {
  public ShareTests() {
    super("SHR");
  }

  @Test(severity = Severity.ERROR)
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

  @Test(severity = Severity.ERROR)
  public void testBlockVolumeIterator() {
    BoundingBox boundingBox = new BoundingBox(0, 0, 0, 2, 2, 2);
    NativeVector vector = new NativeVector(0.0, -1, 0.0);

    BlockPositions positions = boundingBox.blockPositionBetweenDirectional(vector);
    Iterator<BlockPositionCursor> iterator = positions.iterator();

    List<String> visited = new ArrayList<>();
    while (iterator.hasNext()) {
      BlockPositionCursor pos = iterator.next();
      visited.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    assertEquals(27, visited.size());
    assertEquals("0,2,0", visited.get(0));

  }
}
