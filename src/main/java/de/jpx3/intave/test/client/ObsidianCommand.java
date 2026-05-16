package de.jpx3.intave.test.client;

enum ObsidianCommand {
	WORK(0),
	WAIT(1),
	SHUTDOWN(2),
	READY(3),
	DESYNC(4);

	private final int id;

	ObsidianCommand(int id) {
		this.id = id;
	}

	int id() {
		return id;
	}

	static ObsidianCommand byId(int id) {
		for (ObsidianCommand command : values()) {
			if (command.id == id) {
				return command;
			}
		}
		throw new IllegalArgumentException("Unknown replay worker command: " + id);
	}
}
