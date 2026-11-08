package org.najwa.app

import android.util.AtomicFile
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Android's Linux rename replaces the destination atomically. Windows File.renameTo
 * refuses an existing destination; adapt only that OS primitive in JVM tests. */
@Implements(AtomicFile::class)
class WindowsAtomicFile {
    companion object {
        @JvmStatic @Implementation(minSdk=30)
        fun rename(source: File,target: File) {
            Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
