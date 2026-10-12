package com.tasktracker.core

/**
 * Whether the process is the Desktop app rather than the JVM test host.
 *
 * The Desktop entry point sets this before opening the window; the JVM test host
 * never does, so the test host keeps the in-memory preferences backend and does
 * not leave state between runs. Public so the platform-actual modules (which live
 * in different packages) can read it, and so the desktop app can set it.
 */
public var isDesktopApp = false
