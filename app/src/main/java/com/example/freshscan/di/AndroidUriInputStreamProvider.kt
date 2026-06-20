package com.example.freshscan.di

import android.content.Context
import com.example.freshscan.domain.common.UriInputStreamProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUriInputStreamProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : UriInputStreamProvider {
    override fun openInputStream(uriString: String): InputStream? =
        context.contentResolver.openInputStream(android.net.Uri.parse(uriString))
}
