package io.github.ondreamqwq.drc;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The behaviour switches for the panel remote, written to
 * {@code config/dashpanels_remote_control-common.toml}.
 */
public final class DrcConfig {

    public static final ModConfigSpec SPEC;

    /**
     * How far the panel remote's camera leans off the panel's working face, in degrees.
     *
     * <p>Positive values look down at the panel, negative values look up at it. Zero is a straight
     * face-on view.
     */
    public static final ModConfigSpec.DoubleValue CAMERA_TILT;

    /**
     * Whether the remote projects the panels in perspective instead of orthographically.
     *
     * <p>Orthographic is the default: every panel stays the same size no matter how deep it sits,
     * which makes a wall of panels easy to read. Perspective looks more like standing in front of
     * it, at the cost of the panels further back shrinking.
     */
    public static final ModConfigSpec.BooleanValue PERSPECTIVE;

    /**
     * Whether the remote draws its own crosshair.
     *
     * <p>The game's crosshair is cancelled while the remote is open (it would end up behind the
     * panels), so the screen paints its own where a module's "hide the crosshair" flag can be
     * honoured. Turn this off for a completely clean view.
     */
    public static final ModConfigSpec.BooleanValue SHOW_CROSSHAIR;

    /** How far the assembly turns per pixel dragged in the panel remote. */
    public static final ModConfigSpec.DoubleValue ROTATE_SENSITIVITY;

    /**
     * Whether the panel remote draws the little name/reading box in its top left corner.
     *
     * <p>On by default because it is the only place that spells out which module is being driven
     * and what it is set to; off for players who would rather see the panels alone.
     */
    public static final ModConfigSpec.BooleanValue INFO_BOX;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        CAMERA_TILT = builder
                .comment("Degrees the panel remote's camera leans off the panel face.",
                        "Positive looks down at the panel, negative looks up at it, 0 is face on.")
                .defineInRange("cameraTilt", 45.0D, -89.0D, 89.0D);

        PERSPECTIVE = builder
                .comment("Project the panels in perspective instead of orthographically.",
                        "Off keeps every panel the same size; on makes the ones further back smaller.")
                .define("perspective", false);

        SHOW_CROSSHAIR = builder
                .comment("Draw the crosshair in the panel remote.",
                        "Turn this off if you would rather have the screen clean.")
                .define("showCrosshair", true);

        ROTATE_SENSITIVITY = builder
                .comment("How far the assembly turns per pixel dragged in the panel remote.",
                        "0.55 is the default; lower it if the view feels twitchy, raise it to spin it faster.")
                .defineInRange("rotateSensitivity", 0.55D, 0.05D, 3.0D);

        INFO_BOX = builder
                .comment("Draw the name and reading box in the panel remote's top left corner.",
                        "Turn this off if you would rather look at the panels without anything over them.")
                .define("infoBox", true);

        SPEC = builder.build();
    }

    private DrcConfig() {
    }

    public static float cameraTilt() {
        return CAMERA_TILT.get().floatValue();
    }

    public static boolean showCrosshair() {
        return SHOW_CROSSHAIR.get();
    }

    public static float rotateSensitivity() {
        return ROTATE_SENSITIVITY.get().floatValue();
    }

    public static boolean infoBox() {
        return INFO_BOX.get();
    }

    public static boolean perspective() {
        return PERSPECTIVE.get();
    }
}