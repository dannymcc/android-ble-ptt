package io.dmcc.bleptt

import android.app.Application
import io.dmcc.bleptt.ble.PttBleClient
import io.dmcc.bleptt.data.OverlayPreferences
import io.dmcc.bleptt.data.PairedRepository
import io.dmcc.bleptt.overlay.PttOverlayController

class PttApp : Application() {
    val bleClient: PttBleClient by lazy { PttBleClient(applicationContext) }
    val pairedRepository: PairedRepository by lazy { PairedRepository(applicationContext) }
    val overlayPreferences: OverlayPreferences by lazy { OverlayPreferences(applicationContext) }
    val overlayController: PttOverlayController by lazy { PttOverlayController(applicationContext) }
}
