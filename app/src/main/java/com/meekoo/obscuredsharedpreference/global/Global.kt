package com.meekoo.obscuredsharedpreference.global

import com.meekoo.obscuredsharedpreference.application.AppApplication
import com.meekoo.obscuredsharedpreference.storage.EncryptScheme
import com.meekoo.obscuredsharedpreference.storage.EncryptSharedPreference
import com.meekoo.obscuredsharedpreference.storage.EncryptType

val storage: EncryptSharedPreference?
    get() = EncryptSharedPreference.init(
        context = AppApplication.mApp,
        encryptType = EncryptType.KeyAndValue,
        encryptScheme = EncryptScheme.PBEWithMD5AndDES
    )