package com.stoxsim.app

import android.app.Application
import com.stoxsim.app.data.EncryptedRefreshTokenStore
import com.stoxsim.app.data.StoxSimApi

class StoxSimApplication : Application() {
    // Share one session lock even if Android creates another activity/ViewModel in this process.
    internal val api by lazy { StoxSimApi(EncryptedRefreshTokenStore(this)) }
}
