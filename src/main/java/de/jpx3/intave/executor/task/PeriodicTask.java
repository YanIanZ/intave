package de.jpx3.intave.executor.task;

final class PeriodicTask implements Task {
	private final String name;
	private final Runnable runnable;
	private final long delay;
	private final long period;

	PeriodicTask(
		String name, Runnable runnable,
		long delay, long period
	) {
		this.name = name;
		this.runnable = runnable;
		this.delay = delay;
		this.period = period;
	}

	@Override
	public String name() {
		if (name == null) {
			return String.format("periodic(%s, +%d, @%d)", runnable.toString(), delay, period);
		} else {
			return String.format("periodic(%s, +%d, @%d)", name, delay, period);
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
	public long period() {
		return period;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		PeriodicTask that = (PeriodicTask) obj;
		return runnable.equals(that.runnable);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}
}
