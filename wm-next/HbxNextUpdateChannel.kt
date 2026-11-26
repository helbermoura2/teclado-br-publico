package com.wasimaster.wmkeyboard.app.updates

/** Only permanent experimental HBX releases. Upstream and temporary APKs are never candidates. */
internal object HbxNextUpdateChannel {
    private val tagPattern = Regex("""^hbx-next-(\d+\.\d+\.\d+)-permanent$""")
    private val versionPattern = Regex("""^wmkeyboard-(\d+\.\d+\.\d+)-hbx-next-vc(\d+)-full-intl-universal\.apk$""")
    fun eligible(releases: List<GithubRelease>): List<GithubRelease> = releases.mapNotNull { release ->
        val version = tagPattern.matchEntire(release.tagName)?.groupValues?.get(1) ?: return@mapNotNull null
        if (release.draft || !release.prerelease) return@mapNotNull null
        val assets = release.assets.filter { asset ->
            val match = versionPattern.matchEntire(asset.name)
            match != null && match.groupValues[1] == version &&
                (match.groupValues[2].toIntOrNull() ?: 0) >= 48 &&
                asset.size in 1L..400_000_000L &&
                ReleaseAssets.sha256Of(asset.digest) != null &&
                asset.browserDownloadUrl == "https://github.com/${GithubReleases.REPO}/releases/download/${release.tagName}/${asset.name}"
        }
        release.copy(assets = assets).takeIf { assets.size == 1 }
    }
}
