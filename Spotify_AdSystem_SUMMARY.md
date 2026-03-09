# Spotify Ads Investigation Summary

## Scope

This document summarizes a static analysis pass over the decompiled Spotify Android APK located in this directory.

The goal was to answer a specific question:

What in this APK is responsible for ads appearing for free users?

This is based on decompiled resources and smali code only. There was no runtime instrumentation, no network capture, and no backend source. Where the APK gives strong evidence, this document states it directly. Where the APK only suggests behavior, this document marks it as an inference.

## Executive Summary

The APK does not contain one simple local switch like `if user_is_free => show_ads`.

Instead, Spotify appears to use a layered first-party ad system with these major parts:

1. Account tier and entitlement state determine whether the user is free or premium.
2. Remote config and eligibility services decide which ad experiences are enabled for the session.
3. A first-party Esperanto ad pipeline manages ad slots, requests inventory, prepares or triggers ads, and clears ads.
4. Player state carries ad-break information and in-stream ad state.
5. Dedicated UI surfaces render ads in several forms: fullscreen ads, now-playing ad mode, sponsored playlists, leave-behinds, app-open ads, inline video ads, and DSA / consent flows.
6. Separate telemetry, targeting, state, and request-header services feed the serving pipeline.

The best-supported interpretation from the code is:

Free users see ads because their session remains eligible for ad-supported flows, ad slots are enabled for that session, Spotify backend services populate those slots with ad inventory, and the app renders those ads through dedicated presentation surfaces.

Premium appears to suppress eligibility for these paths, but the final decision is not purely local. The client looks like an orchestration and rendering layer for a server-driven ad system.

## Methodology

The analysis covered:

- `AndroidManifest.xml`
- `res/layout/*`
- `res/values/strings.xml`
- `smali/`
- `smali_classes2/` through `smali_classes9/`
- selected files under `unknown/`

The strongest evidence came from:

- `smali_classes4/com/spotify/connectivity/sessionstate/SessionState.smali`
- `smali_classes2/p/mg0.smali`
- `smali_classes2/p/adh.smali`
- `smali_classes2/p/avn0.smali`
- `smali_classes2/p/dh0.smali`
- `smali_classes6/p/eqn0.smali`
- `smali_classes2/p/u90.smali`
- `smali_classes2/p/goj0.smali`
- `smali_classes6/p/edq.smali`
- `smali_classes2/com/spotify/adsdisplay/display/DisplayAdActivity.smali`
- `smali_classes6/com/spotify/nowplayingmodes/adsmode/data/AdsModeModel$Format.smali`
- `smali_classes6/com/spotify/nowplayingmodes/adsmode/data/AdsModeModel$Product.smali`

## High-Level Architecture

At a high level, the client-side ad flow looks like this:

`Session/account state`
-> `free-tier / premium routing`
-> `remote config and eligibility`
-> `slot registry and slot lifecycle`
-> `fetch / prepare / trigger ad inventory`
-> `player ad-break and in-stream state`
-> `render in one of several ad surfaces`
-> `track events / update ad state / apply targeting / request headers`

This is not a generic third-party ad SDK bolted onto the app. The APK contains extensive first-party Spotify packages:

- `com.spotify.ads`
- `com.spotify.adsdisplay`
- `com.spotify.adsinternal`
- `com.spotify.adshome`
- `com.spotify.ad.detection`

That strongly indicates Spotify owns the main ad execution stack in the app.

## Part 1: Account Tier And Eligibility

### 1.1 Central account tier state

`smali_classes4/com/spotify/connectivity/sessionstate/SessionState.smali` defines account product state, including `free` and `premium`, and exposes `getProductType()`.

This is the clearest local representation of user tier in the APK.

### 1.2 Premium entitlement helpers

`smali_classes7/p/pi90.smali` contains helper logic that interprets account metadata as premium only when the type is `premium` and an `employee-free-opt-in` value does not override it.

That is important because it shows premium status is derived from account metadata, not from a hardcoded local constant or permanent feature switch.

