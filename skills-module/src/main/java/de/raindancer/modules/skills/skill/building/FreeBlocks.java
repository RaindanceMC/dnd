package de.raindancer.modules.skills.skill.building;

import de.raindancer.modules.skills.service.BodyService;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Blocks that stand in the world for nothing — refunded when placed, or pasted by Thrift without being paid for. They
 * drop nothing when broken, so placing and breaking them again and again makes nothing out of nothing. Kept in the
 * chunk's own data, so they are remembered over a restart and go with the chunk.
 */
public final class FreeBlocks {

    static final NamespacedKey KEY = new NamespacedKey(BodyService.NAMESPACE, "free_blocks");
    private static final int Y_OFFSET = 2_048;

    private FreeBlocks() {
    }

    /** A block's place in its chunk as one number. */
    static int pack(int x, int y, int z) {
        return (x & 15) | (z & 15) << 4 | ((y + Y_OFFSET) & 0xFFF) << 8;
    }

    /** {chunk x, y, chunk z}. */
    static int[] unpack(int packed) {
        return new int[]{packed & 15, ((packed >>> 8) & 0xFFF) - Y_OFFSET, (packed >>> 4) & 15};
    }

    static int[] with(int[] marks, int mark) {
        for (int each : marks) {
            if (each == mark) {
                return marks;
            }
        }
        int[] more = Arrays.copyOf(marks, marks.length + 1);
        more[marks.length] = mark;
        return more;
    }

    static int[] without(int[] marks, int mark) {
        return Arrays.stream(marks).filter(each -> each != mark).toArray();
    }

    private static int[] marks(PersistentDataContainer data) {
        int[] marks = data.get(KEY, PersistentDataType.INTEGER_ARRAY);
        return marks == null ? new int[0] : marks;
    }

    private static void store(PersistentDataContainer data, int[] marks) {
        if (marks.length == 0) {
            data.remove(KEY);
        } else {
            data.set(KEY, PersistentDataType.INTEGER_ARRAY, marks);
        }
    }

    public static void mark(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        store(data, with(marks(data), pack(block.getX(), block.getY(), block.getZ())));
    }

    /** @return whether it was marked */
    public static boolean unmark(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        int[] before = marks(data);
        if (before.length == 0) {
            return false;
        }
        int[] after = without(before, pack(block.getX(), block.getY(), block.getZ()));
        if (after.length == before.length) {
            return false;
        }
        store(data, after);
        return true;
    }

    public static boolean isMarked(Block block) {
        if (!block.getChunk().getPersistentDataContainer().has(KEY, PersistentDataType.INTEGER_ARRAY)) {
            return false;
        }
        int mark = pack(block.getX(), block.getY(), block.getZ());
        return Arrays.stream(marks(block.getChunk().getPersistentDataContainer())).anyMatch(each -> each == mark);
    }
}
