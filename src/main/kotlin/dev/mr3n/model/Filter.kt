package dev.mr3n.model

import kotlinx.serialization.Serializable

@Serializable
data class Filter(val type: Type,val list: List<String>) {
    enum class Type {
        BLACKLIST,
        WHITELIST
    }
}