### 1.3 Track metadata distinguishes ad content from premium-only content

`smali_classes6/com/spotify/player/model/ContextTrack$Metadata.smali` exposes fields such as:

- `is_podcast_advertisement`
- `is_premium_only`

This means the player model itself distinguishes:

- content that is an advertisement
- content or features that require premium

That separation is consistent with a backend-controlled playback model rather than a single UI-level ad toggle.

### 1.4 Free-tier experience routing

Several configs indicate that free users are routed through a distinct product surface before individual ad surfaces are even considered:

- `smali_classes7/p/e13.smali` includes `enable_new_free_tier_experience`
- `smali_classes7/p/rt2.smali` includes `enable_free_on_demand_experiment`
- `smali_classes7/p/rt2.smali` includes `enable_route_to_pdp_free_on_demand_experiment`
- `smali_classes7/p/sz2.smali` includes `skip_next_feedback_enabled` under `android-libs-nowplaying-reinvent-free-mode`

This is a key architectural clue:

ads are not the only difference between free and premium. There is a broader free-tier product experience, and ads live inside that experience.

### 1.5 Example of a premium boolean consumer

`smali_classes7/p/pc90.smali` explicitly converts a product-type string into a boolean that becomes true only when the value equals `premium`.

`smali_classes7/p/qc90.smali` subscribes to that value and enables its behavior only when the user is not premium and another local override flag is not set.

This is not proof that `qc90` is the main ad gate, but it is strong supporting evidence that many features are gated by product type through observers, not through a single global ad check.

## Part 2: Remote Config And Eligibility Layers

The APK contains several ad-related namespaces that make it clear Spotify can alter behavior remotely.

### 2.1 App-open ads

`smali_classes7/p/jl2.smali` defines configuration under `android-ad-on-app-open`, including:

- `cached_ad_expiration_period_seconds`
- `cta_card_enabled`
- `eligibility_service_enabled`
- `page_enabled`
- `page_injection_during_startup_enabled`
- `skip_button_enabled`
- `skippable_ad_delay_ms`
- `testing_dismiss_ad_when_video_finishes`
- `testing_frequency_capping_enabled`
- `video_loading_timeout_ms`

`smali_classes2/p/lp0.smali` contains a registration point for an `AdOnAppOpenEligibilityService`.

`smali_classes6/p/u080.smali` constructs app-open ad handling objects for `ad-on-app-open` and tags them with the namespace `android-ad-on-app-open`.

This is strong evidence that app-open ads are not always-on. They are gated by both eligibility services and remote config.

### 2.2 General ads display behavior

`smali_classes7/p/ol2.smali` defines config under `android-adsdisplay-ads`, including:

- `enable_out_of_focus_when_in_picture_in_picture_mode`
- `sponsored_playlist_clear_preroll_slot_enabled`

That second flag is especially interesting because it directly links sponsored playlists to preroll slot behavior.

### 2.3 Now Playing ad mode and scroll widgets

`smali_classes7/p/sz2.smali` contains several ad-adjacent flags, including:

- `music_npv_leavebehinds_enabled`
- `sponsored_playlist_v2_scrollcard_enabled`
- `skip_next_feedback_enabled` under `android-libs-nowplaying-reinvent-free-mode`

The code also references `android-libs-nowplaying-ads-mode`.

This shows that the Now Playing ad experience is modular and remotely tunable.

### 2.4 Engineering implication

Any engineer working on ads in this app is not just changing rendering code. They also need to understand:

- account-tier routing
- eligibility services
- namespace-level remote config
- frequency capping and timing configs
- presentation toggles for specific surfaces

## Part 3: The First-Party Ad Slot Pipeline

This is the most important technical finding.

Spotify appears to use a first-party ad-slot system built around Esperanto proto services.

### 3.1 Slot registry

`smali_classes2/p/mg0.smali` defines a registry of named ad slots and the formats each slot supports.

Named slots discovered in the registry include:

