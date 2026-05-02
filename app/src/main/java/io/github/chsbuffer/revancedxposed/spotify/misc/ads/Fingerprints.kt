package io.github.chsbuffer.revancedxposed.spotify.misc.ads

import io.github.chsbuffer.revancedxposed.SkipTest
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.findMethodListDirect

// ─────────────────────────────────────────────────────────────────
// Fingerprints based on diagnostic scan of Spotify APK
// These target Spotify's ad delivery, deserialization, and playback.
// ─────────────────────────────────────────────────────────────────

/**
 * Deserializes the ad break context from player state JSON.
 * Class: com.spotify.player.model.PlayerState_Deserializer
 * Method: deserializeAdBreakContext(JsonParser, DeserializationContext) → AdBreakContext
 */
val adBreakDeserializerFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("com.spotify.player.model.PlayerState_Deserializer")
            name("deserializeAdBreakContext")
        }
    }.single()
}

/**
 * Processes an Ad protobuf object.
 * Found via string 'audioAd': p.lky0.p(com.spotify.ads.esperanto.proto.Ad) → p.e50
 */
@get:SkipTest
val adProcessorFingerprint = findMethodDirect {
    findMethod {
        matcher {
            addParamType("com.spotify.ads.esperanto.proto.Ad")
        }
    }.single()
}

/**
 * AdSlot event handlers — process ad slot events.
 * Found via string 'AdSlot': p.g4h0.accept(Object) and p.zhj.accept(Object)
 *
 * Won't use these directly since they're too generic (accept(Object) pattern).
 */

/**
 * Ad display event tracking classes.
 * com.spotify.adsdisplay.*, com.spotify.adshome.*, com.spotify.nowplayingmodes.adsmode.*
 */
@get:SkipTest
val embeddedAdEventFingerprint = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass("com.spotify.adsdisplay.embeddedad.events.proto.EmbeddedNPVAdEvent")
            name("dynamicMethod")
        }
    }.toList()
}

// ─────────────────────────────────────────────────────────────────
// Phase 2: Esperanto proto slot pipeline fingerprints
// These target the first-party Esperanto ad-slot system.
// Proto class names (com.spotify.ads.esperanto.proto.*) are NOT
// obfuscated and remain stable across Spotify versions.
// ─────────────────────────────────────────────────────────────────

/**
 * Core Slots service client — orchestrates slot lifecycle.
 * Uses string "spotify.ads.esperanto.proto.Slots" with ClientBase.callSingle/callStream.
 * Dispatches: SubSlot, CreateSlot, PrepareSlotRequest, TriggerSlotRequest,
 *             FetchSlot, ClearAvailableAds, ClearAllAds, PrepareNextContextSlot,
 *             PrepareNextTrackSlot.
 *
 * Smali ref: p.avn0 (v9.1.24) — all methods use p.mg0 (slot registry) as first param.
 * We find ALL methods in this class so we can hook its entire output.
 */
@get:SkipTest
val slotsServiceMethodsFingerprint = findMethodListDirect {
    findMethod {
        matcher {
            usingStrings("spotify.ads.esperanto.proto.Slots")
            addParamType("com.spotify.ads.esperanto.proto.CreateSlotRequest")
        }
    }.toList()
}

/**
 * GetAdsResponse parser — maps server ad inventory into local ad queues.
 * Uses string "com.spotify.ads.esperanto.proto.GetAdsResponse" in error message.
 * Smali ref: p.ye0 (v9.1.24) — has apply(Object) method.
 */
@get:SkipTest
val getAdsResponseParserFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingStrings("com.spotify.ads.esperanto.proto.GetAdsResponse")
            name("apply")
        }
    }.single()
}

/**
 * InStream ad subscription — receives ad objects tied to current playback.
 * Uses string "spotify.ads.esperanto.proto.InStream" in the class that has start()/stop().
 * Smali ref: p.goj0 (v9.1.24)
 */
@get:SkipTest
val inStreamSubscriptionFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingStrings("spotify.ads.esperanto.proto.InStream")
            name("start")
            paramCount(0)
        }
    }.single()
}

/**
 * Slot enablement settings — enables/disables individual ad slots.
 * The class calls spotify.ads.esperanto.proto.Settings for slot enablement updates.
 * Smali ref: p.lpl0 (v9.1.24) — uses UpdateSlotEnablement
 */
@get:SkipTest
val slotSettingsFingerprint = findMethodListDirect {
    findMethod {
        matcher {
            usingStrings("spotify.ads.esperanto.proto.Settings")
            addParamType("com.spotify.ads.esperanto.proto.UpdateSlotRequest")
        }
    }.toList()
}

/**
 * Ad orchestrator — coordinates slot actions (NOW, NEXT_CONTEXT, NEXT_TRACK, FETCH, CLEAR)
 * and retrieves ad inventory via GetAds.
 * Uses string "spotify.ads.esperanto.proto.Ads" for GetAds calls.
 * Smali ref: p.dh0 (v9.1.24)
 */
@get:SkipTest
val adsOrchestratorFingerprint = findMethodListDirect {
    findMethod {
        matcher {
            usingStrings("spotify.ads.esperanto.proto.Ads")
        }
    }.toList()
}
