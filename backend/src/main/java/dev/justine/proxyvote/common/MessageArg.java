package dev.justine.proxyvote.common;

/** A message argument that is itself a message key, e.g. "entity.employee" -> "Employee" / "Empleyado". */
public record MessageArg(String key) {}