- `preroll`
- `watchnow`
- `midroll-watchnow`
- `stream`
- `marquee`
- `mobile-screensaver`
- `lyrics-overlay`
- `sponsored-playlist`
- `active-play-limit`
- `repeat-play`
- `podcast-midroll-1`
- `embedded-npv`
- `now-playing-bar`
- `embedded-playlist`
- `home-above-the-fold`

This file is crucial because it shows the app does not think about "ads" as one undifferentiated event. It thinks in terms of named placements.

Each named placement can have its own:

- format constraints
- lifecycle
- fetch/trigger timing
- UI surface
- analytics

### 3.2 Per-slot owner and subscription

`smali_classes2/p/adh.smali` appears to be a per-slot owner/controller.

It combines slot registry information with the lower-level slot service client, opens a `SubSlot` stream, calls `CreateSlot`, and forwards slot state and slot events into the rest of the app.

This looks like the bridge between "a placement exists" and "that placement becomes live for this session".

### 3.3 Core slots service client

`smali_classes2/p/avn0.smali` is one of the core technical files in the whole ad system. It talks to `spotify.ads.esperanto.proto.Slots`.

The methods visible in this client include operations equivalent to:

- `SubSlot`
- `CreateSlot`
- `PrepareSlotRequest`
- `TriggerSlotRequest`
- `ClearSlotRequest`
- `PrepareNextContextSlot`
- `PrepareNextTrackSlot`
- `FetchSlot`
- `ClearAvailableAds`
- `ClearAllAds`

This is the clearest evidence that ad lifecycle is orchestrated around slots and server responses, not just local timers.

### 3.4 High-level slot orchestration

`smali_classes2/p/dh0.smali` and `smali_classes3/p/prk0.smali` appear to sit above the raw slot client.

They coordinate logical actions such as:

- `NOW`
- `NEXT_CONTEXT`
- `NEXT_TRACK`
- `FETCH`
- `CLEAR`
- `CLEAR_ALL`

They also wait for slot readiness and call `Ads/GetAds`, which appears to retrieve actual ad inventory for the slot.

### 3.5 Parsing and mapping proto responses

`smali_classes6/p/eqn0.smali` parses multiple responses from the ad proto services, including:

- `CreateSlotResponse`
- `PrepareSlotResponse`
- `TriggerSlotResponse`
- `ClearSlotResponse`
- `SubSlotResponse`

`smali_classes2/p/pky0.smali` maps `AdSlotEvent` values into local objects. Event kinds include:

- `AVAILABLE`
- `PLAY`
- `DISCARD`
- `UNRECOGNIZED`

`smali_classes7/p/rwm0.smali` consumes `GetAdsResponse`, pulls the current slot's `AdQueue`, and maps proto ads into internal models.

Together, these files show the lower-level data pipeline:

proto service response
-> internal mapped object
-> slot state / queue / event
-> playback or rendering logic

## Part 4: Inputs That Influence Ad Serving

The APK contains several separate channels that feed context into the ad system.

### 4.1 Targeting

`smali_classes2/p/p7r0.smali` builds and sends `PutTargetingRequest` to `spotify.ads.esperanto.proto.Targeting`.

This suggests the client uploads ad targeting context to backend services rather than only evaluating targeting locally.

### 4.2 Ad state

`smali_classes2/p/lnp0.smali` calls `GetState` on `spotify.ads.esperanto.proto.State`.

`smali_classes2/p/nop0.smali` builds and sends `PutStateRequest`.

`smali_classes7/p/sno0.smali` parses `AdStateValue` map entries into value-plus-timestamp objects.

This implies Spotify tracks ad-related state in a synchronized client/server state model.

### 4.3 Slot enablement settings

`smali_classes2/p/lpl0.smali` updates slot enablement through `spotify.ads.esperanto.proto.Settings`.

This is particularly important. It means slot behavior is not just "slot exists" versus "slot absent". Individual slots can be enabled or disabled through a settings channel.

### 4.4 Request headers and device context

`smali_classes2/p/ebr.smali` sets request headers through `SetRequestHeadersRequest`.

