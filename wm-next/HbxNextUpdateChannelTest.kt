package com.wasimaster.wmkeyboard.app.updates

import org.junit.Assert.*
import org.junit.Test

class HbxNextUpdateChannelTest {
    private val tag = "hbx-next-0.1.2-permanent"
    private val name = "wmkeyboard-0.1.2-hbx-next-vc48-full-intl-universal.apk"
    private val asset = GithubAsset(name = name, size = 100, digest = "sha256:" + "a".repeat(64),
        browserDownloadUrl = "https://github.com/${GithubReleases.REPO}/releases/download/$tag/$name")
    private val release = GithubRelease(tagName = tag, prerelease = true, assets = listOf(asset))
    @Test fun permanentOnly() {
        assertEquals(listOf(release), HbxNextUpdateChannel.eligible(listOf(release)))
        assertTrue(HbxNextUpdateChannel.eligible(listOf(release.copy(tagName = "hbx-next-0.1.2-experimental"), release.copy(draft = true), release.copy(prerelease = false))).isEmpty())
    }
    @Test fun rejectWrongUrlDigestAndVersion() {
        for (bad in listOf(asset.copy(browserDownloadUrl = "https://example.org/$name"), asset.copy(digest = null), asset.copy(name = "wmkeyboard-0.1.1-hbx-next-vc47-full-intl-universal.apk"))) {
            assertTrue(HbxNextUpdateChannel.eligible(listOf(release.copy(assets = listOf(bad)))).isEmpty())
        }
    }
    @Test fun onlyNewerVersionIsOffered() {
        val releases = HbxNextUpdateChannel.eligible(listOf(release))
        assertEquals(48, ReleaseAssets.chooseCandidate(releases,47,"full",listOf("arm64-v8a"),true,"intl")?.versionCode)
        assertNull(ReleaseAssets.chooseCandidate(releases,48,"full",listOf("arm64-v8a"),true,"intl"))
    }
}
