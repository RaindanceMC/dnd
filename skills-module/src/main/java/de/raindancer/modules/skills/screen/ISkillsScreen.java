package de.raindancer.modules.skills.screen;

/** A screen belonging to this module. {@code describe()} is what a diagnostic names. */
public interface ISkillsScreen {

    void open();

    default String describe() {
        String name = getClass().getSimpleName();
        return name.isEmpty() ? getClass().getName() : name;
    }
}