`smali_classes2/p/cbr.smali` contributes device or external-context state that appears relevant to serving.

That points to a richer context model than just account tier.

### 4.5 Capability adjustments

`smali_classes2/p/f69.smali`, `smali_classes2/p/a69.smali`, and `smali_classes2/p/b69.smali` reference capabilities such as:

- `AUDIO_ONLY`
- `DISTRACTED_DRIVER`

This suggests ad behavior can change based on device state or playback context. For example, some visual ad surfaces may be inappropriate in audio-only or driving-related conditions.

## Part 5: Live Playback Integration

The ad system is connected to active player state, not just to independent UI components.

### 5.1 In-stream ad subscription

`smali_classes2/p/goj0.smali` subscribes to `spotify.ads.esperanto.proto.InStream` via `SubInStream`.

That indicates the app can receive ad objects tied directly to current playback streams.

### 5.2 Ad-break state

`smali_classes6/p/qgi.smali` references `Break/SubBreakState`.

`smali_classes6/p/edq.smali` maps `EsContextPlayerState` into `PlayerState.adBreakContext(...)`.

`smali_classes2/p/any0.smali` reads helpers such as:

- `totalAdsInBreakEstimate()`
- `positionInCurrentAdBreak()`

These are very strong signals that ad playback is represented explicitly in the player state model.

### 5.3 Meaning of this design

The app is not merely "pausing music and opening an ad screen".

Instead, the player appears aware of:

- whether playback is inside an ad break
- how many ads are in the break
- current ad position within the break
- whether the current item is an ad object

This is consistent with a deep player integration rather than an external overlay.

## Part 6: Presentation Surfaces

Spotify has multiple first-class ad surfaces. This matters because responsibility for "ads showing up" is spread across several product areas.

### 6.1 Dedicated activities in the manifest

`AndroidManifest.xml` registers ad-specific activities, including:

- `com.spotify.adsdisplay.browser.inapp.InAppBrowserActivity`
- `com.spotify.adsdisplay.display.DisplayAdActivity`
- `com.spotify.adsdisplay.products.cmp.CMPActivity`
- `com.spotify.adsinternal.adscommon.inappbrowser.InAppBrowserLauncherActivity`

That alone proves ads are not an accidental side effect of generic browser or media code. They have dedicated application entry points.

### 6.2 Fullscreen display and video ads

`smali_classes2/com/spotify/adsdisplay/display/DisplayAdActivity.smali` is a core rendering surface.

It loads an `ad` parcelable, reads an ad type, and routes to overlays such as:

- mobile overlay
- lyrics overlay
- video overlay

Supporting layouts include:

- `res/layout/fragment_image_overlay.xml`
- `res/layout/fragment_video_overlay.xml`

These layouts contain typical ad UI elements:

- advertisement tag or header
- media surface
- CTA button
- advertiser and tagline text
- close or dismiss controls
- countdown or progress indicators

### 6.3 In-app browser for ad clickthroughs

`smali_classes2/com/spotify/adsdisplay/browser/inapp/InAppBrowserActivity.smali` provides the clickthrough surface for ad landing pages.

This is the bridge from ad impression to user interaction.

### 6.4 Now Playing ads mode

This is one of the most important surfaces in the APK.

Relevant files include:

- `res/layout/ads_mode_page_layout.xml`
- `res/layout/ads_mode_overlay.xml`
- `res/layout/ads_mode_controls_row_layout.xml`
- `res/layout/nowplayingmini_ads.xml`
- `smali_classes6/com/spotify/nowplayingmodes/adsmode/data/AdsModeModel$Format.smali`
- `smali_classes6/com/spotify/nowplayingmodes/adsmode/data/AdsModeModel$Product.smali`
- `smali_classes6/com/spotify/nowplayingmodes/adsmode/ui/overlay/AdsOverlayControlsLayout.smali`

Formats include:

- `VIDEO_AD_VERTICAL`
- `VIDEO_AD_HORIZONTAL`
- `AUDIO_AD`

Products include:

