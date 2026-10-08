package dev.uncanny.generation;

/**
 * The code that actually puts blocks down for one room template.
 *
 * A builder never decides WHETHER it runs - the generator decides that from the
 * seed. A builder only decides what the room looks like. That split is why new
 * rooms can be added without touching the generator.
 */
@FunctionalInterface
public interface TemplateBuilder {

    /** Writes this template into the chunk described by the context. */
    void build(GenerationContext context);
}
