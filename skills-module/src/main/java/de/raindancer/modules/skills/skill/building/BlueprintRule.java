package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.world.safety.Spot;
import de.raindancer.modules.skills.rules.ISkillsRule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * What a blueprint may be and what pasting one costs. Every block placed is paid for with its own item from the
 * player's inventory, and nothing comes out of a paste that was not paid for: crops are put down unripe, cauldrons
 * empty, composters, hives and anchors empty, nothing waterlogged; the second half of a door or a bed comes with the
 * first. Air, fluids, fire, portals and blocks no survival player can hold are never placed.
 */
public final class BlueprintRule implements ISkillsRule {

    public enum Refusal { NO_DRAFTSMAN, BAD_NAME, TOO_BIG, NO_ROOM, TOO_FAR }

    /** How far from the box a player may stand to save it: a paste lands where they stand, as far from it. */
    public static final int MOST_AWAY = 8;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,24}");

    private static final Set<String> NEVER = Set.of("air", "cave_air", "void_air", "water", "lava", "bubble_column",
            "fire", "soul_fire", "nether_portal", "end_portal", "end_gateway", "moving_piston", "piston_head", "light",
            "structure_void", "barrier", "frosted_ice", "bedrock", "end_portal_frame", "reinforced_deepslate",
            "spawner", "trial_spawner", "vault", "budding_amethyst", "command_block", "chain_command_block",
            "repeating_command_block", "structure_block", "jigsaw", "test_block", "test_instance_block",
            "petrified_oak_slab");

    /** Soil a player cannot pick up as it is, paid for with dirt. */
    private static final Set<String> SOIL = Set.of("grass_block", "mycelium", "podzol", "farmland", "dirt_path");

    private static final Map<String, String> EMPTIED = Map.of("water_cauldron", "cauldron", "lava_cauldron", "cauldron",
            "powder_snow_cauldron", "cauldron");

    /** A state's properties set back to what costs nothing more than the item. */
    private static final Map<String, String> EMPTY_PROPERTIES = Map.of("age", "0", "honey_level", "0",
            "berries", "false", "waterlogged", "false", "extended", "false", "charges", "0");

    /** Properties that say how many of the item one block is. */
    private static final Set<String> COUNTED = Set.of("pickles", "candles", "eggs", "layers", "flower_amount",
            "segment_amount");

    private static final Map<String, String> POTTED = Map.of("azalea_bush", "azalea",
            "flowering_azalea_bush", "flowering_azalea");

    /** A box marked out, its corner and its size. */
    public record Box(String world, int x, int y, int z, int width, int height, int length) {

        public static Box of(Spot a, Spot b) {
            int x = Math.min(a.x(), b.x());
            int y = Math.min(a.y(), b.y());
            int z = Math.min(a.z(), b.z());
            return new Box(a.world(), x, y, z, Math.abs(a.x() - b.x()) + 1, Math.abs(a.y() - b.y()) + 1,
                    Math.abs(a.z() - b.z()) + 1);
        }

        public int longest() {
            return Math.max(width, Math.max(height, length));
        }

        public long volume() {
            return (long) width * height * length;
        }

        /** From a block position to the nearest block of the box; 0 inside it. */
        public double distanceTo(int px, int py, int pz) {
            double dx = Math.max(0, Math.max(x - px, px - (x + width - 1)));
            double dy = Math.max(0, Math.max(y - py, py - (y + height - 1)));
            double dz = Math.max(0, Math.max(z - pz, pz - (z + length - 1)));
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** One block of a paste: the state put down, and the items it costs (none for the second half of a door). */
    public record Piece(String state, Map<String, Integer> items) {

        public Piece {
            items = Map.copyOf(items);
        }
    }

    public boolean nameOk(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /**
     * @param edge      the longest side the player's ranks allow, 0 without Draftsman
     * @param replacing a blueprint of that name is saved already, so no new room is needed
     * @param away      how far the player stands from the box
     */
    public Optional<Refusal> maySave(Box box, String name, int edge, int saved, int slots, boolean replacing,
                                     double away) {
        if (edge <= 0 || slots <= 0) {
            return Optional.of(Refusal.NO_DRAFTSMAN);
        }
        if (!nameOk(name)) {
            return Optional.of(Refusal.BAD_NAME);
        }
        if (box.longest() > edge) {
            return Optional.of(Refusal.TOO_BIG);
        }
        if (!replacing && saved >= slots) {
            return Optional.of(Refusal.NO_ROOM);
        }
        if (away > MOST_AWAY) {
            return Optional.of(Refusal.TOO_FAR);
        }
        return Optional.empty();
    }

    /** A blueprint saved with larger ranks than are held now waits for them to come back. */
    public Optional<Refusal> mayPaste(int width, int height, int length, int edge) {
        if (edge <= 0) {
            return Optional.of(Refusal.NO_DRAFTSMAN);
        }
        return Math.max(width, Math.max(height, length)) > edge ? Optional.of(Refusal.TOO_BIG) : Optional.empty();
    }

    /**
     * What a saved block state is put down as, and what it costs; empty for what is never placed.
     *
     * @param itemOf the item a block is placed from, by its namespaced id ({@code minecraft:wall_torch} → torch)
     */
    public Optional<Piece> piece(String state, Function<String, Optional<String>> itemOf) {
        int bracket = state.indexOf('[');
        String id = bracket < 0 ? state : state.substring(0, bracket);
        String name = id.substring(id.indexOf(':') + 1);
        String namespace = id.indexOf(':') < 0 ? "minecraft" : id.substring(0, id.indexOf(':'));
        if (!namespace.equals("minecraft") || NEVER.contains(name) || name.startsWith("infested_")) {
            return Optional.empty();
        }
        String emptied = EMPTIED.get(name);
        if (emptied != null) {
            return itemOf.apply("minecraft:" + emptied).map(item -> new Piece("minecraft:" + emptied, Map.of(item, 1)));
        }
        Map<String, String> properties = properties(bracket < 0 ? "" : state.substring(bracket + 1, state.length() - 1));
        properties.replaceAll((key, value) -> name.equals("composter") && key.equals("level") ? "0"
                : EMPTY_PROPERTIES.getOrDefault(key, value));
        String placed = properties.isEmpty() ? id : id + "[" + join(properties) + "]";
        if ("upper".equals(properties.get("half")) || "head".equals(properties.get("part"))) {
            return Optional.of(new Piece(placed, Map.of()));
        }
        if (name.startsWith("potted_")) {
            String plant = name.substring("potted_".length());
            plant = POTTED.getOrDefault(plant, plant);
            Optional<String> pot = itemOf.apply("minecraft:flower_pot");
            Optional<String> grown = itemOf.apply("minecraft:" + plant);
            if (pot.isEmpty() || grown.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Piece(placed, Map.of(pot.get(), 1, grown.get(), 1)));
        }
        Optional<String> item = itemOf.apply(SOIL.contains(name) ? "minecraft:dirt" : id);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        int count = 1;
        for (String key : COUNTED) {
            if (properties.containsKey(key)) {
                count = Math.max(1, parse(properties.get(key)));
            }
        }
        if (name.endsWith("_slab") && "double".equals(properties.get("type"))) {
            count = 2;
        }
        return Optional.of(new Piece(placed, Map.of(item.get(), count)));
    }

    private static int parse(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }

    private static Map<String, String> properties(String inside) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (inside.isEmpty()) {
            return properties;
        }
        for (String pair : inside.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        return properties;
    }

    private static String join(Map<String, String> properties) {
        StringBuilder out = new StringBuilder();
        properties.forEach((key, value) -> out.append(out.isEmpty() ? "" : ",").append(key).append('=').append(value));
        return out.toString();
    }

    /** Every item the pieces cost, added up. */
    public Map<String, Integer> needed(List<Piece> pieces) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (Piece piece : pieces) {
            piece.items().forEach((item, count) -> needed.merge(item, count, Integer::sum));
        }
        return needed;
    }

    /** What of {@code needed} is not in {@code have}, and how many short. */
    public Map<String, Integer> missing(Map<String, Integer> needed, Map<String, Integer> have) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        needed.forEach((item, count) -> {
            int lacking = count - have.getOrDefault(item, 0);
            if (lacking > 0) {
                missing.put(item, lacking);
            }
        });
        return missing;
    }

    /** {@code (x, z)} turned clockwise {@code quarters} times about the origin. */
    public static int[] turn(int x, int z, int quarters) {
        return switch (Math.floorMod(quarters, 4)) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    /** 90, 180 or 270 degrees as quarter turns. */
    public static Optional<Integer> quarters(int degrees) {
        return switch (degrees) {
            case 0, 360 -> Optional.of(0);
            case 90 -> Optional.of(1);
            case 180 -> Optional.of(2);
            case 270 -> Optional.of(3);
            default -> Optional.empty();
        };
    }

    @Override
    public String describe() {
        return "what a blueprint may be, and what every block of a paste costs";
    }
}