- `PODCAST`
- `MUSIC`
- `SPONSORED_SESSION`

The layouts show dedicated ad-mode UI components such as:

- ad header
- skippable ad stub
- ad info row
- seekbar
- ad controls
- CTA card
- thumbs-up and thumbs-down feedback
- countdown behavior

This strongly indicates the player has a formal ad mode, not just a temporary overlay hack.

### 6.5 Embedded and muted video ads

`smali_classes2/com/spotify/adsdisplay/embeddedad/mutedvideoview/MutedVideoAdView.smali` and `res/layout/muted_video_ad_layout.xml` define a reusable inline video ad surface.

This is useful for contexts where Spotify wants to render video ads without switching to the main fullscreen overlay activity.

### 6.6 Sponsored playlists

Sponsored playlists are a separate subsystem with their own models and API paths.

Relevant files include:

- `smali_classes2/p/u5p0.smali`
- `smali_classes2/com/spotify/adsdisplay/sponsorshipimpl/model/SponsorshipAdData.smali`
- `smali_classes2/p/y5p0.smali`
- `res/layout/sponsored_header_section.xml`

The code references API paths such as:

- `sponsoredplaylist/v1/sponsored`
- `sponsoredplaylist/v1/sponsored/{contextUri}`

The sponsorship data model carries fields such as:

- impression URLs
- creative ID
- line item ID
- logo URL
- clickthrough URL
- click tracking URL
- advertiser name

This subsystem is not just decorative labeling. It has its own data model, tracking, and rendering.

### 6.7 Leave-behinds and "Recent ads"

The APK contains explicit post-impression ad surfaces:

- `res/layout/stream_ad_leavebehind_row.xml`
- `res/layout/leavebehindads_fragment.xml`
- `res/layout/leavebehindads_dialog_fragment.xml`
- `smali_classes2/com/spotify/adsdisplay/cta/model/LeavebehindAd.smali`
- `smali_classes5/p/wm3.smali`

The leave-behind model includes fields such as:

- advertiser
- clickthrough URL
- button label
- tagline
- display image
- logo image
- tracking events
- ad ID
- optional cross-promo fields

This indicates Spotify preserves ad surfaces after playback in some contexts, especially podcast-related ones.

### 6.8 DSA / "Why you're seeing this ad"

Spotify has a dedicated DSA metadata flow.

Relevant files include:

- `smali_classes2/com/spotify/adsdisplay/dsa/datasource/DsaMetadataRequest.smali`
- `smali_classes2/p/com.smali`
- `smali_classes2/com/spotify/adsdisplay/dsa/datasource/DsaMetadataResponse.smali`
- `smali_classes2/com/spotify/adsdisplay/dsa/events/proto/AdDSAEvent.smali`

The response includes fields such as:

- `targetingTypes`
- `legalEntityName`
- `showTailoredAdsSection`

The strings show user-facing explainability content around:

- age
- gender
- interests
- location
- tailored ads explanations

This is a significant product requirement surface, not just analytics plumbing.

### 6.9 CMP / consent

`smali_classes2/com/spotify/adsdisplay/products/cmp/CMPActivity.smali` hosts consent-related UI and references OneTrust.

That matters because ad delivery is intertwined with consent and privacy flows.

### 6.10 Ad feedback

The APK contains dedicated ad feedback models and surfaces, including:

- `res/layout/marquee_feedback_menu.xml`
- `smali_classes6/com/spotify/nowplayingmodes/adsmode/events/proto/AdFeedbackEvent.smali`
- `smali_classes2/com/spotify/adshome/events/proto/AdFeedbackEvent.smali`

The feedback models track fields such as:

- line item ID
- creative ID
- ad playback ID
- ad ID
- advertiser
- format
- image or media references
- feedback event type

This means ad feedback is part of the product loop, not an afterthought.

## Part 7: User-Facing Evidence In Resources

`res/values/strings.xml` contains strong textual evidence of multiple ad experiences.

Examples discovered during analysis include strings for:

