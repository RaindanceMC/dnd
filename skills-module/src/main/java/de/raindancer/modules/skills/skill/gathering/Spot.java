package de.raindancer.modules.skills.skill.gathering;

/** A block's place, without its world: what {@link ConnectedRule} walks over. */
public record Spot(int x, int y, int z) {

    public Spot plus(int dx, int dy, int dz) {
        return new Spot(x + dx, y + dy, z + dz);
    }
}
