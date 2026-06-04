package lol.omnius.android

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Route this activity's volume keys to Android media volume. Call in onCreate. */
fun Activity.routeVolumeKeys() {
    volumeControlStream = AudioManager.STREAM_MUSIC
}

/**
 * Handle a volume key by adjusting STREAM_MUSIC (with the on-screen bar).
 * Returns true if it was a volume key. Call first in onKeyDown.
 * Needed because this TV box doesn't route volume keys to apps automatically.
 */
fun Activity.handleVolumeKey(keyCode: Int): Boolean {
    val dir = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
        KeyEvent.KEYCODE_VOLUME_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
        else -> return false
    }
    (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    return true
}
