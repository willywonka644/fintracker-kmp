package io.github.willywonka644.fintracker.security

interface IPinStore {
    fun hasPin(): Boolean
    fun savePin(hash: String)
    fun getPin(): String?
    fun clearPin()
}
