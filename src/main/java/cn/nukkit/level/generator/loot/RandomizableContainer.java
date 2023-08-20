package cn.nukkit.level.generator.loot;

import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Utils;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

public class RandomizableContainer {
	protected final Map<List<ItemEntry>, RollEntry> pools;
	protected final int size;

	public RandomizableContainer(final Map<List<ItemEntry>, RollEntry> pools, final int size) {
		this.pools = pools;
		this.size = size;
	}

	public final void create(final ListTag<CompoundTag> list, final NukkitRandom random) {
		final CompoundTag[] tags = new CompoundTag[size];

		pools.forEach((pool, roll) -> {
			for (int i = roll.getMin() == -1 ? roll.getMax() : random.nextRange(roll.getMin(), roll.getMax()); i > 0; --i) {
				int result = random.nextBoundedInt(roll.getTotalWeight());
				for (final ItemEntry entry : pool) {
					result -= entry.getWeight();
					if (result < 0) {
						final int index = random.nextBoundedInt(tags.length);
						final Item item = Item.get(entry.getId(), entry.getMeta(), random.nextRange(entry.getMinCount(), entry.getMaxCount()));
						if (item.getId() == Item.ENCHANT_BOOK) {
							final Enchantment enchantment = Enchantment.getEnchantment(Utils.rand(0, 35));
							if (Utils.random.nextDouble() < 0.3) {
								enchantment.setLevel(Utils.rand(1, enchantment.getMaxLevel()));
							}
							item.addEnchantment(enchantment);
						}
						tags[index] = NBTIO.putItemHelper(item, index);
						break;
					}
				}
			}
		});

		for (int i = 0; i < tags.length; i++) {
			if (tags[i] == null) {
				list.add(i, NBTIO.putItemHelper(Item.get(BlockID.AIR), i));
			} else {
				list.add(i, tags[i]);
			}
		}
	}

	protected static class RollEntry {
		private final int max;
		private final int min;
		private final int totalWeight;

		public RollEntry(final int max, final int totalWeight) {
			this(max, -1, totalWeight);
		}

		public RollEntry(final int max, final int min, final int totalWeight) {
			this.max = max;
			this.min = min;
			this.totalWeight = totalWeight;
		}

		public final int getMax() {
			return max;
		}

		public final int getMin() {
			return min;
		}

		public final int getTotalWeight() {
			return totalWeight;
		}
	}

	protected static class ItemEntry {
		private final int id;
		private final int meta;
		private final int maxCount;
		private final int minCount;
		private final int weight;

		public ItemEntry(final int id, final int weight) {
			this(id, 0, weight);
		}

		public ItemEntry(final int id, final int meta, final int weight) {
			this(id, meta, 1, weight);
		}

		public ItemEntry(final int id, final int meta, final int maxCount, final int weight) {
			this(id, meta, maxCount, 1, weight);
		}

		public ItemEntry(final int id, final int meta, final int maxCount, final int minCount, final int weight) {
			this.id = id;
			this.meta = meta;
			this.maxCount = maxCount;
			this.minCount = minCount;
			this.weight = weight;
		}

		public final int getId() {
			return id;
		}

		public final int getMeta() {
			return meta;
		}

		public final int getMaxCount() {
			return maxCount;
		}

		public final int getMinCount() {
			return minCount;
		}

		public final int getWeight() {
			return weight;
		}
	}

	protected static class PoolBuilder {
		private final List<ItemEntry> pool = Lists.newArrayList();
		private int totalWeight = 0;

		public final PoolBuilder register(final ItemEntry entry) {
			pool.add(entry);
			totalWeight += entry.getWeight();
			return this;
		}

		public final List<ItemEntry> build() {
			return pool;
		}

		public final int getTotalWeight() {
			return totalWeight;
		}
	}
}
