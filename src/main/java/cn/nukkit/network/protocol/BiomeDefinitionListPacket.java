package cn.nukkit.network.protocol;

import cn.nukkit.Nukkit;
import cn.nukkit.utils.Utils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.io.ByteStreams;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.ToString;
import lombok.Value;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.zip.Deflater;

@ToString()
public class BiomeDefinitionListPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BIOME_DEFINITION_LIST_PACKET;

    private static final DataPacket CACHED_PACKET_361;
    private static final BatchPacket CACHED_PACKET_419;
    private static final BatchPacket CACHED_PACKET_486;
    private static final BatchPacket CACHED_PACKET_527;
    private static final BatchPacket CACHED_PACKET_544;
    private static final BatchPacket CACHED_PACKET_786;
    private static final BatchPacket CACHED_PACKET;

    private static final byte[] TAG_361;
    private static final byte[] TAG_419;
    private static final byte[] TAG_486;
    private static final byte[] TAG_527;
    private static final byte[] TAG_544;
    private static final byte[] TAG_786;

    private LinkedHashMap<String, BiomeDefinitionData> biomeDefinitions;

    static {
        try {
            TAG_361 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_361.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_12_0;
            pk.tryEncode();
            CACHED_PACKET_361 = pk; //.compress(Deflater.BEST_COMPRESSION); 压缩会导致1.16.40无法进入服务器
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 361", e);
        }
        try {
            TAG_419 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_419.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_16_100;
            pk.tryEncode();
            CACHED_PACKET_419 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 419", e);
        }
        try {
            TAG_486 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_486.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_18_10;
            pk.tryEncode();
            CACHED_PACKET_486 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 486", e);
        }
        try {
            TAG_527 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_527.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_19_0;
            pk.tryEncode();
            CACHED_PACKET_527 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 527", e);
        }
        try {
            TAG_544 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_554.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_19_20;
            pk.tryEncode();
            CACHED_PACKET_544 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 554", e);
        }
        try {
            TAG_786 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_786.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_21_70;
            pk.tryEncode();
            CACHED_PACKET_786 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 554", e);
        }
        try {
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.biomeDefinitions = new GsonBuilder().registerTypeAdapter(Color.class, new ColorTypeAdapter()).create().fromJson(Utils.loadJsonResource("stripped_biome_definitions_800.json"), new TypeToken<LinkedHashMap<String, BiomeDefinitionData>>() {
            }.getType());
            pk.protocol = ProtocolInfo.v1_21_80;
            pk.tryEncode();
            CACHED_PACKET = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 800", e);
        }
    }

    public static DataPacket getCachedPacket(int protocol) {
        if (protocol < ProtocolInfo.v1_12_0) {
            throw new UnsupportedOperationException("Unsupported protocol version: " + protocol);
        }

        if (protocol >= ProtocolInfo.v1_21_80) {
            return CACHED_PACKET;
        } else if (protocol >= ProtocolInfo.v1_21_70_24) {
            return CACHED_PACKET_786;
        } else if (protocol >= ProtocolInfo.v1_19_30_23) {
            return CACHED_PACKET_544;
        } else if (protocol >= ProtocolInfo.v1_19_0) {
            return CACHED_PACKET_527;
        } else if (protocol >= ProtocolInfo.v1_18_10) {
            return CACHED_PACKET_486;
        } else if (protocol >= ProtocolInfo.v1_16_100) {
            return CACHED_PACKET_419;
        } else {
            return CACHED_PACKET_361;
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_80) {
            if (this.biomeDefinitions == null) {
                throw new RuntimeException("biomeDefinitions == null, use getCachedPacket!");
            }

            SequencedHashSet<String> strings = new SequencedHashSet<>();

            this.putUnsignedVarInt(this.biomeDefinitions.size());
            for (Map.Entry<String, BiomeDefinitionData> entry : this.biomeDefinitions.entrySet()) {
                String name = entry.getKey();
                BiomeDefinitionData definition = entry.getValue();
                this.putLShort(strings.addAndGetIndex(name));
                this.putBoolean(false); // Optional ID
                this.putLFloat(definition.getTemperature());
                this.putLFloat(definition.getDownfall());
                this.putLFloat(definition.getRedSporeDensity());
                this.putLFloat(definition.getBlueSporeDensity());
                this.putLFloat(definition.getAshDensity());
                this.putLFloat(definition.getWhiteAshDensity());
                this.putLFloat(definition.getDepth());
                this.putLFloat(definition.getScale());
                this.putLInt(definition.getMapWaterColor().getRGB());
                this.putBoolean(definition.isRain());
                this.putBoolean(false); // Optional Tags
                this.putBoolean(false); // Optional ChunkGenData
            }

            this.putUnsignedVarInt(strings.size());
            for (String str : strings) {
                this.putString(str);
            }
        } else if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.put(TAG_786);
        } else if (this.protocol >= ProtocolInfo.v1_19_30_23) {
            this.put(TAG_544);
        } else if (this.protocol >= ProtocolInfo.v1_19_0) {
            this.put(TAG_527);
        } else if (this.protocol >= ProtocolInfo.v1_18_10) {
            this.put(TAG_486);
        } else if (this.protocol >= ProtocolInfo.v1_16_100) {
            this.put(TAG_419);
        } else {
            this.put(TAG_361);
        }
    }

    @Value
    private static class BiomeDefinitionData {

        public float temperature;
        public float downfall;
        public float redSporeDensity;
        public float blueSporeDensity;
        public float ashDensity;
        public float whiteAshDensity;
        public float depth;
        public float scale;
        public Color mapWaterColor;
        public boolean rain;

        @JsonCreator
        public BiomeDefinitionData(float temperature, float downfall, float redSporeDensity,
                                   float blueSporeDensity, float ashDensity, float whiteAshDensity, float depth,
                                   float scale, Color mapWaterColor, boolean rain) {
            this.temperature = temperature;
            this.downfall = downfall;
            this.redSporeDensity = redSporeDensity;
            this.blueSporeDensity = blueSporeDensity;
            this.ashDensity = ashDensity;
            this.whiteAshDensity = whiteAshDensity;
            this.depth = depth;
            this.scale = scale;
            this.mapWaterColor = mapWaterColor;
            this.rain = rain;
        }
    }

    @SuppressWarnings({"NullableProblems", "SuspiciousMethodCalls"})
    private static class SequencedHashSet<E> implements java.util.List<E> {

        private final Object2IntMap<E> map = new Object2IntLinkedOpenHashMap<>();
        private final Int2ObjectMap<E> inverse = new Int2ObjectLinkedOpenHashMap<>();
        private int index = 0;

        @Override
        public int indexOf(Object o) {
            return map.getInt(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return map.getInt(o);
        }

        @Override
        public ListIterator<E> listIterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return map.containsKey(o);
        }

        @Override
        public Iterator<E> iterator() {
            return map.keySet().iterator();
        }

        @Override
        public Object[] toArray() {
            return map.keySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return map.keySet().toArray(a);
        }

        @Override
        public boolean add(E e) {
            if (!this.map.containsKey(e)) {
                int index = this.index++;
                this.map.put(e, index);
                this.inverse.put(index, e);
                return true;
            }
            return false;
        }

        public int addAndGetIndex(E e) {
            if (!this.map.containsKey(e)) {
                int index = this.index++;
                this.map.put(e, index);
                this.inverse.put(index, e);
                return index;
            }
            return this.map.getInt(e);
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return map.keySet().containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            for (E e : c) {
                this.add(e);
            }
            return true;
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public E get(int index) {
            return this.inverse.get(index);
        }

        @Override
        public E set(int index, E element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int index, E element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public E remove(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return map.keySet().toString();
        }
    }

    private static class ColorTypeAdapter extends TypeAdapter<Color> {

        @Override
        public void write(JsonWriter out, Color color) {
        }

        @Override
        public Color read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            int r = 0, g = 0, b = 0, a = 255;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "r": r = in.nextInt(); break;
                    case "g": g = in.nextInt(); break;
                    case "b": b = in.nextInt(); break;
                    case "a": a = in.nextInt(); break;
                    default: in.skipValue(); break;
                }
            }
            in.endObject();
            return new Color(r, g, b, a);
        }
    }
}
