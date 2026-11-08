package org.najwa.app

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Test-host adaptation only: AndroidX assumes Linux '/' in canonical paths.
 * Preserve the exact descendant check while accepting Windows canonical separators. */
@Implements(className="androidx.core.content.FileProvider\$SimplePathStrategy",isInAndroidSdk=false)
class WindowsFileProviderPaths {
    @Implementation fun belongsToRoot(filePath: String,rootPath: String): Boolean =
        filePath.replace('\\','/').trimEnd('/').startsWith(rootPath.replace('\\','/').trimEnd('/')+"/")
}
