package com.tunnix.core.models

/**
 * Single authoritative resolver for asymmetric download settings.
 * Applies VLESS download settings strictly for default ports (2053, 6793) or
 * user-specified custom target ports.
 *
 * NOTE: Both production servers requiring extra routing on ports 2053 and 6793
 * use protocol VLESS with network XHTTP (confirmed by project owner).
 * Non-XHTTP servers (e.g. VLESS with WS or TCP on Cloudflare ports like 2053)
 * must remain untouched to prevent broken connections.
 */
object DownloadSettingsResolver {

    /**
     * Resolves the appropriate DownloadSettingsConfig for a given server and custom configuration.
     * Order of precedence:
     * 1) Explicit custom configuration if enabled and matching target/server port.
     * 2) Built-in preset from SecureVault (port 2053 or 6793) if enabled, provided user hasn't explicitly disabled the feature.
     * 3) null for all other cases.
     */
    fun resolve(server: ServerConfig, custom: DownloadSettingsConfig?): DownloadSettingsConfig? {
        if (server.protocol != ProtocolType.VLESS || server.network != NetworkType.XHTTP) return null

        // 1) User explicitly defined this port in custom configuration and it is enabled
        if (custom != null && custom.isEnabled &&
            (server.port == custom.effectiveTargetPort || server.port == custom.port)
        ) {
            return custom
        }

        // 2) Otherwise, internal preset for default ports (2053 or 6793)
        val preset = SecureVault.getConfigForPort(server.port)
        if (preset != null && preset.isEnabled) {
            // Respect explicit user intent to disable the feature
            if (custom != null && !custom.isEnabled) return null
            return preset
        }

        return null
    }
}
