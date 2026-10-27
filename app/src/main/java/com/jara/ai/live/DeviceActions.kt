package com.jara.ai.live

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

object DeviceActions {
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = "${ctx.packageName}/${JaraAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(me, true) } && JaraAccessibilityService.instance != null
    }

    private fun fn(name: String, desc: String, props: JSONObject? = null, req: List<String> = emptyList()): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props != null) o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", JSONArray(req)))
        return o
    }
    private fun str(d: String) = JSONObject().put("type", "STRING").put("description", d)

    val declarations: JSONArray = JSONArray()
        .put(fn("open_app", "Open an installed app by its name.", JSONObject().put("app_name", str("App label, e.g. YouTube")), listOf("app_name")))
        .put(fn("press_system_button", "Press a system button: home, back, recents or notifications.", JSONObject().put("button", str("home|back|recents|notifications")), listOf("button")))
        .put(fn("click_on_screen_text", "Tap the on-screen element whose text matches.", JSONObject().put("text", str("Visible text")), listOf("text")))
        .put(fn("type_text", "Type text into the focused input field.", JSONObject().put("text", str("Text to type")), listOf("text")))
        .put(fn("scroll", "Scroll the current screen.", JSONObject().put("direction", str("up|down")), listOf("direction")))
        .put(fn("read_screen", "Read visible text on the current screen."))
        .put(fn("open_phone_settings", "Open the Android Settings app."))

    /** Executes a model-requested tool. Returns an honest result; failures are reported, never faked. */
    fun execute(ctx: Context, name: String, args: JSONObject, screenControlAllowed: Boolean): JSONObject {
        fun fail(m: String) = JSONObject().put("success", false).put("error", m)
        fun ok(extra: JSONObject = JSONObject()) = extra.put("success", true)
        if (!screenControlAllowed) return fail("User has not allowed screen control in app settings.")
        try {
            when (name) {
                "open_app" -> {
                    val q = args.optString("app_name").trim()
                    val pm = ctx.packageManager
                    val li = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    val hit = li.firstOrNull { it.loadLabel(pm).toString().equals(q, true) }
                        ?: li.firstOrNull { it.loadLabel(pm).toString().contains(q, true) }
                        ?: return fail("No installed app matches '$q'")
                    val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return fail("Cannot launch")
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    return ok(JSONObject().put("opened", hit.loadLabel(pm).toString()))
                }
                "open_phone_settings" -> {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return ok()
                }
            }
            val svc = JaraAccessibilityService.instance ?: return fail("Accessibility service is not enabled.")
            return when (name) {
                "press_system_button" -> if (svc.press(args.optString("button"))) ok() else fail("Action failed")
                "click_on_screen_text" -> if (svc.clickText(args.optString("text"))) ok() else fail("No clickable element with that text")
                "type_text" -> if (svc.typeText(args.optString("text"))) ok() else fail("No editable focused field (or it is a password field)")
                "scroll" -> if (svc.scroll(args.optString("direction") == "down")) ok() else fail("Nothing scrollable")
                "read_screen" -> ok(JSONObject().put("text", svc.readScreen()))
                else -> fail("Unknown tool $name")
            }
        } catch (e: Exception) { return fail(e.message ?: "error") }
    }
}
