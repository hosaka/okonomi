package cc.hosaka.okonomi

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import cc.hosaka.okonomi.feature.navigation.processTextForward

/**
 * The "Okonomi" item in apps' text selection toolbar: hands the
 * selection to [MainActivity] in okonomi's own task, then finishes
 * without ever being seen.
 *
 * It exists because the toolbar starts its target expecting a result,
 * and Android then places that activity in the caller's task and
 * ignores `singleTask`. Seen on a Pixel 6 with Chrome: with the filter
 * on [MainActivity], a second copy of it opened inside Chrome's task
 * while the copy in okonomi's own task took the lookup, so the reader
 * saw an empty Search, and back from a fresh copy sent Chrome's whole
 * task away. This activity accepts living in the caller's task; what it
 * starts is built by [processTextForward], where the rules are tested.
 *
 * The referrer tells a selection made in okonomi itself (its own text
 * menus offer "Okonomi" too) from one made elsewhere. A caller can set
 * its referrer, but at worst that turns its lookup into an ordinary
 * in-app search.
 *
 * No result is ever set: `finish()` answers the caller with
 * `RESULT_CANCELED`, which leaves its text as it was. A plain [Activity]
 * with `Theme.NoDisplay`, which requires finishing before `onResume`,
 * as `onCreate` does.
 */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processTextForward(
            source = intent,
            target = ComponentName(this, MainActivity::class.java),
            fromOkonomi = referrer?.host == packageName,
        )?.let { forward -> startActivity(forward) }
        finish()
    }
}
