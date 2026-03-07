/*
 * Custom changes:
 * V's Custom Attributes injected into the Defensive Unsafe Clone method.
 * Non-destructive attribute override: clones AccountAttribute objects instead of mutating in-place
 * V's MASTER DUAL-FORENSICS DUMPER: Harvests both the Server's Reality and V's Shadow Map.
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
import java.util.HashMap;

import app.revanced.extension.shared.Logger;
import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("unused")
public final class UnlockPremiumPatch {

    // V'S DUAL MEMORY BANKS
    private static final Map<String, String> SEEN_ORIGINAL_STATES = new HashMap<>();
    private static final Map<String, String> SEEN_SHADOW_STATES = new HashMap<>();
    private static final Object FILE_LOCK = new Object();

    private static class OverrideAttribute {
        final String key;
        final Object overrideValue;
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
            // --- CORE FUNCTIONALITY ---
            new OverrideAttribute("player-license", "on-demand"),
            new OverrideAttribute("shuffle", FALSE),
            new OverrideAttribute("on-demand", TRUE),
            new OverrideAttribute("streaming", TRUE),
            new OverrideAttribute("pick-and-shuffle", FALSE),
            new OverrideAttribute("streaming-rules", ""),
            new OverrideAttribute("nft-disabled", "1"),

            // 🎯 --- V'S AGGRESSIVE NEW HITLIST --- 🎯
            new OverrideAttribute("smart-shuffle", "AVAILABLE", false),
            new OverrideAttribute("ad-formats-preroll-video", FALSE, false),
            // new OverrideAttribute("estimated-age", 24, false),
            new OverrideAttribute("has-audiobooks-subscription", TRUE, false),
            new OverrideAttribute("social-session-free-tier", FALSE, false),
            new OverrideAttribute("jam-social-session", "PREMIUM", false),
            //new OverrideAttribute("can-block-content", 0, false),
            new OverrideAttribute("parrot", "enabled", false),
            // new OverrideAttribute("is_email_verified", TRUE, false),
            //new OverrideAttribute("offline", TRUE, false),
            // new OverrideAttribute("app-developer", 1, false),
            new OverrideAttribute("on-demand-trial-in-progress", TRUE, false),
            new OverrideAttribute("ugc-abuse-report", FALSE, false),
            new OverrideAttribute("offline-backup", "ENABLED", false),
            new OverrideAttribute("lyrics-offline", TRUE, false),

            // 🚀 --- THE PERFORMANCE & UI OVERRIDES --- 🚀
            new OverrideAttribute("is-tuna", TRUE, false),
            new OverrideAttribute("is-seadragon", TRUE, false),
            //new OverrideAttribute("prefetch-keys", "10", false), // Maxed out from 1
            //new OverrideAttribute("prefetch-window-max", 20, false), // Maxed out from 2

            // 💀 --- THE ANTI-TAMPER SPOOF --- 💀
            // new OverrideAttribute("at-signal", "0,deadbeef00", false), // Force-feeding them a dead hash

            // --- V'S CUSTOM DEEP CORE ATTRIBUTES ---
            new OverrideAttribute("audio-quality", "2", false),
            new OverrideAttribute("social-session", TRUE, false),
            new OverrideAttribute("obfuscate-restricted-tracks", FALSE, false),
            new OverrideAttribute("dj-accessible", TRUE, false),
            new OverrideAttribute("enable-dj", TRUE, false),
            new OverrideAttribute("ai-playlists", TRUE, false),
            new OverrideAttribute("can_use_superbird", TRUE, false)
    );

    private static final List<Integer> REMOVED_HOME_SECTIONS = List.of(
            com.spotify.home.evopage.homeapi.proto.Section.VIDEO_BRAND_AD_FIELD_NUMBER,
            com.spotify.home.evopage.homeapi.proto.Section.IMAGE_BRAND_AD_FIELD_NUMBER
    );

    private static final List<Integer> REMOVED_BROWSE_SECTIONS = List.of(
            com.spotify.browsita.v1.resolved.Section.BRAND_ADS_FIELD_NUMBER
    );

    /**
     * V'S DUAL FORENSIC DUMPER
     * Takes a label to distinguish between the Server's map and our Shadow map.
     */
    private static void dumpForensics(String mapLabel, Map<String, ?> map, Map<String, String> memoryBank) {
        try {
            android.content.Context ctx = app.revanced.extension.shared.Utils.getContext();
            if (ctx == null) return;

            java.io.File file = new java.io.File(ctx.getExternalFilesDir(null), "v_config_forensics.txt");
            boolean changesFound = false;
            StringBuilder logBuilder = new StringBuilder();

            synchronized (FILE_LOCK) {
                if (!file.exists()) {
                    logBuilder.append("=== V'S DUAL CONFIG FORENSICS DUMP ===\n\n");
                    changesFound = true;
                }

                for (Map.Entry<String, ?> entry : map.entrySet()) {
                    String key = entry.getKey();
                    Object protoWrapper = entry.getValue();
                    if (protoWrapper == null) continue;

                    try {
                        Object realValue = XposedHelpers.getObjectField(protoWrapper, "value_");
                        String valueStr = (realValue != null) ? realValue.toString() : "NULL";

                        String acceptedType = "Unknown";
                        if (realValue != null) {
                            if (realValue instanceof Boolean) acceptedType = "Boolean";
                            else if (realValue instanceof String) acceptedType = "String";
                            else if (realValue instanceof Integer || realValue instanceof Long) acceptedType = "Numeric";
                            else acceptedType = realValue.getClass().getSimpleName();
                        }

                        String previousValue = memoryBank.get(key);

                        if (previousValue == null || !previousValue.equals(valueStr)) {
                            memoryBank.put(key, valueStr);
                            changesFound = true;

                            if (previousValue == null) {
                                logBuilder.append("[").append(mapLabel).append("] [NEW] '").append(key)
                                        .append("' | Current: [").append(valueStr)
                                        .append("] | Accepts: [").append(acceptedType).append("]\n");
                            } else {
                                logBuilder.append("[").append(mapLabel).append("] [CHANGED] '").append(key)
                                        .append("' | Old: [").append(previousValue)
                                        .append("] -> New: [").append(valueStr)
                                        .append("]\n");
                            }
                        }
                    } catch (Exception e) {
                        String rawStr = protoWrapper.toString();
                        if (!memoryBank.containsKey(key)) {
                            memoryBank.put(key, rawStr);
                            changesFound = true;
                            logBuilder.append("[").append(mapLabel).append("] [RAW] '").append(key)
                                    .append("' | Value: [").append(rawStr).append("]\n");
                        }
                    }
                }

                if (changesFound) {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(file, true);
                    fos.write(logBuilder.toString().getBytes("UTF-8"));
                    fos.close();
                }
            }
        } catch (Exception e) {}
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ?> createOverriddenAttributesMap(Map<String, ?> originalMap) {
        // 🚨 DUMP 1: THE REALITY (What the server sent) 🚨
        if (originalMap != null && !originalMap.isEmpty()) {
            dumpForensics("SERVER", originalMap, SEEN_ORIGINAL_STATES);
        }

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

                Object clonedAttribute = shallowCloneObject(attribute);
                XposedHelpers.setObjectField(clonedAttribute, "value_", override.overrideValue);
                result.put(override.key, clonedAttribute);
            }

            // 🚨 DUMP 2: THE ILLUSION (What we are feeding the app) 🚨
            dumpForensics("SHADOW", result, SEEN_SHADOW_STATES);

            // THE SELF-WRITING JAVA HONEYPOT MAP
            return new java.util.LinkedHashMap<String, Object>(result) {
                @Override
                public Object get(Object key) {
                    if ("ads".equals(key)) {
                        try {
                            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
                            StringBuilder sb = new StringBuilder("\n\n🍯 [JAVA HONEYPOT] 'ads' WAS QUERIED!\n🔎 --- JADX DOSSIER ---\n");
                            int count = 0;

                            for (StackTraceElement element : trace) {
                                String cName = element.getClassName();
                                if (cName.contains("Xposed") || cName.contains("UnlockPremiumPatch") || cName.startsWith("java.") || cName.startsWith("android.")) {
                                    continue;
                                }

                                if (count == 0) {
                                    sb.append("🎯 CALLER: ").append(cName).append(".").append(element.getMethodName())
                                            .append(" (Line: ").append(element.getLineNumber()).append(")\n");
                                } else {
                                    sb.append("   -> ").append(cName).append(".").append(element.getMethodName())
                                            .append(" (Line: ").append(element.getLineNumber()).append(")\n");
                                }
                                count++;
                                if (count >= 8) break;
                            }
                            sb.append("--------------------------\n");

                            android.content.Context ctx = app.revanced.extension.shared.Utils.getContext();
                            if (ctx != null) {
                                java.io.File file = new java.io.File(ctx.getExternalFilesDir(null), "v_java_honeypot.txt");
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(file, true);
                                fos.write(sb.toString().getBytes("UTF-8"));
                                fos.close();
                            }
                        } catch (Exception e) {}
                    }
                    return super.get(key);
                }
            };
        } catch (Exception ex) {
            Logger.printException(() -> "createOverriddenAttributesMap failure", ex);
            return originalMap;
        }
    }

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

    public static String removeStationString(String spotifyUriOrUrl) {
        try {
            return spotifyUriOrUrl.replace("spotify:station:", "spotify:");
        } catch (Exception ex) {
            return spotifyUriOrUrl;
        }
    }

    private interface FeatureTypeIdProvider<T> {
        int getFeatureTypeId(T section);
    }

    private static <T> void removeSections(
            List<T> sections,
            FeatureTypeIdProvider<T> featureTypeExtractor,
            List<Integer> idsToRemove
    ) {
        try {
            java.util.Iterator<T> iterator = sections.iterator();

            while (iterator.hasNext()) {
                T section = iterator.next();
                int featureTypeId = featureTypeExtractor.getFeatureTypeId(section);
                if (idsToRemove.contains(featureTypeId)) {
                    iterator.remove();
                }
            }
        } catch (Exception ex) {}
    }

    public static void removeHomeSections(List<?> sections) {
        removeSections(
                sections,
                section -> XposedHelpers.getIntField(section, "featureTypeCase_"),
                REMOVED_HOME_SECTIONS
        );
    }

    public static void removeBrowseSections(List<?> sections) {
        removeSections(
                sections,
                section -> XposedHelpers.getIntField(section, "sectionTypeCase_"),
                REMOVED_BROWSE_SECTIONS
        );
    }
}
