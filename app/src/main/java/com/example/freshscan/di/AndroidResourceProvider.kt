package com.example.freshscan.di

import android.content.Context
import com.example.freshscan.domain.common.ResourceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidResourceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ResourceProvider {
    override fun getString(resId: Int): String = context.getString(resId)
    override fun getString(resId: Int, vararg args: Any): String = context.getString(resId, *args)
}
