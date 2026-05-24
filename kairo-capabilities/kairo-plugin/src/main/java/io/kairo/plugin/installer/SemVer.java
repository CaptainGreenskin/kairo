/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.installer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal semver value object and constraint matcher used by {@link DependencyResolver}.
 *
 * <p>Supports the npm-style operators that are common in plugin manifests:
 *
 * <ul>
 *   <li>Exact: {@code "1.2.3"} — requires exactly that version
 *   <li>Caret: {@code "^1.2.3"} — &gt;= 1.2.3 and &lt; 2.0.0 (compatible within MAJOR; for 0.x.y
 *       behaves like tilde, locking to MINOR — matches npm behaviour)
 *   <li>Tilde: {@code "~1.2.3"} — &gt;= 1.2.3 and &lt; 1.3.0
 *   <li>Range: {@code ">=1.2.3"}, {@code "<2.0.0"} — comparison with the supplied version
 *   <li>Wildcard: {@code "*"} or empty — matches any version
 * </ul>
 *
 * <p>Pre-release suffixes ({@code -alpha.1}) are accepted in version strings but are compared by
 * basic rules: pre-releases sort before the corresponding release version, and their identifiers
 * are compared lexicographically. Build metadata ({@code +123}) is ignored for ordering.
 *
 * <p>This is intentionally a small subset of full semver — sufficient for the v1.3 plugin
 * dependency model. It is not a general-purpose semver library.
 */
public record SemVer(int major, int minor, int patch, String preRelease)
        implements Comparable<SemVer> {

    private static final Pattern PARSE =
            Pattern.compile(
                    "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z0-9.-]+))?(?:\\+[A-Za-z0-9.-]+)?$");

    /** Parses {@code "MAJOR.MINOR.PATCH[-pre][+build]"}. */
    public static SemVer parse(String version) {
        if (version == null) throw new IllegalArgumentException("version is null");
        Matcher m = PARSE.matcher(version.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("not a semver: " + version);
        }
        return new SemVer(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group(4));
    }

    @Override
    public int compareTo(SemVer other) {
        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;
        c = Integer.compare(this.patch, other.patch);
        if (c != 0) return c;
        // Per semver: a version with pre-release sorts BEFORE the same version without one.
        if (this.preRelease == null && other.preRelease == null) return 0;
        if (this.preRelease == null) return 1;
        if (other.preRelease == null) return -1;
        return this.preRelease.compareTo(other.preRelease);
    }

    /**
     * Returns true iff {@code version} satisfies {@code constraint}. See class javadoc for the
     * supported constraint syntax.
     */
    public static boolean satisfies(String version, String constraint) {
        if (constraint == null || constraint.isBlank() || "*".equals(constraint.trim())) {
            return true;
        }
        SemVer v;
        try {
            v = parse(version);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String c = constraint.trim();
        if (c.startsWith("^")) {
            SemVer base = parse(c.substring(1));
            return v.compareTo(base) >= 0 && v.compareTo(caretUpper(base)) < 0;
        }
        if (c.startsWith("~")) {
            SemVer base = parse(c.substring(1));
            return v.compareTo(base) >= 0
                    && v.compareTo(new SemVer(base.major, base.minor + 1, 0, null)) < 0;
        }
        if (c.startsWith(">=")) {
            return v.compareTo(parse(c.substring(2).trim())) >= 0;
        }
        if (c.startsWith("<=")) {
            return v.compareTo(parse(c.substring(2).trim())) <= 0;
        }
        if (c.startsWith(">")) {
            return v.compareTo(parse(c.substring(1).trim())) > 0;
        }
        if (c.startsWith("<")) {
            return v.compareTo(parse(c.substring(1).trim())) < 0;
        }
        // Bare version → exact match.
        try {
            return v.compareTo(parse(c)) == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static SemVer caretUpper(SemVer base) {
        // npm semantics: for 0.x.y, ^ acts like ~ (locks to minor).
        if (base.major == 0) {
            if (base.minor == 0) {
                return new SemVer(0, 0, base.patch + 1, null);
            }
            return new SemVer(0, base.minor + 1, 0, null);
        }
        return new SemVer(base.major + 1, 0, 0, null);
    }

    @Override
    public String toString() {
        StringBuilder sb =
                new StringBuilder()
                        .append(major)
                        .append('.')
                        .append(minor)
                        .append('.')
                        .append(patch);
        if (preRelease != null) sb.append('-').append(preRelease);
        return sb.toString();
    }
}
