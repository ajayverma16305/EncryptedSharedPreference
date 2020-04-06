package com.meekoo.obscuredsharedpreference.storage

import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.provider.Settings
import android.util.*
import com.google.gson.*
import javax.crypto.*
import javax.crypto.spec.*

/**
 * Created by Ajay Verma.
 */
enum class EncryptScheme(val rawValue: String) {
    PBEWithMD5AndDES(rawValue = "PBEWithMD5AndDES"),
    PBKDF2WithHmacSHA1(rawValue = "PBEWithMD5AndDES" /*"PBKDF2WithHmacSHA1"*/) // Currently it doesnot follow the script
}

/**
 * Allow encrypt type for encryption
 */
enum class EncryptType {
    OnlyValue,
    KeyAndValue;

    val encryptValueType: Boolean get() = this == OnlyValue
    val encryptBothType: Boolean get() = this == KeyAndValue
}

/**
 * Constructor
 * @param context
 * @param delegate - SharedPreferences object from the system
 */

class EncryptSharedPreference private constructor(
    private val context: Context,
    private val delegate: SharedPreferences,
    private var encryptType: EncryptType,
    private var encryptScheme: EncryptScheme
) : SharedPreferences, OnEncryptPreferenceListener {

    companion object {
        private const val PREFERENCE_NAME = "_encrypt_pref_file"
        private val UTF_8 = Charsets.UTF_8
        private const val ERROR_TEXT = "Warning, could not decrypt the value. Possible incorrect key. "

        //this key is defined at runtime based on ANDROID_ID which is supposed to last the life of the device
        private var SEKRIT: CharArray? = null
        private var mPreference: EncryptSharedPreference? = null

        //Set to true if a decryption error was detected
        //in the case of float, int, and long we can tell if there was a parse error
        //this does not detect an error in strings or boolean - that requires more sophisticated checks
        var decryptionErrorFlag = false

        /**
         * Only used to change to a new key during runtime.
         * If you don't want to use the default per-device key for example
         * @param key
         */
        fun setNewKey(key: String) {
            SEKRIT = key.toCharArray()
        }

        /**
         * Accessor to grab the preferences in a singleton.  This stores the reference in a singleton so it can be accessed repeatedly with
         * no performance penalty
         * @param c - the context used to access the preferences.
         * @param appName - domain the shared preferences should be stored under
         * @param contextMode - Typically Context.MODE_PRIVATE
         * @return
         */
        @Synchronized
        fun init(
            context: Context,
            preferenceName: String? = PREFERENCE_NAME,
            contextMode: Int = Context.MODE_PRIVATE,
            encryptType: EncryptType = EncryptType.KeyAndValue,
            encryptScheme: EncryptScheme = EncryptScheme.PBEWithMD5AndDES
        ): EncryptSharedPreference? {
            if (null == mPreference) {
                // Make sure to use application context since preferences live outside an Activity
                // use for objects that have global scope like: prefs or starting services
                val applicationContext = context.applicationContext

                mPreference = EncryptSharedPreference(
                    context = applicationContext,
                    delegate = applicationContext.getSharedPreferences(preferenceName, contextMode),
                    encryptType = encryptType,
                    encryptScheme = encryptScheme
                )
            }
            return mPreference
        }
    }

    init {
        SEKRIT = Settings.Secure.ANDROID_ID.toCharArray()
    }

    fun setEncryptScheme(encryptScheme: EncryptScheme) {
        this.encryptScheme = encryptScheme
    }

    fun setEncryptType(encryptType: EncryptType) {
        this.encryptType = encryptType
    }

    override fun edit(): Editor {
        return Editor()
    }

    override fun getAll(): Map<String, Any> {
        return mapOf()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        //if these weren't encrypted, then it won't be a string
        val v = try {
            if (encryptType.encryptValueType) {
                delegate.getString(key, defValue.toString())
            } else {
                delegate.getString(encrypt(key), defValue.toString())
            }
        } catch (e: ClassCastException) {
            return delegate.getBoolean(key, defValue)
        }
        return decrypt(v)?.toBoolean() ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val v = try {
            if (encryptType.encryptValueType) {
                delegate.getString(key, defValue.toString())
            } else {
                delegate.getString(encrypt(key), defValue.toString())
            }
        } catch (e: ClassCastException) {
            return delegate.getFloat(key, defValue)
        }
        try {
            return decrypt(v)?.toFloatOrNull() ?: defValue
        } catch (e: NumberFormatException) {
            //could not decrypt the number.  Maybe we are using the wrong key?
            decryptionErrorFlag = true
            Log.e(this.javaClass.name, "" + e.message)
        }
        return defValue
    }

    override fun getInt(key: String, defValue: Int): Int {
        val v = try {
            if (encryptType.encryptValueType) {
                delegate.getString(key, defValue.toString())
            } else {
                delegate.getString(encrypt(key), defValue.toString())
            }
        } catch (e: ClassCastException) {
            return delegate.getInt(key, defValue)
        }

        try {
            return decrypt(v)?.toIntOrNull() ?: defValue
        } catch (e: NumberFormatException) {
            //could not decrypt the number.  Maybe we are using the wrong key?
            decryptionErrorFlag = true
            Log.e(this.javaClass.name, "$ERROR_TEXT e.message")
        }
        return defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val v = try {
            if (encryptType.encryptValueType) {
                delegate.getString(key, defValue.toString())
            } else {
                delegate.getString(encrypt(key), defValue.toString())
            }
        } catch (e: ClassCastException) {
            return delegate.getLong(key, defValue)
        }
        try {
            return decrypt(v)?.toLongOrNull() ?: defValue
        } catch (e: NumberFormatException) {
            //could not decrypt the number.  Maybe we are using the wrong key?
            decryptionErrorFlag = true
            Log.e(this.javaClass.name, "$ERROR_TEXT e.message")
        }
        return defValue
    }

    override fun getString(key: String, defValue: String?): String? {
        val value = if (encryptType.encryptValueType) {
            delegate.getString(key, defValue.toString())
        } else {
            delegate.getString(encrypt(key), defValue.toString())
        }
        return decrypt(value) ?: defValue
    }

    override fun getDouble(key: String, defaultValue: Double): Double {
        val v = try {
            if (encryptType.encryptValueType) {
                delegate.getString(key, defaultValue.toString())
            } else {
                delegate.getString(encrypt(key), defaultValue.toString())
            }
        } catch (e: ClassCastException) {
            return delegate.getLong(key, defaultValue.toLong()).toDouble()
        }
        try {
            return Double.fromBits(decrypt(v)?.toLongOrNull() ?: 0L)
        } catch (e: NumberFormatException) {
            //could not decrypt the number.  Maybe we are using the wrong key?
            decryptionErrorFlag = true
            Log.e(this.javaClass.name, "$ERROR_TEXT e.message")
        }
        return defaultValue
    }

    override fun contains(s: String): Boolean {
        return delegate.contains(decrypt(s))
    }

    override fun putString(key: String, value: String) {
        mPreference?.run {
            edit().putString(key, value).apply()
        }
    }

    override fun putInt(key: String, value: Int) {
        mPreference?.run { edit().putInt(key, value).apply() }
    }

    override fun putLong(key: String, value: Long) {
        mPreference?.run { edit().putLong(key, value).apply() }
    }

    override fun putDouble(key: String, value: Double) {
        mPreference?.run {
            edit().putLong(key, java.lang.Double.doubleToRawLongBits(value)).apply()
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        mPreference?.run { edit().putBoolean(key, value).apply() }
    }

    /**
     * Saves object into the Preferences.
     *
     * @param `object` Object of model class (of type [T]) to save
     * @param key Key with which Shared preferences to
     **/
    fun <T> putObject(key: String, value: T) {
        //Convert object to JSON String.
        val jsonString = GsonBuilder().create().toJson(value)
        //Save that String in SharedPreferences
        this.putString(key, jsonString)
    }

    /**
     * Used to retrieve object from the Preferences.
     *
     * @param key Shared Preference key with which object was saved.
     **/
    inline fun <reified T> getObject(key: String): T? {
        //We read JSON String which was saved.
        val value = getString(key, null)

        //JSON String was found which means object can be read.
        //We convert this JSON String to model object. Parameter "c" (of
        //type “T” is used to cast.
        return GsonBuilder().create().fromJson(value!!, T::class.java)
    }

    override fun remove(key: String) {
        mPreference?.run { edit().remove(key).apply() }
    }

    override fun clear() {
        mPreference?.run { edit().clear().apply() }
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        throw RuntimeException("This class does not work with String Sets.")
    }

    private fun encrypt(value: String?): String {
        return try {
            val bytes = value?.toByteArray(UTF_8) ?: ByteArray(0)

            val keyFactory = SecretKeyFactory.getInstance(encryptScheme.rawValue)
            val key = keyFactory.generateSecret(PBEKeySpec(SEKRIT))
            val pbeCipher = Cipher.getInstance(encryptScheme.rawValue)

            pbeCipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                PBEParameterSpec(
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ).toByteArray(UTF_8), 20
                )
            )
            String(Base64.encode(pbeCipher.doFinal(bytes), Base64.NO_WRAP), UTF_8)
        } catch (e: Exception) {
            Log.e(this.javaClass.name, "$ERROR_TEXT e.message")
            value ?: throw RuntimeException(e)
        }
    }

    private fun decrypt(value: String?): String? {
        return try {
            val bytes = if (value != null) Base64.decode(value, Base64.DEFAULT) else ByteArray(0)

            val keyFactory = SecretKeyFactory.getInstance(encryptScheme.rawValue)
            val key = keyFactory.generateSecret(PBEKeySpec(SEKRIT))
            val pbeCipher = Cipher.getInstance(encryptScheme.rawValue)

            pbeCipher.init(
                Cipher.DECRYPT_MODE,
                key,
                PBEParameterSpec(
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ).toByteArray(UTF_8), 20
                )
            )
            String(pbeCipher.doFinal(bytes), UTF_8)
        } catch (e: Exception) {
            Log.e(this.javaClass.name, "$ERROR_TEXT e.message")
            null
        }
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {
        delegate.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {
        delegate.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    inner class Editor : SharedPreferences.Editor {
        private var delegate: SharedPreferences.Editor =
            this@EncryptSharedPreference.delegate.edit()

        override fun putBoolean(key: String, value: Boolean): Editor {
            if (encryptType.encryptValueType) {
                delegate.putString(key, encrypt(value.toString()))
            } else {
                delegate.putString(encrypt(key), encrypt(value.toString()))
            }
            return this
        }

        override fun putFloat(key: String, value: Float): Editor {
            if (encryptType.encryptValueType) {
                delegate.putString(key, encrypt(value.toString()))
            } else {
                delegate.putString(encrypt(key), encrypt(value.toString()))
            }
            return this
        }

        override fun putInt(key: String, value: Int): Editor {
            if (encryptType.encryptValueType) {
                delegate.putString(key, encrypt(value.toString()))
            } else {
                delegate.putString(encrypt(key), encrypt(value.toString()))
            }
            return this
        }

        override fun putLong(key: String, value: Long): Editor {
            if (encryptType.encryptValueType) {
                delegate.putString(key, encrypt(value.toString()))
            } else {
                delegate.putString(encrypt(key), encrypt(value.toString()))
            }
            return this
        }

        override fun putString(key: String, value: String?): Editor {
            if (encryptType.encryptValueType) {
                delegate.putString(key, encrypt(value.toString()))
            } else {
                delegate.putString(encrypt(key), encrypt(value.toString()))
            }
            return this
        }

        override fun apply() {
            // to maintain compatibility with android level 7
            delegate.commit()
        }

        override fun clear(): Editor {
            delegate.clear()
            return this
        }

        override fun commit(): Boolean {
            return delegate.commit()
        }

        override fun remove(s: String): Editor {
            delegate.remove(decrypt(s))
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            throw RuntimeException("This class does not work with String Sets.")
        }
    }
}

interface OnEncryptPreferenceListener {
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putDouble(key: String, value: Double)
    fun putBoolean(key: String, value: Boolean)
    fun getDouble(key: String, defaultValue: Double): Double
    fun remove(key: String)
    fun clear()
}