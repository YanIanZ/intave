package de.jpx3.intave.player.attribute;

import com.comphenix.protocol.wrappers.MinecraftKey;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class WrappedAttributeModifier {
	private final MinecraftKey key;
	private final UUID uuid;
	private final String name;
	private final Operation operation;
	private final double amount;

	public WrappedAttributeModifier(
		MinecraftKey key, UUID uuid,
		String name, Operation operation,
		double amount
	) {
		this.key = key;
		this.uuid = uuid;
		this.name = name;
		this.operation = operation;
		this.amount = amount;
	}

	public MinecraftKey getKey() {
		return key;
	}

	public UUID getUUID() {
		return uuid;
	}

	public String getName() {
		return name;
	}

	public Operation getOperation() {
		return operation;
	}

	public double getAmount() {
		return amount;
	}

	public static Set<WrappedAttributeModifier> fromProtocolLib(
		Set<com.comphenix.protocol.wrappers.WrappedAttributeModifier> protocolLibModifiers
	) {
		return protocolLibModifiers.stream()
			.map(WrappedAttributeModifier::fromProtocolLib)
			.collect(Collectors.toSet());
	}

	private static WrappedAttributeModifier fromProtocolLib(
		com.comphenix.protocol.wrappers.WrappedAttributeModifier protocolLibModifier
	) {
		return new WrappedAttributeModifier(
			protocolLibModifier.getKey(),
			protocolLibModifier.getUUID(),
			protocolLibModifier.getName(),
			Operation.fromId(protocolLibModifier.getOperation().getId()),
			protocolLibModifier.getAmount()
		);
	}

	public static Builder newBuilder(UUID uuid) {
		return new Builder(uuid);
	}

	public static class Builder {
		private final UUID uuid;
		private MinecraftKey key;
		private String name;
		private Operation operation;
		private double amount;

		public Builder(UUID uuid) {
			this.uuid = uuid;
		}

		public Builder withKey(MinecraftKey key) {
			this.key = key;
			return this;
		}

		public Builder withName(String name) {
			this.name = name;
			return this;
		}

		public Builder withOperation(Operation operation) {
			this.operation = operation;
			return this;
		}

		public Builder withAmount(double amount) {
			this.amount = amount;
			return this;
		}

		public WrappedAttributeModifier build() {
			if (name == null || operation == null) {
				throw new IllegalStateException("Key, name, and operation must be set");
			}
			if (key == null) {
				key = new MinecraftKey("intave", "custom_modifier");
			}
			return new WrappedAttributeModifier(key, uuid, name, operation, amount);
		}
	}

	public enum Operation {
		ADD_NUMBER(0),
		MULTIPLY_PERCENTAGE(1),
		ADD_PERCENTAGE(2);

		private final int id;

		Operation(int id) {
			this.id = id;
		}

		public int getId() {
			return this.id;
		}

		public static Operation fromId(int id) {
			for (Operation op : values()) {
				if (op.getId() == id) {
					return op;
				}
			}
			throw new IllegalArgumentException("Invalid operation id: " + id);
		}
	}
}
