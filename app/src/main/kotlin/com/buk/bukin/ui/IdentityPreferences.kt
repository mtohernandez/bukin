package com.buk.bukin.ui

import android.content.Context
import com.buk.bukin.domain.model.Colaborador

/**
 * Who this phone belongs to: a typed name and the id the server gave back for it.
 *
 * `SharedPreferences` for the same reason `OnboardingPreferences` uses it — two strings read
 * once at launch do not justify DataStore, a schema, or a coroutine.
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
        set(value) = prefs.edit()
            .putString(KEY_ID, value?.id)
            .putString(KEY_NOMBRE, value?.nombre)
            .apply()

    private companion object {
        const val FILE_NAME = "bukin_identity"
        const val KEY_ID = "colaborador_id"
        const val KEY_NOMBRE = "nombre_completo"
    }
}
