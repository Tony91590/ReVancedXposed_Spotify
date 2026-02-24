/*
 * Custom changes:
 * Wipe stubbed types: REMOVED_HOME_SECTIONS, createOverriddenAttributesMap, removeHomeSections
 * Non-destructive attribute override: clones AccountAttribute objects instead of mutating in-place
 * */
package app.revanced.extension.spotify.misc;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import app.revanced.extension.shared.Logger;
import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("unused")
public final class UnlockPremiumPatch {

    private static class OverrideAttribute {
        /**
         * Account attribute key.
         */
        final String key;

        /**
         * Override value.
         */
        final Object overrideValue;

        /**
         * If this attribute is expected to be present in all situations.
         * If false, then no error is raised if the attribute is missing.
         */
        final boolean isExpected;

        OverrideAttribute(String key, Object overrideValue) {
            this(key, overrideValue, true);
        }

        OverrideAttribute(String key, Object overrideValue, boolean isExpected) {
            this.key = Objects.requireNonNull(key);
            this.overrideValue = Objects.requireNonNull(overrideValue);
            this.isExpected = isExpected;
        }
    }

    private static final List<OverrideAttribute> PREMIUM_OVERRIDES = List.of(
            // Disables player and app ads.
            new OverrideAttribute("ads", FALSE),
            // Works along on-demand, allows playing any song without restriction.
            new OverrideAttribute("player-license", "on-demand"),
            // Disables shuffle being initially enabled when first playing a playlist.
            new OverrideAttribute("shuffle", FALSE),
            // Allows playing any song on-demand, without a shuffled order.
            new OverrideAttribute("on-demand", TRUE),
            // Make sure playing songs is not disabled remotely and playlists show up.
            new OverrideAttribute("streaming", TRUE),
            // Allows adding songs to queue and removes the smart shuffle mode restriction,
            // allowing to pick any of the other modes. Flag is not present in legacy app target.
            new OverrideAttribute("pick-and-shuffle", FALSE),
            // Disables shuffle-mode streaming-rule, which forces songs to be played shuffled
            // and breaks the player when other patches are applied.
            new OverrideAttribute("streaming-rules", ""),
            // Enables premium UI in settings and removes the premium button in the nav-bar.
            new OverrideAttribute("nft-disabled", "1"),
            // Enable Spotify Car Thing hardware device.
            // Device is discontinued and no longer works with the latest releases,
            // but it might still work with older app targets.
            new OverrideAttribute("can_use_superbird", TRUE, false),
            // Removes the premium button in the nav-bar for tablet users.
            new OverrideAttribute("tablet-free", FALSE, false)
    );

    /**
     * A list of home sections feature types ids which should be removed. These ids match the ones from the protobuf
     * response which delivers home sections.
     */
    private static final List<Integer> REMOVED_HOME_SECTIONS = List.of(
            com.spotify.home.evopage.homeapi.proto.Section.VIDEO_BRAND_AD_FIELD_NUMBER,
            com.spotify.home.evopage.homeapi.proto.Section.IMAGE_BRAND_AD_FIELD_NUMBER
    );

    /**
     * A list of browse sections feature types ids which should be removed. These ids match the ones from the protobuf
     * response which delivers browse sections.
     */
    private static final List<Integer> REMOVED_BROWSE_SECTIONS = List.of(
            com.spotify.browsita.v1.resolved.Section.BRAND_ADS_FIELD_NUMBER
    );

