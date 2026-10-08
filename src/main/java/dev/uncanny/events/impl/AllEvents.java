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

        // ---- FALSE NORMALITY: almost correct, which is worse ----
        UncannyEventManager.register(new ExtraStepEvent());
        UncannyEventManager.register(new WrongWoodTreeEvent());
        UncannyEventManager.register(new WrongPathBlockEvent());
        UncannyEventManager.register(new ChestThatWasntThereEvent());
        UncannyEventManager.register(new MissingBlockEvent());

        // ---- DIMENSION ATMOSPHERE: one behavioural rule per layer ----
        UncannyEventManager.register(DimensionAtmosphereEvent.hallShift());
        UncannyEventManager.register(DimensionAtmosphereEvent.woodsArrangement());
        UncannyEventManager.register(DimensionAtmosphereEvent.copyDuplicate());
        UncannyEventManager.register(DimensionAtmosphereEvent.houseWall());
        UncannyEventManager.register(DimensionAtmosphereEvent.archiveNote());
        UncannyEventManager.register(DimensionAtmosphereEvent.deepScale());
        UncannyEventManager.register(DimensionAtmosphereEvent.belowFragment());
        UncannyEventManager.register(DimensionAtmosphereEvent.overworldBleed());
        UncannyEventManager.register(DimensionAtmosphereEvent.partitionFragment());

        // ---- NEAR MISS: attempts that aborted. Uncommon, by design ----
        UncannyEventManager.register(new AbruptSoundEvent());
        UncannyEventManager.register(new DoorAlmostEvent());
        UncannyEventManager.register(new StructureFlickerEvent());
        UncannyEventManager.register(new SelectedTargetEvent());

        // ---- REALITY ECHO: this player's own actions, coming back ----
        UncannyEventManager.register(new RestoredBlockEvent());
        UncannyEventManager.register(new CopiedStructureEvent());
        UncannyEventManager.register(new EchoDoorEvent());

        // ---- NOTICED: the player notices and explains it away ----
        UncannyEventManager.register(new WrongSignEvent());
        UncannyEventManager.register(new ChestMovedEvent());
        UncannyEventManager.register(new DistantKnockEvent());
        UncannyEventManager.register(new SkyGlitchEvent());
        UncannyEventManager.register(new SealPlateEvent());
        UncannyEventManager.register(new ArchiveCallEvent());

        // ---- CHAINS: conditional progression, never guaranteed ----
        UncannyEventManager.register(new WrongTreeEvent());
        UncannyEventManager.register(new WoodsConvergenceEvent());

        // ---- IDENTITY: HIGH instability only ----
        UncannyEventManager.register(new WrongIdentitySignEvent());

        // ---- MAJOR: once each, and the player cannot unsee them ----
        UncannyEventManager.register(new WrongGraveEvent());
        UncannyEventManager.register(new GraveGoneEvent());
        UncannyEventManager.register(new OtherHouseEvent());
        UncannyEventManager.register(new RoomThatRemembersEvent());
        UncannyEventManager.register(new FinalDoorEvent());
    }
}
