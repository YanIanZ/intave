package de.jpx3.intave.test.client;

import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.test.TestCompletionLatch;

@HighOrderService
public final class ClientTestService {

	public void setup() {
		TestCompletionLatch.begunTest();


		TestCompletionLatch.finishTest();
	}
}
