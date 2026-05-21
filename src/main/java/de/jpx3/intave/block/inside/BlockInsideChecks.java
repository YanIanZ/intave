package de.jpx3.intave.block.inside;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;

public final class BlockInsideChecks {
	private final static BlockInsideCheck v2111 = new v2111BlockInsideCheck();

	public static BlockInsideCheck suitableFor(User user) {
		int protocolVersion = user.meta().protocol().protocolVersion();
		if (protocolVersion >= ProtocolMetadata.VER_1_21_11) {
			return v2111;
		}
		return null;
	}

	public static BlockInsideCheck generic() {
		return null;
	}
}
