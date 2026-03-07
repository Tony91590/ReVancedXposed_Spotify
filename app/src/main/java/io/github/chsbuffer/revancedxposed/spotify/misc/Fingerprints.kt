package io.github.chsbuffer.revancedxposed.spotify.misc

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.SkipTest
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.strings
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType
import io.github.chsbuffer.revancedxposed.returns

val productStateProtoFingerprint = fingerprint {
    returns("Ljava/util/Map;")
    classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
}

val attributesMapField =
    findFieldDirect { productStateProtoFingerprint().usingFields.single().field }

val buildQueryParametersFingerprint = findMethodDirect {
    findMethod {
        matcher {
            strings("trackRows", "device_type:tablet")
        }
    }.single()
}
val contextFromJsonFingerprint = fingerprint {
    opcodes(
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_STATIC
    )
    methodMatcher {
        name("fromJson")
        declaredClass(
            "voiceassistants.playermodels.ContextJsonAdapter", StringMatchType.EndsWith
        )
    }
}

val contextMenuViewModelClass = findClassDirect {
    return@findClassDirect runCatching {
        fingerprint {
            strings("ContextMenuViewModel(header=")
        }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.CONSTRUCTOR)
            strings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
            parameters("L", "Ljava/util/List;", "Z")
        }
    }.declaredClass!!
}

val viewModelClazz = findClassDirect {
    findMethod {
        findFirst = true
        matcher { name("getViewModel") }
    }.single().returnType!!
}

val isPremiumUpsellField = findFieldDirect {
    viewModelClazz().fields.filter { it.typeName == "boolean" }[1]
}

@SkipTest
fun structureGetSectionsFingerprint(className: String) = fingerprint {
    classMatcher { className(className, StringMatchType.EndsWith) }
    methodMatcher {
        addUsingField {
            usingType = UsingType.Read
            name = "sections_"
        }
    }
}

val homeStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("homeapi.proto.HomeStructure")
val browseStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("browsita.v1.resolved.BrowseStructure")

val pendragonJsonFetchMessageRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            name("apply")
            addInvoke {
                name("<init>")
                declaredClass("FetchMessageRequest", StringMatchType.EndsWith)
            }
        }
    }.single()
}

val pendragonJsonFetchMessageListRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            name("apply")
            addInvoke {
                name("<init>")
                declaredClass("FetchMessageListRequest", StringMatchType.EndsWith)
            }
        }
    }.single()
}

// --- NEW RAW DEXKIT FINGERPRINTS ---
val protobufMessageFingerprint: FindMethodListFunc = { bridge ->
    // 1. Find classes that contain Protobuf strings
    val protoClasses = bridge.findClass {
        matcher { usingStrings("Protocol message") }
    }

    // 2. Extract only the methods that return a byte array
    // Depending on the DexKit version, byte arrays are named "byte[]" or "[B"
    protoClasses.flatMap { it.methods }.filter {
        it.returnType?.name == "byte[]" || it.returnType?.name == "[B"
    }
}

val adManagerFingerprint: FindClassFunc = { bridge ->
    bridge.findClass {
        searchPackages("com.spotify")
        matcher {
            // Broadened the search terms to catch newer ad manager iterations
            usingStrings("spotify:ad:", "ad_request", "ad_playback", "sponsored_context")
        }
    }.firstOrNull() ?: throw RuntimeException("AdManager Strings Not Found")
}

    val adActivityFingerprint: FindClassFunc = { bridge ->
        bridge.findClass {
            matcher {
                superClass("android.app.Activity")
                // Broadened to catch any activity referencing ad layouts or promo UI
                usingStrings("ad_companion", "video_ad", "sponsored_session", "advertisement", "slate_ad")
            }
        }.firstOrNull() ?: throw RuntimeException("AdActivity Strings Not Found")
    }
val productStateImplFingerprint: FindClassFunc = { bridge ->
    bridge.findClass {
        matcher { usingStrings("streaming-rules", "ads") }
    }.firstOrNull() ?: throw RuntimeException("ProductState Strings Not Found") // Removed ?.classData
}