    /**
     * Injection point. Creates a non-destructive copy of the attributes map with overridden values.
     * Original AccountAttribute objects are NOT modified, preventing server-side detection
     * through serialization of tampered protobuf data back to Spotify servers.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, ?> createOverriddenAttributesMap(Map<String, ?> originalMap) {
        try {
            Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) originalMap);

            for (OverrideAttribute override : PREMIUM_OVERRIDES) {
                Object attribute = result.get(override.key);

                if (attribute == null) {
                    if (override.isExpected) {
                        Logger.printException(() -> "Attribute " + override.key + " expected but not found");
                    }
                    continue;
                }

                Object originalValue = XposedHelpers.getObjectField(attribute, "value_");

                if (override.overrideValue.equals(originalValue)) {
                    continue;
                }

                Logger.printInfo(() -> "Overriding account attribute " + override.key +
                        " from " + originalValue + " to " + override.overrideValue);

                // Clone the attribute object so the original protobuf data stays untouched
                // for server serialization, preventing server-side state mismatch detection.
                Object clonedAttribute = shallowCloneObject(attribute);
                XposedHelpers.setObjectField(clonedAttribute, "value_", override.overrideValue);
                result.put(override.key, clonedAttribute);
            }

            return result;
        } catch (Exception ex) {
            Logger.printException(() -> "createOverriddenAttributesMap failure", ex);
            return originalMap;
        }
    }

    /**
     * Creates a shallow clone of an object by allocating a new instance via Unsafe
     * (without calling any constructor) and copying all instance fields from the hierarchy.
     * Unsafe is accessed entirely via reflection to avoid compile-time dependency on sun.misc.
     */
    private static volatile Object unsafeInstance;
    private static volatile java.lang.reflect.Method allocateInstanceMethod;

    private static Object shallowCloneObject(Object original) {
        try {
            if (unsafeInstance == null) {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                unsafeInstance = unsafeField.get(null);
                allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class.class);
            }

            Class<?> clazz = original.getClass();
            Object clone = allocateInstanceMethod.invoke(unsafeInstance, clazz);

            // Copy all instance fields including those from superclasses.
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    f.set(clone, f.get(original));
                }
                current = current.getSuperclass();
            }

            return clone;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone " + original.getClass().getName(), e);
        }
    }

    /**
     * Injection point. Remove station data from Google Assistant URI.
     */
    public static String removeStationString(String spotifyUriOrUrl) {
        try {
            Logger.printInfo(() -> "Removing station string from " + spotifyUriOrUrl);
            return spotifyUriOrUrl.replace("spotify:station:", "spotify:");
        } catch (Exception ex) {
            Logger.printException(() -> "removeStationString failure", ex);
            return spotifyUriOrUrl;
        }
    }

    private interface FeatureTypeIdProvider<T> {
        int getFeatureTypeId(T section);
    }

    /**
     * Returns a new filtered list with ad sections removed.
     * Does NOT mutate the original protobuf list, preventing detection through
     * protobuf integrity checks or server-side serialization of the modified structure.
     */
    private static <T> List<T> filterSections(
            List<T> sections,
            FeatureTypeIdProvider<T> featureTypeExtractor,
            List<Integer> idsToRemove
    ) {
        try {
            List<T> filtered = new java.util.ArrayList<>(sections.size());
            for (T section : sections) {
                int featureTypeId = featureTypeExtractor.getFeatureTypeId(section);
                if (idsToRemove.contains(featureTypeId)) {
                    Logger.printInfo(() -> "Filtering section with feature type id " + featureTypeId);
                } else {
                    filtered.add(section);
                }
            }
            return filtered;
        } catch (Exception ex) {
            Logger.printException(() -> "filterSections failure", ex);
            return sections;
        }
    }

    /**
     * Injection point. Returns a new list with ads sections filtered from home.
     * Original protobuf list is not modified.
     */
    public static List<?> filterHomeSections(List<?> sections) {
        Logger.printInfo(() -> "Filtering ads sections from home");
        return filterSections(
                sections,
                section -> XposedHelpers.getIntField(section, "featureTypeCase_"),
                REMOVED_HOME_SECTIONS
        );
    }

    /**
     * Injection point. Returns a new list with ads sections filtered from browse.
     * Original protobuf list is not modified.
     */
    public static List<?> filterBrowseSections(List<?> sections) {
        Logger.printInfo(() -> "Filtering ads sections from browse");
        return filterSections(
                sections,
                section -> XposedHelpers.getIntField(section, "sectionTypeCase_"),
                REMOVED_BROWSE_SECTIONS
        );
    }
}
