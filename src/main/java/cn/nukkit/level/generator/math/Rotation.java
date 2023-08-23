package cn.nukkit.level.generator.math;

import cn.nukkit.block.BlockID;

public enum Rotation {
    NONE,
    CLOCKWISE_90,
    CLOCKWISE_180,
    COUNTERCLOCKWISE_90;

    public static int clockwise90(final int id, int meta) {
        switch (id) {
            case BlockID.TORCH:
            case BlockID.UNLIT_REDSTONE_TORCH:
            case BlockID.REDSTONE_TORCH:
                switch (meta) {
                    case 1 -> {
                        return 3;
                    }
                    case 2 -> {
                        return 4;
                    }
                    case 3 -> {
                        return 2;
                    }
                    case 4 -> {
                        return 1;
                    }
                }
                break;

            case BlockID.RAIL:
                switch (meta) {
                    case 6 -> {
                        return 7;
                    }
                    case 7 -> {
                        return 8;
                    }
                    case 8 -> {
                        return 9;
                    }
                    case 9 -> {
                        return 6;
                    }
                }

            case BlockID.POWERED_RAIL:
            case BlockID.DETECTOR_RAIL:
            case BlockID.ACTIVATOR_RAIL:
                switch (meta & 0x7) {
                    case 0 -> {
                        return 1 | meta & ~0x7;
                    }
                    case 1 -> {
                        return meta & ~0x7;
                    }
                    case 2 -> {
                        return 5 | meta & ~0x7;
                    }
                    case 3 -> {
                        return 4 | meta & ~0x7;
                    }
                    case 4 -> {
                        return 2 | meta & ~0x7;
                    }
                    case 5 -> {
                        return 3 | meta & ~0x7;
                    }
                }
                break;

            case BlockID.RED_SANDSTONE_STAIRS:
            case BlockID.WOOD_STAIRS:
            case BlockID.COBBLESTONE_STAIRS:
            case BlockID.BRICK_STAIRS:
            case BlockID.STONE_BRICK_STAIRS:
            case BlockID.NETHER_BRICKS_STAIRS:
            case BlockID.SANDSTONE_STAIRS:
            case BlockID.SPRUCE_WOOD_STAIRS:
            case BlockID.BIRCH_WOOD_STAIRS:
            case BlockID.JUNGLE_WOOD_STAIRS:
            case BlockID.QUARTZ_STAIRS:
            case BlockID.ACACIA_WOODEN_STAIRS:
            case BlockID.DARK_OAK_WOODEN_STAIRS:
            case BlockID.PURPUR_STAIRS:
                switch (meta) {
                    case 0 -> {
                        return 2;
                    }
                    case 1 -> {
                        return 3;
                    }
                    case 2 -> {
                        return 1;
                    }
                    case 3 -> {
                        return 0;
                    }
                    case 4 -> {
                        return 6;
                    }
                    case 5 -> {
                        return 7;
                    }
                    case 6 -> {
                        return 5;
                    }
                    case 7 -> {
                        return 4;
                    }
                }
                break;

            case BlockID.STONE_BUTTON:
            case BlockID.WOODEN_BUTTON: {
                final int thrown = meta & 0x8;
                switch (meta & ~0x8) {
                    case 2 -> {
                        return 5 | thrown;
                    }
                    case 3 -> {
                        return 4 | thrown;
                    }
                    case 4 -> {
                        return 2 | thrown;
                    }
                    case 5 -> {
                        return 3 | thrown;
                    }
                }
                break;
            }

            case BlockID.LEVER: {
                final int thrown = meta & 0x8;
                switch (meta & ~0x8) {
                    case 1 -> {
                        return 3 | thrown;
                    }
                    case 2 -> {
                        return 4 | thrown;
                    }
                    case 3 -> {
                        return 2 | thrown;
                    }
                    case 4 -> {
                        return 1 | thrown;
                    }
                    case 5 -> {
                        return 6 | thrown;
                    }
                    case 6 -> {
                        return 5 | thrown;
                    }
                    case 7 -> {
                        return thrown;
                    }
                    case 0 -> {
                        return 7 | thrown;
                    }
                }
                break;
            }

            case BlockID.WOODEN_DOOR_BLOCK:
            case BlockID.IRON_DOOR_BLOCK:
            case BlockID.SPRUCE_DOOR_BLOCK:
            case BlockID.BIRCH_DOOR_BLOCK:
            case BlockID.JUNGLE_DOOR_BLOCK:
            case BlockID.ACACIA_DOOR_BLOCK:
            case BlockID.DARK_OAK_DOOR_BLOCK:
                if ((meta & 0x8) != 0) {
                    break;
                }

            case BlockID.END_PORTAL_FRAME:
            case BlockID.COCOA:
            case BlockID.TRIPWIRE_HOOK: {
                final int extra = meta & ~0x3;
                final int withoutFlags = meta & 0x3;
                switch (withoutFlags) {
                    case 0 -> {
                        return 1 | extra;
                    }
                    case 1 -> {
                        return 2 | extra;
                    }
                    case 2 -> {
                        return 3 | extra;
                    }
                    case 3 -> {
                        return extra;
                    }
                }
                break;
            }
            case BlockID.SIGN_POST:
            case BlockID.STANDING_BANNER:
                return (meta + 4) % 16;

            case BlockID.LADDER:
            case BlockID.WALL_SIGN:
            case BlockID.WALL_BANNER:
            case BlockID.CHEST:
            case BlockID.FURNACE:
            case BlockID.BURNING_FURNACE:
            case BlockID.ENDER_CHEST:
            case BlockID.TRAPPED_CHEST:
            case BlockID.HOPPER_BLOCK: {
                final int extra = meta & 0x8;
                final int withoutFlags = meta & ~0x8;
                switch (withoutFlags) {
                    case 2 -> {
                        return 5 | extra;
                    }
                    case 3 -> {
                        return 4 | extra;
                    }
                    case 4 -> {
                        return 2 | extra;
                    }
                    case 5 -> {
                        return 3 | extra;
                    }
                }
                break;
            }
            case BlockID.DISPENSER:
            case BlockID.DROPPER:
            case BlockID.END_ROD:
                final int dispPower = meta & 0x8;
                switch (meta & ~0x8) {
                    case 2 -> {
                        return 5 | dispPower;
                    }
                    case 3 -> {
                        return 4 | dispPower;
                    }
                    case 4 -> {
                        return 2 | dispPower;
                    }
                    case 5 -> {
                        return 3 | dispPower;
                    }
                }
                break;

            case BlockID.PUMPKIN:
            case BlockID.JACK_O_LANTERN:
                switch (meta) {
                    case 0 -> {
                        return 1;
                    }
                    case 1 -> {
                        return 2;
                    }
                    case 2 -> {
                        return 3;
                    }
                    case 3 -> {
                        return 0;
                    }
                }
                break;

            case BlockID.HAY_BALE:
            case BlockID.LOG:
            case BlockID.LOG2:
            case BlockID.QUARTZ_BLOCK:
            case BlockID.PURPUR_BLOCK:
            case BlockID.BONE_BLOCK:
                if (meta >= 4 && meta <= 11) meta ^= 0xc;
                break;

            case BlockID.UNPOWERED_COMPARATOR:
            case BlockID.POWERED_COMPARATOR:
            case BlockID.UNPOWERED_REPEATER:
            case BlockID.POWERED_REPEATER:
                final int dir = meta & 0x03;
                final int delay = meta - dir;
                switch (dir) {
                    case 0 -> {
                        return 1 | delay;
                    }
                    case 1 -> {
                        return 2 | delay;
                    }
                    case 2 -> {
                        return 3 | delay;
                    }
                    case 3 -> {
                        return delay;
                    }
                }
                break;

            case BlockID.TRAPDOOR:
            case BlockID.IRON_TRAPDOOR:
                final int withoutOrientation = meta & ~0x3;
                final int orientation = meta & 0x3;
                switch (orientation) {
                    case 0 -> {
                        return 3 | withoutOrientation;
                    }
                    case 1 -> {
                        return 2 | withoutOrientation;
                    }
                    case 2 -> {
                        return withoutOrientation;
                    }
                    case 3 -> {
                        return 1 | withoutOrientation;
                    }
                }
                break;

            case BlockID.PISTON:
            case BlockID.STICKY_PISTON:
            case BlockID.PISTON_HEAD:
            case 137: //BlockID.COMMAND_BLOCK
            case 188: //BlockID.REPEATING_COMMAND_BLOCK
            case 189: //BlockID.CHAIN_COMMAND_BLOCK
                final int rest = meta & ~0x7;
                switch (meta & 0x7) {
                    case 2 -> {
                        return 5 | rest;
                    }
                    case 3 -> {
                        return 4 | rest;
                    }
                    case 4 -> {
                        return 2 | rest;
                    }
                    case 5 -> {
                        return 3 | rest;
                    }
                }
                break;

            case BlockID.BROWN_MUSHROOM_BLOCK:
            case BlockID.RED_MUSHROOM_BLOCK:
                if (meta >= 10) return meta;
                return meta * 3 % 10;

            case BlockID.VINE:
                return (meta << 1 | meta >> 3) & 0xf;

            case BlockID.FENCE_GATE:
            case BlockID.FENCE_GATE_SPRUCE:
            case BlockID.FENCE_GATE_BIRCH:
            case BlockID.FENCE_GATE_JUNGLE:
            case BlockID.FENCE_GATE_DARK_OAK:
            case BlockID.FENCE_GATE_ACACIA:
                return meta + 1 & 0x3 | meta & ~0x3;

            case BlockID.ANVIL:
                final int damage = meta & ~0x3;
                switch (meta & 0x3) {
                    case 0 -> {
                        return 3 | damage;
                    }
                    case 2 -> {
                        return 1 | damage;
                    }
                    case 1 -> {
                        return damage;
                    }
                    case 3 -> {
                        return 2 | damage;
                    }
                }
                break;

            case BlockID.BED_BLOCK:
                return meta & ~0x3 | meta + 1 & 0x3;

            case BlockID.SKULL_BLOCK:
            case BlockID.PURPLE_GLAZED_TERRACOTTA:
            case BlockID.WHITE_GLAZED_TERRACOTTA:
            case BlockID.ORANGE_GLAZED_TERRACOTTA:
            case BlockID.MAGENTA_GLAZED_TERRACOTTA:
            case BlockID.LIGHT_BLUE_GLAZED_TERRACOTTA:
            case BlockID.YELLOW_GLAZED_TERRACOTTA:
            case BlockID.LIME_GLAZED_TERRACOTTA:
            case BlockID.PINK_GLAZED_TERRACOTTA:
            case BlockID.GRAY_GLAZED_TERRACOTTA:
            case BlockID.SILVER_GLAZED_TERRACOTTA:
            case BlockID.CYAN_GLAZED_TERRACOTTA:
            case BlockID.BLUE_GLAZED_TERRACOTTA:
            case BlockID.BROWN_GLAZED_TERRACOTTA:
            case BlockID.GREEN_GLAZED_TERRACOTTA:
            case BlockID.RED_GLAZED_TERRACOTTA:
            case BlockID.BLACK_GLAZED_TERRACOTTA:
            case BlockID.OBSERVER:
                switch (meta) {
                    case 2 -> {
                        return 5;
                    }
                    case 3 -> {
                        return 4;
                    }
                    case 4 -> {
                        return 2;
                    }
                    case 5 -> {
                        return 3;
                    }
                }
                break;

            case BlockID.NETHER_PORTAL:
                return meta + 1 & 0x1;

            case BlockID.ITEM_FRAME_BLOCK:
                switch (meta) {
                    case 0 -> {
                        return 2;
                    }
                    case 1 -> {
                        return 3;
                    }
                    case 2 -> {
                        return 1;
                    }
                    case 3 -> {
                        return 0;
                    }
                }
                break;

        }

        return meta;
    }

