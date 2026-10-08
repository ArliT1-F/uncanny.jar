package dev.uncanny.events.impl;

import dev.uncanny.events.UncannyEventManager;

/**
 * The list of anomalies.
 *
 * One line each. To add an anomaly, write the class and add a line here - the
 * director finds it, times it, cools it down and books it, with no other wiring.
 *
 * The order matters for nothing at runtime; it is ordered by how loud each one is,
 * which makes this file a decent summary of the mod's pacing.
 */
public final class AllEvents {

    private AllEvents() {
    }

    public static void register() {
        // ---- QUIET: the player will probably not notice these ----
        UncannyEventManager.register(new MissingTreeEvent());
        UncannyEventManager.register(new ExtraTreeEvent());
        UncannyEventManager.register(new DoorStateEvent());
        UncannyEventManager.register(new MissingLightEvent());
        UncannyEventManager.register(new DistantFootstepEvent());
        UncannyEventManager.register(new SilenceEvent());

        // ---- NOTICED: the player notices and explains it away ----
        UncannyEventManager.register(new WrongSignEvent());
        UncannyEventManager.register(new ChestMovedEvent());
        UncannyEventManager.register(new DistantKnockEvent());
        UncannyEventManager.register(new SkyGlitchEvent());
        UncannyEventManager.register(new SealPlateEvent());
        UncannyEventManager.register(new ArchiveCallEvent());

        // ---- MAJOR: once each, and the player cannot unsee them ----
        UncannyEventManager.register(new WrongGraveEvent());
        UncannyEventManager.register(new GraveGoneEvent());
        UncannyEventManager.register(new OtherHouseEvent());
        UncannyEventManager.register(new RoomThatRemembersEvent());
        UncannyEventManager.register(new FinalDoorEvent());
    }
}
