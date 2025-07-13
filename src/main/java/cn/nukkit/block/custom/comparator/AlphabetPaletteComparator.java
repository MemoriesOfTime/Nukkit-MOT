package cn.nukkit.block.custom.comparator;

import org.cloudburstmc.nbt.NbtMap;

import java.util.Comparator;

public class AlphabetPaletteComparator implements Comparator<String> {
    public static final AlphabetPaletteComparator INSTANCE = new AlphabetPaletteComparator();

    /*@Override
    public int compare(NbtMap o1, NbtMap o2) {
        return getIdentifier(o1).compareToIgnoreCase(getIdentifier(o2));
    }*/

    @Override
    public int compare(String o1, String o2) {
        return o1.compareToIgnoreCase(o2);
    }

    private String getIdentifier(NbtMap state) {
        return state.getString("name");
    }
}
