package com.cra.cloudreve.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == 0
}

@Serializable
data class BatchItem<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null
)

@Serializable
data class BatchResponse<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: List<BatchItem<T>>? = null
)
