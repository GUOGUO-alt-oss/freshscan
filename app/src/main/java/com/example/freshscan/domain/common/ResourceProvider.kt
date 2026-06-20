package com.example.freshscan.domain.common

interface ResourceProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg args: Any): String
}
