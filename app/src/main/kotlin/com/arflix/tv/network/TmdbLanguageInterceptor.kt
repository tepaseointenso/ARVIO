package com.arflix.tv.network

import com.arflix.tv.util.LanguageSettingsRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbLanguageInterceptor @Inject constructor(
    private val languageSettingsRepository: LanguageSettingsRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host != "api.themoviedb.org") {
            return chain.proceed(request)
        }
        if (url.queryParameter("language") != null) {
            return chain.proceed(request)
        }
        val path = url.encodedPath
        val shouldSkipLanguage = path.contains("/external_ids") ||
            path.contains("/watch/providers") ||
            path.contains("/find/")
        if (shouldSkipLanguage) {
            return chain.proceed(request)
        }

        val localizedUrl = url.newBuilder()
            .addQueryParameter("language", languageSettingsRepository.currentMetadataLanguageTag())
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(localizedUrl)
                .build()
        )
    }
}
