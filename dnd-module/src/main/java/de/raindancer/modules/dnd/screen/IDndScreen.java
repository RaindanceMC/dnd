package de.raindancer.modules.dnd.screen;

/** A screen belonging to this module. {@code describe()} is what a diagnostic names. */
public interface IDndScreen {

    void open();

    default String describe() {
        String name = getClass().getSimpleName();
        return name.isEmpty() ? getClass().getName() : name;
    }
}
