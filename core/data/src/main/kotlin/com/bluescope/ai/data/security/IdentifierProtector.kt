package com.bluescope.ai.data.security

interface IdentifierProtector {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
    fun lookupHash(value: String): String
}
