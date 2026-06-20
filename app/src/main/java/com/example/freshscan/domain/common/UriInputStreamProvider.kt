package com.example.freshscan.domain.common

import java.io.InputStream

interface UriInputStreamProvider {
    fun openInputStream(uriString: String): InputStream?
}
