package cn.nukkit;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Auto-detected JUnit extension that boots {@link MockServer} before any test runs.
 * <p>
 * Classes such as {@code cn.nukkit.entity.data.Skin} read {@code Server.getInstance()}
 * from static initializers and setters, so the first test class to load them crashes
 * with {@code ExceptionInInitializerError} unless a server instance already exists.
 * Which test class runs first depends on surefire's filesystem ordering (differs
 * between macOS and Linux CI runners), so relying on some test calling
 * {@link MockServer#init()} first is fragile. Registering this extension globally
 * via {@code META-INF/services} plus {@code junit.jupiter.extensions.autodetection.enabled}
 * guarantees the mock server is installed before the first test of every class.
 * <p>
 * Calls {@link MockServer#init()} (idempotent, also restores a cleared
 * {@code Server.instance}) but never {@link MockServer#reset()}, so tests that stub
 * the mock themselves are unaffected.
 */
public class MockServerExtension implements BeforeAllCallback, BeforeEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        MockServer.init();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        MockServer.init();
    }
}
