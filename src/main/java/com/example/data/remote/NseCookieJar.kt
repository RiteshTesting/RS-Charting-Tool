package com.example.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class NseCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val existing = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(existing) {
            for (cookie in cookies) {
                existing.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) {
                    existing.add(cookie)
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val matching = mutableListOf<Cookie>()
        cookieStore.forEach { (_, list) ->
            synchronized(list) {
                list.removeAll { it.expiresAt <= now }
                for (cookie in list) {
                    if (cookie.matches(url)) {
                        matching.add(cookie)
                    }
                }
            }
        }
        return matching
    }

    fun hasCookiesFor(host: String): Boolean {
        val list = cookieStore[host] ?: return false
        synchronized(list) {
            return list.isNotEmpty()
        }
    }

    fun clear() {
        cookieStore.clear()
    }
}
