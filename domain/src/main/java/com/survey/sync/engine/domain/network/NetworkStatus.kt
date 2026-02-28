package com.survey.sync.engine.domain.network

sealed class NetworkStatus {
    object Available : NetworkStatus()
    object Weak : NetworkStatus()
    object Unavailable : NetworkStatus()
}