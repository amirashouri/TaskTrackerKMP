package com.tasktracker.keychain

import com.tasktracker.core.TTLogger
import com.tasktracker.core.isDesktopApp
import com.tasktracker.core.logMessage

/**
 * The JVM keychain, chosen by what the process is and what it is running on.
 *
 * Three cases, and the two fallbacks are the point of the function:
 *
 *  - **The JVM test host** ([isDesktopApp] false) gets an [InMemoryKeychain]. A test that
 *    wrote a credential to the developer's real login keychain would leave state behind, be
 *    invisible to the next reader of the test, and fail on a machine with a locked keychain —
 *    the same reasoning that keeps `user-defaults` in memory on this host.
 *  - **The Desktop app on macOS** gets [MacOsKeychain], so a sign-in survives the process.
 *  - **The Desktop app anywhere else** gets an [InMemoryKeychain] and says so in the log.
 *    There is no Windows Credential Manager or libsecret binding here, and silently returning
 *    a stub that looks like it stored something is worse than an admitted gap: the user is
 *    signed out on the next launch, which is the behaviour the JVM target had before this
 *    existed, and it is at least honest.
 *
 * [host] is unused — the JVM backend has no equivalent of Android's `Context`, and reaching a
 * macOS keychain needs no ambient object.
 */
actual fun systemKeychain(host: Any?): KeychainAccess {
    if (!isDesktopApp) return InMemoryKeychain()

    if (!isMacOs()) {
        TTLogger.auth.info {
            logMessage {
                append("keychain: no system credential store on ")
                append(System.getProperty("os.name") ?: "this platform")
                append(" - credentials will not survive a restart")
            }
        }
        return InMemoryKeychain()
    }

    return MacOsKeychain()
}

/** Whether this JVM is on macOS, which is the only platform the JNA binding targets. */
internal fun isMacOs(): Boolean =
    System.getProperty("os.name")?.startsWith("Mac") == true
