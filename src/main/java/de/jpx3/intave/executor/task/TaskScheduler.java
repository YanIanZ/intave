package de.jpx3.intave.executor.task;

import de.jpx3.intave.user.User;

public interface TaskScheduler {
	void startSync(Task task);

	void startUserSync(Task task, User user);

	void startAsync(Task task);

	void stop(Task task);

	void stopAll();
}
