package de.jpx3.intave.player.attribute;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WrappedAttribute {
	private final String attributeKey;
	private final double defaultBaseValue;
	private double baseValue;
	private final Set<WrappedAttributeModifier> attributeModifiers = new HashSet<>();

	public WrappedAttribute(String attributeKey, double defaultBaseValue) {
		this.attributeKey = attributeKey;
		this.defaultBaseValue = defaultBaseValue;
	}

	public static WrappedAttribute fromProtocolLib(
		com.comphenix.protocol.wrappers.WrappedAttribute attribute
	) {
		WrappedAttribute wrappedAttribute = new WrappedAttribute(attribute.getAttributeKey(), attribute.getBaseValue());
		wrappedAttribute.baseValue = attribute.getBaseValue();
		wrappedAttribute.attributeModifiers.addAll(WrappedAttributeModifier.fromProtocolLib(attribute.getModifiers()));
		return wrappedAttribute;
	}

	public String attributeKey() {
		return attributeKey;
	}

	public double baseValue() {
		return baseValue;
	}

	public Set<WrappedAttributeModifier> modifiers() {
		return Collections.unmodifiableSet(attributeModifiers);
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static Builder newBuilder(WrappedAttribute wrappedAttribute) {
		return new Builder()
			.withAttributeKey(wrappedAttribute.attributeKey)
			.withDefaultBaseValue(wrappedAttribute.defaultBaseValue)
			.withBaseValue(wrappedAttribute.baseValue)
			.withAttributeModifiers(wrappedAttribute.attributeModifiers);
	}

	public static class Builder {
		private String attributeKey;
		private double defaultBaseValue;
		private double baseValue;
		private final Set<WrappedAttributeModifier> attributeModifiers = new HashSet<>();

		public Builder withAttributeKey(String attributeKey) {
			this.attributeKey = attributeKey;
			return this;
		}

		public Builder withDefaultBaseValue(double defaultBaseValue) {
			this.defaultBaseValue = defaultBaseValue;
			return this;
		}

		public Builder withBaseValue(double baseValue) {
			this.baseValue = baseValue;
			return this;
		}

		public Builder withAttributeModifiers(Set<WrappedAttributeModifier> attributeModifiers) {
			this.attributeModifiers.clear();
			this.attributeModifiers.addAll(attributeModifiers);
			return this;
		}

		public WrappedAttribute build() {
			WrappedAttribute attribute = new WrappedAttribute(attributeKey, defaultBaseValue);
			attribute.baseValue = baseValue;
			attribute.attributeModifiers.addAll(attributeModifiers);
			return attribute;
		}
	}
}
