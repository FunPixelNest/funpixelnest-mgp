package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.config.BackendConfig

/**
 * Picks the backend: the real Supabase one when SUPABASE_URL and SUPABASE_ANON_KEY are configured
 * (local.properties or environment), otherwise the in-memory demo backend.
 */
fun createPlatformRepository(): PlatformRepository =
    if (BackendConfig.SUPABASE_URL.isNotBlank() && BackendConfig.SUPABASE_ANON_KEY.isNotBlank()) {
        SupabasePlatformRepository(BackendConfig.SUPABASE_URL, BackendConfig.SUPABASE_ANON_KEY)
    } else {
        FakePlatformRepository()
    }

/** True when the app is running on the demo backend (shown as a small badge in Profile). */
val isDemoBackend: Boolean
    get() = BackendConfig.SUPABASE_URL.isBlank() || BackendConfig.SUPABASE_ANON_KEY.isBlank()