    public static int counterclockwise90(final int id, int meta) {
        switch (id) {
            case BlockID.TORCH:
            case BlockID.UNLIT_REDSTONE_TORCH:
            case BlockID.REDSTONE_TORCH:
                switch (meta) {
                    case 3 -> {
                        return 1;
                    }
                    case 4 -> {
                        return 2;
                    }
                    case 2 -> {
                        return 3;
                    }
                    case 1 -> {
                        return 4;
                    }
                }
                break;

            case BlockID.RAIL:
                switch (meta) {
                    case 7 -> {
                        return 6;
                    }
                    case 8 -> {
                        return 7;
                    }
                    case 9 -> {
                        return 8;
                    }
                    case 6 -> {
                        return 9;
                    }
                }

            case BlockID.POWERED_RAIL:
            case BlockID.DETECTOR_RAIL:
            case BlockID.ACTIVATOR_RAIL:
                final int power = meta & ~0x7;
                switch (meta & 0x7) {
                    case 1 -> {
                        return power;
                    }
                    case 0 -> {
                        return 1 | power;
                    }
                    case 5 -> {
                        return 2 | power;
                    }
                    case 4 -> {
                        return 3 | power;
                    }
                    case 2 -> {
                        return 4 | power;
                    }
                    case 3 -> {
                        return 5 | power;
                    }
                }
                break;

            case BlockID.RED_SANDSTONE_STAIRS:
            case BlockID.WOOD_STAIRS:
            case BlockID.COBBLESTONE_STAIRS:
            case BlockID.BRICK_STAIRS:
            case BlockID.STONE_BRICK_STAIRS:
            case BlockID.NETHER_BRICKS_STAIRS:
            case BlockID.SANDSTONE_STAIRS:
            case BlockID.SPRUCE_WOOD_STAIRS:
            case BlockID.BIRCH_WOOD_STAIRS:
            case BlockID.JUNGLE_WOOD_STAIRS:
            case BlockID.QUARTZ_STAIRS:
            case BlockID.ACACIA_WOODEN_STAIRS:
            case BlockID.DARK_OAK_WOODEN_STAIRS:
            case BlockID.PURPUR_STAIRS:
                switch (meta) {
                    case 2 -> {
                        return 0;
                    }
                    case 3 -> {
                        return 1;
                    }
                    case 1 -> {
                        return 2;
                    }
                    case 0 -> {
                        return 3;
                    }
                    case 6 -> {
                        return 4;
                    }
                    case 7 -> {
                        return 5;
                    }
                    case 5 -> {
                        return 6;
                    }
                    case 4 -> {
                        return 7;
                    }
                }
                break;

            case BlockID.STONE_BUTTON:
            case BlockID.WOODEN_BUTTON: {
                final int thrown = meta & 0x8;
                switch (meta & ~0x8) {
                    case 4 -> {
                        return 3 | thrown;
                    }
                    case 5 -> {
                        return 2 | thrown;
                    }
                    case 3 -> {
                        return 5 | thrown;
                    }
                    case 2 -> {
                        return 4 | thrown;
                    }
                }
                break;
            }

            case BlockID.LEVER: {
                final int thrown = meta & 0x8;
                switch (meta & ~0x8) {
                    case 3 -> {
                        return 1 | thrown;
                    }
                    case 4 -> {
                        return 2 | thrown;
                    }
                    case 2 -> {
                        return 3 | thrown;
                    }
                    case 1 -> {
                        return 4 | thrown;
                    }
                    case 6 -> {
                        return 5 | thrown;
                    }
                    case 5 -> {
                        return 6 | thrown;
                    }
                    case 0 -> {
                        return 7 | thrown;
                    }
                    case 7 -> {
                        return thrown;
                    }
                }
                break;
            }

            case BlockID.WOODEN_DOOR_BLOCK:
            case BlockID.IRON_DOOR_BLOCK:
            case BlockID.SPRUCE_DOOR_BLOCK:
            case BlockID.BIRCH_DOOR_BLOCK:
            case BlockID.JUNGLE_DOOR_BLOCK:
            case BlockID.ACACIA_DOOR_BLOCK:
            case BlockID.DARK_OAK_DOOR_BLOCK:
                if ((meta & 0x8) != 0) {
                    break;
                }

            case BlockID.END_PORTAL_FRAME:
            case BlockID.COCOA:
            case BlockID.TRIPWIRE_HOOK: {
                final int extra = meta & ~0x3;
                final int withoutFlags = meta & 0x3;
                switch (withoutFlags) {
                    case 1 -> {
                        return extra;
                    }
                    case 2 -> {
                        return 1 | extra;
                    }
                    case 3 -> {
                        return 2 | extra;
                    }
                    case 0 -> {
                        return 3 | extra;
                    }
                }
                break;
            }
            case BlockID.SIGN_POST:
            case BlockID.STANDING_BANNER:
                return (meta + 12) % 16;

            case BlockID.LADDER:
            case BlockID.WALL_SIGN:
            case BlockID.WALL_BANNER:
            case BlockID.CHEST:
            case BlockID.FURNACE:
            case BlockID.BURNING_FURNACE:
            case BlockID.ENDER_CHEST:
            case BlockID.TRAPPED_CHEST:
            case BlockID.HOPPER_BLOCK: {
                final int extra = meta & 0x8;
                final int withoutFlags = meta & ~0x8;
                switch (withoutFlags) {
                    case 5 -> {
                        return 2 | extra;
                    }
                    case 4 -> {
                        return 3 | extra;
                    }
                    case 2 -> {
                        return 4 | extra;
                    }
                    case 3 -> {
                        return 5 | extra;
                    }
                }
                break;
            }
            case BlockID.DISPENSER:
            case BlockID.DROPPER:
            case BlockID.END_ROD:
                final int dispPower = meta & 0x8;
                switch (meta & ~0x8) {
                    case 5 -> {
                        return 2 | dispPower;
                    }
                    case 4 -> {
                        return 3 | dispPower;
                    }
                    case 2 -> {
                        return 4 | dispPower;
                    }
                    case 3 -> {
                        return 5 | dispPower;
                    }
                }
                break;
            case BlockID.PUMPKIN:
            case BlockID.JACK_O_LANTERN:
                switch (meta) {
                    case 1 -> {
                        return 0;
                    }
                    case 2 -> {
                        return 1;
                    }
                    case 3 -> {
                        return 2;
                    }
                    case 0 -> {
                        return 3;
                    }
                }
                break;
            case BlockID.HAY_BALE:
            case BlockID.LOG:
            case BlockID.LOG2:
            case BlockID.QUARTZ_BLOCK:
            case BlockID.PURPUR_BLOCK:
            case BlockID.BONE_BLOCK:
                if (meta >= 4 && meta <= 11) meta ^= 0xc;
                break;

            case BlockID.UNPOWERED_COMPARATOR:
            case BlockID.POWERED_COMPARATOR:
            case BlockID.UNPOWERED_REPEATER:
            case BlockID.POWERED_REPEATER:
                final int dir = meta & 0x03;
                final int delay = meta - dir;
                switch (dir) {
                    case 1 -> {
                        return delay;
                    }
                    case 2 -> {
                        return 1 | delay;
                    }
                    case 3 -> {
                        return 2 | delay;
                    }
                    case 0 -> {
                        return 3 | delay;
                    }
                }
                break;

            case BlockID.TRAPDOOR:
            case BlockID.IRON_TRAPDOOR:
                final int withoutOrientation = meta & ~0x3;
                final int orientation = meta & 0x3;
                switch (orientation) {
                    case 3 -> {
                        return withoutOrientation;
                    }
                    case 2 -> {
                        return 1 | withoutOrientation;
                    }
                    case 0 -> {
                        return 2 | withoutOrientation;
                    }
                    case 1 -> {
                        return 3 | withoutOrientation;
                    }
                }

            case BlockID.PISTON:
            case BlockID.STICKY_PISTON:
            case BlockID.PISTON_HEAD:
            case 137: //BlockID.COMMAND_BLOCK
            case 188: //BlockID.REPEATING_COMMAND_BLOCK
            case 189: //BlockID.CHAIN_COMMAND_BLOCK
                final int rest = meta & ~0x7;
                switch (meta & 0x7) {
                    case 5 -> {
                        return 2 | rest;
                    }
                    case 4 -> {
                        return 3 | rest;
                    }
                    case 2 -> {
                        return 4 | rest;
                    }
                    case 3 -> {
                        return 5 | rest;
                    }
                }
                break;

            case BlockID.BROWN_MUSHROOM_BLOCK:
            case BlockID.RED_MUSHROOM_BLOCK:
                if (meta >= 10) return meta;
                return meta * 7 % 10;

            case BlockID.VINE:
                return (meta >> 1 | meta << 3) & 0xf;

            case BlockID.FENCE_GATE:
            case BlockID.FENCE_GATE_SPRUCE:
            case BlockID.FENCE_GATE_BIRCH:
            case BlockID.FENCE_GATE_JUNGLE:
            case BlockID.FENCE_GATE_DARK_OAK:
            case BlockID.FENCE_GATE_ACACIA:
                return meta + 3 & 0x3 | meta & ~0x3;

            case BlockID.ANVIL:
                final int damage = meta & ~0x3;
                switch (meta & 0x3) {
                    case 0 -> {
                        return 1 | damage;
                    }
                    case 2 -> {
                        return 3 | damage;
                    }
                    case 1 -> {
                        return 2 | damage;
                    }
                    case 3 -> {
                        return damage;
                    }
                }
                break;

            case BlockID.BED_BLOCK:
                return meta & ~0x3 | meta - 1 & 0x3;

            case BlockID.SKULL_BLOCK:
            case BlockID.PURPLE_GLAZED_TERRACOTTA:
            case BlockID.WHITE_GLAZED_TERRACOTTA:
            case BlockID.ORANGE_GLAZED_TERRACOTTA:
            case BlockID.MAGENTA_GLAZED_TERRACOTTA:
            case BlockID.LIGHT_BLUE_GLAZED_TERRACOTTA:
            case BlockID.YELLOW_GLAZED_TERRACOTTA:
            case BlockID.LIME_GLAZED_TERRACOTTA:
            case BlockID.PINK_GLAZED_TERRACOTTA:
            case BlockID.GRAY_GLAZED_TERRACOTTA:
            case BlockID.SILVER_GLAZED_TERRACOTTA:
            case BlockID.CYAN_GLAZED_TERRACOTTA:
            case BlockID.BLUE_GLAZED_TERRACOTTA:
            case BlockID.BROWN_GLAZED_TERRACOTTA:
            case BlockID.GREEN_GLAZED_TERRACOTTA:
            case BlockID.RED_GLAZED_TERRACOTTA:
            case BlockID.BLACK_GLAZED_TERRACOTTA:
            case BlockID.OBSERVER:
                switch (meta) {
                    case 2 -> {
                        return 4;
                    }
                    case 3 -> {
                        return 5;
                    }
                    case 4 -> {
                        return 3;
                    }
                    case 5 -> {
                        return 2;
                    }
                }
                break;

            case BlockID.NETHER_PORTAL:
                return meta + 1 & 0x1;

            case BlockID.ITEM_FRAME_BLOCK:
                switch (meta) {
                    case 0 -> {
                        return 3;
                    }
                    case 1 -> {
                        return 2;
                    }
                    case 2 -> {
                        return 0;
                    }
                    case 3 -> {
                        return 1;
                    }
                }
                break;

        }

        return meta;
    }

