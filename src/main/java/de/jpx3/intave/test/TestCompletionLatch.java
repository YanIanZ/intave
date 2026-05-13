package de.jpx3.intave.test;

import de.jpx3.intave.IntaveBuildConfig;

import java.util.ArrayList;
import java.util.List;

/*
	We want to wait for all tests to complete until the server is shut down.
 */
public final class TestCompletionLatch {
	private static int awaitedTests;
	private static List<Runnable> completionListeners = new ArrayList<>();

	public static void begunTest() {
		if (IntaveBuildConfig.TEST_BUILD) {
			awaitedTests++;
		}
	}

	public static void finishTest() {
		if (IntaveBuildConfig.TEST_BUILD) {
			awaitedTests--;
			if (awaitedTests == 0) {
				List<Runnable> subscribers = completionListeners;
				completionListeners = new ArrayList<>();
				subscribers.forEach(Runnable::run);
			}
		}
	}

	public static void onComplete(Runnable runnable) {
		if (IntaveBuildConfig.TEST_BUILD) {
			if (awaitedTests == 0) {
				runnable.run();
			} else {
				completionListeners.add(runnable);
			}
		}
	}
}
