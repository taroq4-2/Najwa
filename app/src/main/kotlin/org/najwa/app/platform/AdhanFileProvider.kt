package org.najwa.app.platform

/** Separate subclass avoids vendor issues with declaring FileProvider directly. */
class AdhanFileProvider: androidx.core.content.FileProvider()
