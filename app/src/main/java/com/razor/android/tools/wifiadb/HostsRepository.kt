package com.razor.android.tools.wifiadb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一台保存的 PC 主机 */
data class HostEntry(
    val id: Long,
    var label: String,
    var ip: String
)

/** 用 SharedPreferences（JSON）保存主机列表与当前选中项 */
class HostsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("wifi_adb_hosts", Context.MODE_PRIVATE)

    fun load(): MutableList<HostEntry> {
        val data = prefs.getString(KEY_DATA, null) ?: return mutableListOf()
        return try {
            val json = JSONObject(data)
            val arr = json.optJSONArray("hosts") ?: JSONArray()
            MutableList(arr.length()) { i ->
                val item = arr.getJSONObject(i)
                HostEntry(item.getLong("id"), item.getString("label"), item.getString("ip"))
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun loadActiveId(): Long? = try {
        prefs.getString(KEY_DATA, null)?.let { JSONObject(it).optLong("activeId", -1L) }
            ?.takeIf { it > 0 }
    } catch (e: Exception) {
        null
    }

    fun save(hosts: List<HostEntry>, activeId: Long?) {
        val json = JSONObject().apply {
            put("hosts", JSONArray().apply {
                hosts.forEach { h ->
                    put(JSONObject().apply {
                        put("id", h.id)
                        put("label", h.label)
                        put("ip", h.ip)
                    })
                }
            })
            put("activeId", activeId ?: -1L)
        }
        prefs.edit().putString(KEY_DATA, json.toString()).apply()
    }

    companion object {
        private const val KEY_DATA = "data"
    }
}
