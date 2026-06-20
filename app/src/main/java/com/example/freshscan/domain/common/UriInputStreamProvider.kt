package com.example.freshscan.domain.common

import android.net.Uri
import java.io.InputStream

interface UriInputStreamProvider {
    fun openInputStream(uri: Uri): InputStream?
}