    public static int clockwise180(final int id, final int meta) {
        switch (id) {
            case BlockID.TORCH:
            case BlockID.UNLIT_REDSTONE_TORCH:
            case BlockID.REDSTONE_TORCH:
                switch (meta) {
                    case 1 -> {
                        return 2;
                    }
                    case 2 -> {
                        return 1;
                    }
                    case 3 -> {
                        return 4;
                    }
                    case 4 -> {
                        return 3;
                    }
                    // 5 is vertical
                }
                break;

            case BlockID.RAIL:
                switch (meta) {
                    case 6 -> {
                        return 8;
                    }
                    case 7 -> {
                        return 9;
                    }
                    case 8 -> {
                        return 6;
                    }
                    case 9 -> {
                        return 7;
                    }
                }


            case BlockID.POWERED_RAIL:
            case BlockID.DETECTOR_RAIL:
            case BlockID.ACTIVATOR_RAIL:
                switch (meta & 0x7) {
                    case 0 -> {
                        return meta & ~0x7;
                    }
                    case 1 -> {
                        return 1 | meta & ~0x7;
                    }
                    case 2 -> {
                        return 3 | meta & ~0x7;
                    }
                    case 3 -> {
                        return 2 | meta & ~0x7;
                    }
                    case 4 -> {
                        return 5 | meta & ~0x7;
					}
					case 5 -> {
						return 4 | meta & ~0x7;
					}
				}
				break;

			case BlockID.RED_SANDSTONE_STAIRS:
			case BlockID.WOOD_STAIRS:
			case BlockID.COBBLESTONE_STAIRS:
			case BlockID.BRICK_STAIRS:
			case BlockID.STONE_BRICK_STAIRS:
			case BlockID.NETHER_BRICKS_STAIRS:
			case BlockID.SANDSTONE_STAIRS:
			case BlockID.SPRUCE_WOOD_STAIRS:
			case BlockID.BIRCH_WOOD_STAIRS:
			case BlockID.JUNGLE_WOOD_STAIRS:
			case BlockID.QUARTZ_STAIRS:
			case BlockID.ACACIA_WOODEN_STAIRS:
			case BlockID.DARK_OAK_WOODEN_STAIRS:
			case BlockID.PURPUR_STAIRS:
				switch (meta) {
					case 0 -> {
						return 1;
					}
					case 1 -> {
						return 0;
					}
					case 2 -> {
						return 3;
					}
					case 3 -> {
						return 2;
					}
					case 4 -> {
						return 5;
					}
					case 5 -> {
						return 4;
					}
					case 6 -> {
						return 7;
					}
					case 7 -> {
						return 6;
					}
				}
				break;

			case BlockID.STONE_BUTTON:
			case BlockID.WOODEN_BUTTON: {
				final int thrown = meta & 0x8;
				switch (meta & ~0x8) {
					case 2 -> {
						return 3 | thrown;
					}
					case 3 -> {
						return 2 | thrown;
					}
					case 4 -> {
						return 5 | thrown;
					}
					case 5 -> {
						return 4 | thrown;
					}
					// 0 and 1 are vertical
				}
				break;
			}

			case BlockID.LEVER: {
				final int thrown = meta & 0x8;
				switch (meta & ~0x8) {
					case 1 -> {
						return 2 | thrown;
					}
					case 2 -> {
						return 1 | thrown;
					}
					case 3 -> {
						return 4 | thrown;
					}
					case 4 -> {
						return 3 | thrown;
					}
					case 5 -> {
						return 5 | thrown;
					}
					case 6 -> {
						return 6 | thrown;
					}
					case 7 -> {
						return 7 | thrown;
					}
					case 0 -> {
						return thrown;
					}
				}
				break;
			}

			case BlockID.WOODEN_DOOR_BLOCK:
			case BlockID.IRON_DOOR_BLOCK:
			case BlockID.SPRUCE_DOOR_BLOCK:
			case BlockID.BIRCH_DOOR_BLOCK:
			case BlockID.JUNGLE_DOOR_BLOCK:
			case BlockID.ACACIA_DOOR_BLOCK:
			case BlockID.DARK_OAK_DOOR_BLOCK:
				if ((meta & 0x8) != 0) {
					// door top halves contain no orientation information
					break;
				}


			case BlockID.END_PORTAL_FRAME:
			case BlockID.COCOA:
			case BlockID.TRIPWIRE_HOOK: {
				final int extra = meta & ~0x3;
				final int withoutFlags = meta & 0x3;
				switch (withoutFlags) {
					case 0 -> {
						return 2 | extra;
					}
					case 1 -> {
						return 3 | extra;
					}
					case 2 -> {
						return extra;
					}
					case 3 -> {
						return 1 | extra;
					}
				}
				break;
			}
			case BlockID.SIGN_POST:
			case BlockID.STANDING_BANNER:
				return (meta + 8) % 16;

			case BlockID.LADDER:
			case BlockID.WALL_SIGN:
			case BlockID.WALL_BANNER:
			case BlockID.CHEST:
			case BlockID.FURNACE:
			case BlockID.BURNING_FURNACE:
			case BlockID.ENDER_CHEST:
			case BlockID.TRAPPED_CHEST:
			case BlockID.HOPPER_BLOCK: {
				final int extra = meta & 0x8;
				final int withoutFlags = meta & ~0x8;
				switch (withoutFlags) {
					case 2 -> {
						return 3 | extra;
					}
					case 3 -> {
						return 2 | extra;
					}
					case 4 -> {
						return 5 | extra;
					}
					case 5 -> {
						return 4 | extra;
					}
				}
				break;
			}
			case BlockID.DISPENSER:
			case BlockID.DROPPER:
			case BlockID.END_ROD:
				final int dispPower = meta & 0x8;
				switch (meta & ~0x8) {
					case 2 -> {
						return 3 | dispPower;
					}
					case 3 -> {
						return 2 | dispPower;
					}
					case 4 -> {
						return 5 | dispPower;
					}
					case 5 -> {
						return 4 | dispPower;
					}
				}
				break;

			case BlockID.PUMPKIN:
			case BlockID.JACK_O_LANTERN:
				switch (meta) {
					case 0 -> {
						return 2;
					}
					case 1 -> {
						return 3;
					}
					case 2 -> {
						return 0;
					}
					case 3 -> {
						return 1;
					}
				}
				break;

			case BlockID.HAY_BALE:
			case BlockID.LOG:
			case BlockID.LOG2:
			case BlockID.QUARTZ_BLOCK:
			case BlockID.PURPUR_BLOCK:
			case BlockID.BONE_BLOCK, BlockID.NETHER_PORTAL:
				break;

			case BlockID.UNPOWERED_COMPARATOR:
			case BlockID.POWERED_COMPARATOR:
			case BlockID.UNPOWERED_REPEATER:
			case BlockID.POWERED_REPEATER:
				final int dir = meta & 0x03;
				final int delay = meta - dir;
				switch (dir) {
					case 0 -> {
						return 2 | delay;
					}
					case 1 -> {
						return 3 | delay;
					}
					case 2 -> {
						return delay;
					}
					case 3 -> {
						return 1 | delay;
					}
				}
				break;

			case BlockID.TRAPDOOR:
			case BlockID.IRON_TRAPDOOR:
				final int withoutOrientation = meta & ~0x3;
				final int orientation = meta & 0x3;
				switch (orientation) {
					case 0 -> {
						return 1 | withoutOrientation;
					}
					case 1 -> {
						return withoutOrientation;
					}
					case 2 -> {
						return 3 | withoutOrientation;
					}
					case 3 -> {
						return 2 | withoutOrientation;
					}
				}
				break;

			case BlockID.PISTON:
			case BlockID.STICKY_PISTON:
			case BlockID.PISTON_HEAD:
			case 137: //BlockID.COMMAND_BLOCK
			case 188: //BlockID.REPEATING_COMMAND_BLOCK
			case 189: //BlockID.CHAIN_COMMAND_BLOCK
				final int rest = meta & ~0x7;
				switch (meta & 0x7) {
					case 2 -> {
						return 3 | rest;
					}
					case 3 -> {
						return 2 | rest;
					}
					case 4 -> {
						return 5 | rest;
					}
					case 5 -> {
						return 4 | rest;
					}
				}
				break;

			case BlockID.BROWN_MUSHROOM_BLOCK:
			case BlockID.RED_MUSHROOM_BLOCK:
				if (meta >= 10) return meta;
				return meta * 9 % 10;

			case BlockID.VINE:
				return (meta << 2 | meta >> 2) & 0xf;

			case BlockID.FENCE_GATE:
			case BlockID.FENCE_GATE_SPRUCE:
			case BlockID.FENCE_GATE_BIRCH:
			case BlockID.FENCE_GATE_JUNGLE:
			case BlockID.FENCE_GATE_DARK_OAK:
			case BlockID.FENCE_GATE_ACACIA:
				return meta + 2 & 0x3 | meta & ~0x3;

			case BlockID.ANVIL:
				final int damage = meta & ~0x3;
				switch (meta & 0x3) {
					case 0 -> {
						return 2 | damage;
					}
					case 2 -> {
						return damage;
					}
					case 1 -> {
						return 3 | damage;
					}
					case 3 -> {
						return 1 | damage;
					}
				}
				break;

			case BlockID.BED_BLOCK:
				return meta & ~0x3 | meta + 2 & 0x3;

			case BlockID.SKULL_BLOCK:
			case BlockID.PURPLE_GLAZED_TERRACOTTA:
			case BlockID.WHITE_GLAZED_TERRACOTTA:
			case BlockID.ORANGE_GLAZED_TERRACOTTA:
			case BlockID.MAGENTA_GLAZED_TERRACOTTA:
			case BlockID.LIGHT_BLUE_GLAZED_TERRACOTTA:
			case BlockID.YELLOW_GLAZED_TERRACOTTA:
			case BlockID.LIME_GLAZED_TERRACOTTA:
			case BlockID.PINK_GLAZED_TERRACOTTA:
			case BlockID.GRAY_GLAZED_TERRACOTTA:
			case BlockID.SILVER_GLAZED_TERRACOTTA:
			case BlockID.CYAN_GLAZED_TERRACOTTA:
			case BlockID.BLUE_GLAZED_TERRACOTTA:
			case BlockID.BROWN_GLAZED_TERRACOTTA:
			case BlockID.GREEN_GLAZED_TERRACOTTA:
			case BlockID.RED_GLAZED_TERRACOTTA:
			case BlockID.BLACK_GLAZED_TERRACOTTA:
			case BlockID.OBSERVER:
				switch (meta) {
					case 2 -> {
						return 3;
					}
					case 3 -> {
						return 2;
					}
					case 4 -> {
						return 5;
					}
					case 5 -> {
						return 4;
					}
				}
				break;

			case BlockID.ITEM_FRAME_BLOCK:
				switch (meta) {
					case 0 -> {
						return 1;
					}
					case 1 -> {
						return 0;
					}
					case 2 -> {
						return 3;
					}
					case 3 -> {
						return 2;
					}
				}
				break;

		}

		return meta;
	}
}
