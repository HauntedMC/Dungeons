package nl.hauntedmc.dungeons.repository;

/** Published versions are immutable; zero denotes an unpublished local draft. */
public record RepoVersion(int major, int minor, int patch) implements Comparable<RepoVersion> {
    public static final RepoVersion DRAFT = new RepoVersion(0, 0, 0);
    public static final RepoVersion FIRST = new RepoVersion(1, 0, 0);

    public RepoVersion {
        if (major < 0 || minor < 0 || patch < 0)
            throw new IllegalArgumentException("Negative version");
    }

    public static RepoVersion parse(String value) {
        if (!value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException("Expected major.minor.patch");
        String[] parts = value.split("\\.");
        try {
            return new RepoVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Version exceeds supported range", invalid);
        }
    }

    public RepoVersion bump(String kind) {
        try {
            return switch (kind.toLowerCase(java.util.Locale.ROOT)) {
                case "major" -> new RepoVersion(Math.incrementExact(major), 0, 0);
                case "minor" -> new RepoVersion(major, Math.incrementExact(minor), 0);
                case "patch" -> new RepoVersion(major, minor, Math.incrementExact(patch));
                default -> throw new IllegalArgumentException("Choose major, minor or patch");
            };
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Version exceeds supported range", overflow);
        }
    }

    @Override
    public int compareTo(RepoVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        return result == 0 ? Integer.compare(patch, other.patch) : result;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
