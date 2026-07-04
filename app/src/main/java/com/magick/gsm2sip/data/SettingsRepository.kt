package com.magick.gsm2sip.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sip_settings")

/**
 * Persists [SipConfig] to Jetpack DataStore.
 *
 * NOTE ON CREDENTIALS: the SIP password is stored in DataStore preferences,
 * which live in the app's private storage but are NOT hardware-encrypted. For a
 * production deployment, wrap [KEY_PASSWORD] reads/writes with Jetpack Security
 * (EncryptedSharedPreferences) or the Android Keystore. Left as plain DataStore
 * here to keep the sample self-contained; see README "Known limitations".
 */
class SettingsRepository(private val context: Context) {

    val config: Flow<SipConfig> = context.dataStore.data.map { it.toConfig() }

    suspend fun save(config: SipConfig) {
        context.dataStore.edit { p ->
            p[KEY_DOMAIN] = config.domain
            p[KEY_USERNAME] = config.username
            p[KEY_PASSWORD] = config.password
            p[KEY_REALM] = config.realm
            p[KEY_DISPLAY_NAME] = config.displayName
            p[KEY_PORT] = config.port
            p[KEY_TRANSPORT] = config.transport.name
            p[KEY_OUTBOUND_PROXY] = config.outboundProxy
            p[KEY_CALLER_ID] = config.callerId
            p[KEY_STUN_ENABLED] = config.stunEnabled
            p[KEY_STUN_SERVER] = config.stunServer
            p[KEY_REG_EXPIRY] = config.regExpirySeconds
            p[KEY_KEEPALIVE] = config.keepAliveSeconds
            p[KEY_CODECS] = config.codecPriority.joinToString(",")
            p[KEY_AUTOSTART] = config.autoStartOnBoot
            p[KEY_AUTOANSWER] = config.autoAnswerGsm
            p[KEY_DTMF_2833] = config.dtmfRfc2833
        }
    }

    private fun Preferences.toConfig(): SipConfig {
        val d = SipConfig()
        return SipConfig(
            domain = this[KEY_DOMAIN] ?: d.domain,
            username = this[KEY_USERNAME] ?: d.username,
            password = this[KEY_PASSWORD] ?: d.password,
            realm = this[KEY_REALM] ?: d.realm,
            displayName = this[KEY_DISPLAY_NAME] ?: d.displayName,
            port = this[KEY_PORT] ?: d.port,
            transport = runCatching { SipTransport.valueOf(this[KEY_TRANSPORT] ?: d.transport.name) }
                .getOrDefault(d.transport),
            outboundProxy = this[KEY_OUTBOUND_PROXY] ?: d.outboundProxy,
            callerId = this[KEY_CALLER_ID] ?: d.callerId,
            stunEnabled = this[KEY_STUN_ENABLED] ?: d.stunEnabled,
            stunServer = this[KEY_STUN_SERVER] ?: d.stunServer,
            regExpirySeconds = this[KEY_REG_EXPIRY] ?: d.regExpirySeconds,
            keepAliveSeconds = this[KEY_KEEPALIVE] ?: d.keepAliveSeconds,
            codecPriority = this[KEY_CODECS]?.split(",")?.filter { it.isNotBlank() } ?: d.codecPriority,
            autoStartOnBoot = this[KEY_AUTOSTART] ?: d.autoStartOnBoot,
            autoAnswerGsm = this[KEY_AUTOANSWER] ?: d.autoAnswerGsm,
            dtmfRfc2833 = this[KEY_DTMF_2833] ?: d.dtmfRfc2833,
        )
    }

    private companion object {
        val KEY_DOMAIN = stringPreferencesKey("domain")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_REALM = stringPreferencesKey("realm")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_PORT = intPreferencesKey("port")
        val KEY_TRANSPORT = stringPreferencesKey("transport")
        val KEY_OUTBOUND_PROXY = stringPreferencesKey("outbound_proxy")
        val KEY_CALLER_ID = stringPreferencesKey("caller_id")
        val KEY_STUN_ENABLED = booleanPreferencesKey("stun_enabled")
        val KEY_STUN_SERVER = stringPreferencesKey("stun_server")
        val KEY_REG_EXPIRY = intPreferencesKey("reg_expiry")
        val KEY_KEEPALIVE = intPreferencesKey("keepalive")
        val KEY_CODECS = stringPreferencesKey("codecs")
        val KEY_AUTOSTART = booleanPreferencesKey("autostart")
        val KEY_AUTOANSWER = booleanPreferencesKey("autoanswer")
        val KEY_DTMF_2833 = booleanPreferencesKey("dtmf_2833")
    }
}
