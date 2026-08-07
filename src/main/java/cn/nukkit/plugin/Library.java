package cn.nukkit.plugin;

/**
 * @deprecated 改用 {@link LibraryLoader.Coordinate}（不可变 record）；保留仅为向后兼容。 / Use {@link LibraryLoader.Coordinate} instead; retained for backward compatibility.
 */
@Deprecated
public interface Library {

    String getGroupId();

    String getArtifactId();

    String getVersion();
}
