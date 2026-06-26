package com.oropeza.urbanapp.core.bootstrap

sealed class UrbanBootstrapResult {
    abstract val status: UrbanBootstrapStatus
    
    data class Success(override val status: UrbanBootstrapStatus) : UrbanBootstrapResult()
    data class Warning(override val status: UrbanBootstrapStatus) : UrbanBootstrapResult()
    data class Failure(override val status: UrbanBootstrapStatus) : UrbanBootstrapResult()
}
