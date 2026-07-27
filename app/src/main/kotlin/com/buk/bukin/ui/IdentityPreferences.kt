package com.buk.bukin.ui

import android.content.Context
import androidx.core.content.edit
import com.buk.bukin.domain.model.Colaborador

/**
 * Who this phone belongs to: a typed name, the id the server gave back for it, and — since
 * session 5 — the path to a photo the person picked.
 *
 * `SharedPreferences` for the same reason `OnboardingPreferences` uses it: three strings
 * read once at launch do not justify DataStore, a schema, or a coroutine.
 *
 * This is scaffolding, not a feature. It is the whole of "authentication" in v1, and it is
 * why the identity limitation in `context/architecture.md` is worth disclosing: the app
 * believes whatever name was typed here.
 */
class IdentityPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Null until someone has entered a name and the server has issued an id for it. */
    var colaborador: Colaborador?
        get() {
            val id = prefs.getString(KEY_ID, null) ?: return null
            val nombre = prefs.getString(KEY_NOMBRE, null) ?: return null
            return Colaborador(id = id, nombre = nombre)
        }
        set(value) = prefs.edit {
            putString(KEY_ID, value?.id)
            putString(KEY_NOMBRE, value?.nombre)
        }

    /**
     * Absolute path to the avatar inside `filesDir`, or null.
     *
     * **On-device only, by decision.** A Supabase Storage bucket would mean new RLS
     * policies and an upload path in `:core:data` — backend work inside a design session,
     * on a product with no auth to scope a bucket against. The accepted cost is that it
     * does not survive a reinstall.
     */
    var avatarPath: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit { putString(KEY_AVATAR, value) }

    private companion object {
        const val FILE_NAME = "bukin_identity"
        const val KEY_ID = "colaborador_id"
        const val KEY_NOMBRE = "nombre_completo"
        const val KEY_AVATAR = "avatar_path"
    }
}
