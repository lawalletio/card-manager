package io.lawallet.cardactivation

import android.app.Application
import io.lawallet.cardactivation.data.AuthStore
import io.lawallet.cardactivation.pos.ZcsPrinter

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthStore.init(this)
        // Best-effort early init; re-run after the READ_PHONE_STATE grant.
        ZcsPrinter.init()
    }
}
