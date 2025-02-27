/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.stdext.HashCode
import java.lang.ref.WeakReference

private const val RANGE_MAP_ATTRIBUTE_VERSION = 2

/** We use [WeakReference] because uncached [loadRangeMap] is quite cheap */
private val MACRO_RANGE_MAP_CACHE_KEY: Key<WeakReference<RangeMap>> = Key.create("MACRO_RANGE_MAP_CACHE_KEY")
private val RANGE_MAP_ATTRIBUTE = FileAttribute(
    "org.rust.macro.RangeMap",
    RANGE_MAP_ATTRIBUTE_VERSION,
    /* fixedSize = */ true // don't allocate extra space for each record
)

fun VirtualFile.writeRangeMap(ranges: RangeMap) {
    checkWriteAccessAllowed()

    RANGE_MAP_ATTRIBUTE.writeAttribute(this).use {
        ranges.writeTo(it)
    }

    if (getUserData(MACRO_RANGE_MAP_CACHE_KEY)?.get() != null) {
        putUserData(MACRO_RANGE_MAP_CACHE_KEY, WeakReference(ranges))
    }
}

fun VirtualFile.loadRangeMap(): RangeMap? {
    checkReadAccessAllowed()

    getUserData(MACRO_RANGE_MAP_CACHE_KEY)?.get()?.let { return it }

    val data = RANGE_MAP_ATTRIBUTE.readAttribute(this) ?: return null
    val ranges = RangeMap.readFrom(data)
    putUserData(MACRO_RANGE_MAP_CACHE_KEY, WeakReference(ranges))
    return ranges
}

fun VirtualFile.loadMixHash(): HashCode? {
    val name = name
    val underscoreIndex = name.lastIndexOf('_')
    if (underscoreIndex == -1) return null
    return HashCode.fromHexString(name.substring(0, underscoreIndex))
}
