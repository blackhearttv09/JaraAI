package com.jara.ai.live

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Acts only after the user enables it in Android Settings. Never reads password fields. */
class JaraAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JaraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // Fixed: use a block body so "return false" is allowed.
    fun press(button: String): Boolean {
        val action = when (button) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }

        return performGlobalAction(action)
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)

        for (n in nodes) {
            var cur: AccessibilityNodeInfo? = n

            while (cur != null) {
                if (cur.isClickable && cur.isEnabled) {
                    return cur.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }
                cur = cur.parent
            }
        }

        return false
    }

    fun typeText(text: String): Boolean {
        val node = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false

        if (node.isPassword) return false

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }

        return node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        )
    }

    fun scroll(down: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false

        fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isScrollable) return n

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { child ->
                    find(child)?.let { return it }
                }
            }

            return null
        }

        val scrollable = find(root) ?: return false

        return scrollable.performAction(
            if (down) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
        )
    }

    fun readScreen(max: Int = 3000): String {
        val sb = StringBuilder()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || sb.length > max || node.isPassword) return

            val text = node.text ?: node.contentDescription

            if (!text.isNullOrBlank()) {
                sb.append(text).append('\n')
            }

            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
        }

        walk(rootInActiveWindow)
        return sb.toString().take(max)
    }
}
