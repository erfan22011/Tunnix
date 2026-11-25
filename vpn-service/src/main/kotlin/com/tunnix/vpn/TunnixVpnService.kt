package com.tunnix.vpn

import android.net.VpnService
import android.content.Intent

class TunnixVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
