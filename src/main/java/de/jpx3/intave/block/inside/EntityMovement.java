package de.jpx3.intave.block.inside;

import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;

import java.util.Optional;

public final class EntityMovement {
	private final Position from;
	private final Position to;
	private final Optional<Motion> axisDependentOriginalMovement;

	public EntityMovement(
		Position from, Position to,
		Optional<Motion> axisDependentOriginalMovement
	) {
		this.from = from;
		this.to = to;
		this.axisDependentOriginalMovement = axisDependentOriginalMovement;
	}

	public Position from() {
		return from;
	}

	public Position to() {
		return to;
	}

	public Optional<Motion> axisDependentOriginalMovement() {
		return axisDependentOriginalMovement;
	}
}