- ad-supported or free-tier messaging
- "Enjoy this playlist with limited ads..."
- "Spotify starts after this brief ad"
- "Ad Playing"
- "Recent ads"
- "Presented By %s"
- "Limited ads, thanks to %s"
- premium being sold as ad-free

These strings line up with the code structure:

- app-open ads
- sponsored playlists
- leave-behinds
- normal playback ads
- premium upsell

The resources are consistent with the smali findings.

## Part 8: Eventing, Telemetry, And State Synchronization

### 8.1 Event subscription and posting

`smali_classes2/p/u90.smali` interacts with `spotify.ads.esperanto.proto.Events`.

It supports operations equivalent to:

- `SubEvent`
- `postEvent`

The code includes fields for:

- slot ID
- source
- reason
- payload
- timestamps

`smali_classes2/p/z90.smali` posts unmanaged events.

### 8.2 Why this matters

For an engineer responsible for ads, this means ad rendering cannot be treated as pure UI. There is a feedback channel back to backend systems for:

- impression lifecycle
- playback lifecycle
- user interactions
- failures or discards
- feedback and explainability events

### 8.3 Ad state is timestamped

`smali_classes7/p/sno0.smali` parses timestamped ad state values.

This suggests ad state is expected to evolve during a session and possibly be shared across multiple subsystems.

## Part 9: Best-Supported Answer To The Main Question

### What makes ads appear for free users?

The best-supported answer from this APK is:

Ads appear for free users because the session is identified as non-premium, free-tier and ad-specific product flows remain enabled for that session, Spotify backend services populate one or more named ad slots with inventory, player state carries ad-break or in-stream ad information, and dedicated UI surfaces render the resulting ads.

### In more concrete engineering terms

The app appears to do roughly this:

1. Read session/account product type.
2. Determine whether the user is in free-tier or premium routing.
3. Apply remote config and eligibility rules for ad-capable experiences.
4. Register or subscribe to named ad slots appropriate for the current product surface.
5. Upload targeting, state, headers, and context.
6. Ask backend services for slot inventory or prepare/trigger a slot.
7. Receive ad queue, slot events, and ad-break state.
8. Render the ad in a surface appropriate to the slot and format.
9. Track the outcome and update state.

### What does not appear to be true

The APK does not strongly support the idea that:

- one local boolean alone turns ads on everywhere
- ads are only implemented by one activity
- ads are driven solely by generic Google ad SDK calls

There is support code related to advertising identifiers, but that appears ancillary. The primary serving architecture is Spotify-owned.

## Part 10: Engineering Implications

If an engineer owned ads in this app, they would need to reason across all of these layers:

- account tier and entitlement derivation
- remote config namespaces and experiments
- eligibility services
- slot registry and slot naming
- slot lifecycle orchestration
- ad queue retrieval
- targeting and request metadata
- player ad-break integration
- multiple rendering surfaces
- telemetry and feedback
- consent and DSA explainability

This means bugs or behavioral changes can easily be introduced by touching only one layer without understanding the others.

Examples:

- a product-tier change could accidentally expose or suppress ad flows
- a slot enablement change could affect only some surfaces but not others
- a player-state change could break ad countdowns or break position handling
- a remote config change could enable a UI surface before the corresponding backend eligibility path is ready
- a sponsored playlist change could affect preroll clearing behavior through `sponsored_playlist_clear_preroll_slot_enabled`

## Part 11: Supporting Evidence Map

