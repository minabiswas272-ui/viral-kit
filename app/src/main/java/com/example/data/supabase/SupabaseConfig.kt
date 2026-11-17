package com.example.data.supabase

import com.example.BuildConfig
import com.example.ui.components.isSafeWebUrl

data class SupabaseConfig(
    val projectUrl: String,
    val anonKey: String
) {
    val isConfigured: Boolean
        get() = isSafeWebUrl(projectUrl) &&
            projectUrl != PLACEHOLDER_URL &&
            anonKey.isNotBlank() &&
            anonKey != PLACEHOLDER_KEY

    val restBaseUrl: String
        get() = "${projectUrl.trim().trimEnd('/')}/rest/v1"

    companion object {
        const val PLACEHOLDER_URL = "SUPABASE_URL_DEFAULT_VALUE"
        const val PLACEHOLDER_KEY = "SUPABASE_ANON_KEY_DEFAULT_VALUE"

        fun fromBuildConfig(): SupabaseConfig {
            return SupabaseConfig(
                projectUrl = BuildConfig.SUPABASE_URL.trim(),
                anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
            )
        }
    }
}
