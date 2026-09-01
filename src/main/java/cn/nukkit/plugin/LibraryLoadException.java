package cn.nukkit.plugin;

@SuppressWarnings("serial")
public class LibraryLoadException extends RuntimeException {

    public LibraryLoadException(String message) {
        super(message);
    }

    public LibraryLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @deprecated 改用 {@link #LibraryLoadException(String)}；新解析器不再依赖 {@link Library}。 / Use the String constructor instead.
     */
    @Deprecated
    public LibraryLoadException(Library library) {
        super("Load library " + (library == null ? "?" : library.getArtifactId()) + " failed!");
    }
}
