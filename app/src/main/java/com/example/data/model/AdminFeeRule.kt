package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/**
 * Aturan biaya admin bertingkat/tier otomatis berdasarkan rentang nominal transfer.
 * Contoh default:
 * - Nominal Rp 10.000 s/d Rp 400.000 -> Admin Rp 3.000
 * - Nominal Rp 401.000 s/d Rp 600.000 -> Admin Rp 4.000
 * Pengguna dapat mengubah, menghapus, atau menambahkan aturan baru.
 */
data class AdminFeeRule(
  val id: String = UUID.randomUUID().toString(),
  val minNominal: Long,
  val maxNominal: Long,
  val adminFee: Long,
) {
  fun matches(nominal: Long): Boolean {
    return nominal in minNominal..maxNominal
  }

  val formattedRange: String
    get() = "${formatRupiah(minNominal)} – ${formatRupiah(maxNominal)}"

  val formattedFee: String
    get() = formatRupiah(adminFee)

  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("id", id)
      put("minNominal", minNominal)
      put("maxNominal", maxNominal)
      put("adminFee", adminFee)
    }
  }

  companion object {
    private fun formatRupiah(value: Long): String {
      val format = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("id").setRegion("ID").build())
      format.maximumFractionDigits = 0
      return format.format(value).replace("Rp", "Rp ")
    }

    fun fromJson(obj: JSONObject): AdminFeeRule {
      return AdminFeeRule(
        id = obj.optString("id", UUID.randomUUID().toString()),
        minNominal = obj.optLong("minNominal", 0L),
        maxNominal = obj.optLong("maxNominal", 0L),
        adminFee = obj.optLong("adminFee", 0L),
      )
    }

    fun defaultRules(): List<AdminFeeRule> = listOf(
      // 10rb sampai 400rb admin 3rb
      AdminFeeRule(
        id = "tier-1",
        minNominal = 10_000L,
        maxNominal = 400_000L,
        adminFee = 3_000L
      ),
      // 401rb sampai 600rb admin 4rb
      AdminFeeRule(
        id = "tier-2",
        minNominal = 401_000L,
        maxNominal = 600_000L,
        adminFee = 4_000L
      )
    )

    fun rulesToJson(rules: List<AdminFeeRule>): String {
      val array = JSONArray()
      rules.forEach { array.put(it.toJson()) }
      return array.toString()
    }

    fun rulesFromJson(jsonStr: String): List<AdminFeeRule> {
      if (jsonStr.isBlank()) return defaultRules()
      return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<AdminFeeRule>()
        for (i in 0 until array.length()) {
          list.add(fromJson(array.getJSONObject(i)))
        }
        if (list.isEmpty()) defaultRules() else list
      } catch (e: Exception) {
        defaultRules()
      }
    }

    /**
     * Mencari aturan tier yang cocok dengan nominal transfer.
     * Mengembalikan biaya admin yang sesuai, atau null jika tidak ada aturan yang cocok.
     */
    fun findMatchingRule(nominal: Long, rules: List<AdminFeeRule>): AdminFeeRule? {
      if (nominal <= 0L) return null
      return rules.firstOrNull { it.matches(nominal) }
    }

    fun calculateAdminFee(nominal: Long, rules: List<AdminFeeRule>): Long? {
      return findMatchingRule(nominal, rules)?.adminFee
    }
  }
}