| Area | Key files | Why they matter |
| --- | --- | --- |
| Account tier | `smali_classes4/com/spotify/connectivity/sessionstate/SessionState.smali`, `smali_classes7/p/pi90.smali` | Free vs premium state and entitlement interpretation |
| Free-tier routing | `smali_classes7/p/e13.smali`, `smali_classes7/p/rt2.smali`, `smali_classes7/p/sz2.smali` | Free-tier experiments and alternate product surfaces |
| Slot registry | `smali_classes2/p/mg0.smali` | Enumerates named ad placements |
| Slot lifecycle | `smali_classes2/p/adh.smali`, `smali_classes2/p/avn0.smali`, `smali_classes2/p/dh0.smali` | Create, subscribe, prepare, trigger, fetch, clear |
| Response parsing | `smali_classes6/p/eqn0.smali`, `smali_classes2/p/pky0.smali`, `smali_classes7/p/rwm0.smali` | Converts proto responses into app models and queues |
| Targeting/state | `smali_classes2/p/p7r0.smali`, `smali_classes2/p/nop0.smali`, `smali_classes2/p/lnp0.smali`, `smali_classes7/p/sno0.smali` | Serving context and synchronized ad state |
| Settings/headers | `smali_classes2/p/lpl0.smali`, `smali_classes2/p/ebr.smali`, `smali_classes2/p/cbr.smali` | Slot enablement and request metadata |
| Event telemetry | `smali_classes2/p/u90.smali`, `smali_classes2/p/z90.smali` | Event streaming and event posting |
| Playback integration | `smali_classes2/p/goj0.smali`, `smali_classes6/p/qgi.smali`, `smali_classes6/p/edq.smali`, `smali_classes2/p/any0.smali` | In-stream ads and ad-break context |
| Fullscreen UI | `AndroidManifest.xml`, `smali_classes2/com/spotify/adsdisplay/display/DisplayAdActivity.smali`, `smali_classes2/com/spotify/adsdisplay/browser/inapp/InAppBrowserActivity.smali` | Primary fullscreen and clickthrough rendering |
| Now Playing ads | `res/layout/ads_mode_page_layout.xml`, `res/layout/ads_mode_overlay.xml`, `smali_classes6/com/spotify/nowplayingmodes/adsmode/data/AdsModeModel$Format.smali`, `smali_classes6/com/spotify/nowplayingmodes/adsmode/data/AdsModeModel$Product.smali` | Dedicated player ad mode |
| Sponsored playlists | `smali_classes2/p/u5p0.smali`, `smali_classes2/com/spotify/adsdisplay/sponsorshipimpl/model/SponsorshipAdData.smali`, `res/layout/sponsored_header_section.xml` | Sponsored playlist ad model and surface |
| Leave-behinds | `smali_classes2/com/spotify/adsdisplay/cta/model/LeavebehindAd.smali`, `smali_classes5/p/wm3.smali`, `res/layout/leavebehindads_fragment.xml` | Post-impression ad surfaces |
| DSA / consent | `smali_classes2/com/spotify/adsdisplay/dsa/datasource/DsaMetadataRequest.smali`, `smali_classes2/com/spotify/adsdisplay/products/cmp/CMPActivity.smali` | Explainability and privacy flow |
| App-open ads | `smali_classes7/p/jl2.smali`, `smali_classes2/p/lp0.smali`, `smali_classes6/p/u080.smali` | Dedicated app-open eligibility and rendering path |

## Part 12: Limits Of This Analysis

This summary is strong on client architecture, but there are limits:

- It does not prove exact backend rules for who receives which ads.
- It does not prove precise runtime sequencing for every ad surface.
- It does not include live traffic, protobuf payload captures, or backend feature values.
- Some slot handlers in the registry appear to use no-op registration callbacks, so not every defined slot is guaranteed to be active in this build.
- Some product-type gates were easy to identify, but not every free-versus-premium branch in the APK was exhaustively traced.

## Final Conclusion

The APK shows a mature, multi-layer Spotify-owned ad platform embedded deeply into the Android client.

The key point is not "which single file shows ads".

The key point is that ads appear through a coordinated system:

- account tier marks the session as free or premium
- remote config and eligibility decide which ad experiences are active
- slot services manage placements such as preroll, stream, podcast midroll, sponsored playlist, and now-playing bar
- backend services return inventory and ad-break state
- player and UI surfaces render the ad
- telemetry, state, feedback, DSA, and consent flows complete the lifecycle

If an engineer needed to own or debug ads in this app, they would need to treat the system as a distributed product pipeline, not as a single UI feature.
