package io.dmcc.bleptt

import android.app.Application
import io.dmcc.bleptt.ble.PttBleClient
import io.dmcc.bleptt.data.PairedRepository

class PttApp : Application() {
    val bleClient: PttBleClient by lazy { PttBleClient(applicationContext) }
    val pairedRepository: PairedRepository by lazy { PairedRepository(applicationContext) }
}
