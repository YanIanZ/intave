package de.jpx3.intave.executor.task;

final class DelayedTask implements Task {
	private final String name;
	private final Runnable runnable;
	private final long delay;

	public DelayedTask(
		String name, Runnable runnable, long delay
	) {
		this.name = name;
		this.runnable = runnable;
		this.delay = delay;
	}

	@Override
	public String name() {
		if (name == null) {
			return String.format("delayed(%s, +%d)", runnable.toString(), delay);
		} else {
			return String.format("delayed(%s, +%d)", name, delay);
		}
	}

	@Override
	public void run() {
		runnable.run();
	}

	@Override
	public long delay() {
		return delay;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		DelayedTask that = (DelayedTask) obj;
		return runnable.equals(that.runnable);
	}
}



