package com.oropeza.urbanapp.core.config

sealed class UrbanConfiguration {
    abstract val key: String

    data class BooleanConfig(override val key: String, val value: Boolean) : UrbanConfiguration()
    data class IntConfig(override val key: String, val value: Int) : UrbanConfiguration()
    data class DoubleConfig(override val key: String, val value: Double) : UrbanConfiguration()
    data class StringConfig(override val key: String, val value: String) : UrbanConfiguration()
}
