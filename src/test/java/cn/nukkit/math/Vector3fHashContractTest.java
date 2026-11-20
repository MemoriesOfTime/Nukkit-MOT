package cn.nukkit.math;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Vector3fHashContractTest {
    @Test void equalCopyFindsStoredValue() {
        Vector3f a = new Vector3f(1.25f, -3.5f, 8.125f);
        var map = new HashMap<Vector3f, String>(); map.put(a, "stored");
        assertEquals("stored", map.get(a.clone()));
    }
    @Test void signedZerosRemainEqualAndShareHash() {
        Vector3f a = new Vector3f(0f, -0f, 0f), b = new Vector3f(-0f, 0f, -0f);
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
        var set = new HashSet<Vector3f>(); set.add(a); set.add(b); assertEquals(1, set.size());
    }
    @Test void identityHashApiRemainsIdentityBased() {
        Vector3f a = new Vector3f(2, 3, 4);
        assertEquals(System.identityHashCode(a), a.rawHashCode());
    }
    @Test void fractionalCoordinatesAreNotAllOneBucket() {
        var hashes = new HashSet<Integer>();
        for (int i = 0; i < 16; i++) hashes.add(new Vector3f(i / 16f, 0, 0).hashCode());
        assertEquals(16, hashes.size());
    }
}
