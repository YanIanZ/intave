package de.jpx3.intave.security;

import de.jpx3.intave.resource.Resource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class HashList {
  private final Set<String> hashes;
  private final MessageDigest sha256Digest;

  private HashList(Set<String> hashes) {
    this.hashes = ConcurrentHashMap.newKeySet();
    this.hashes.addAll(hashes);
    try {
      this.sha256Digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Unable to find SHA-256 hashing digest", exception);
    }
  }

  public boolean containsName(String name) {
    return hashBlacklisted(hashOf(name)) || hashBlacklisted(hashOf(name.toLowerCase()))
      || hashBlacklisted(hashOf(name.toUpperCase()));
  }

  public boolean containsId(UUID id) {
    return hashBlacklisted(hashOf(id.toString()));
  }

  private boolean hashBlacklisted(String hash) {
    return hashes.contains(hash);
  }

  private String hashOf(String input) {
    // HashList is now mutated from the cross-server thread while players are hashed on join.
    synchronized (sha256Digest) {
      return bytesToHex(sha256Digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (byte daBite : hash) {
      String hex = Integer.toHexString(0xff & daBite);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  public void addName(String name) {
    if (name == null || name.isEmpty()) {
      return;
    }
    hashes.add(hashOf(name));
    hashes.add(hashOf(name.toLowerCase()));
    hashes.add(hashOf(name.toUpperCase()));
  }

  public void addId(UUID id) {
    if (id == null) {
      return;
    }
    hashes.add(hashOf(id.toString()));
  }

  public static HashList empty() {
    return new HashList(new HashSet<>());
  }

  public static HashList from(Resource resource) {
    return new HashList(new HashSet<>(resource.readLines()));
  }
}
