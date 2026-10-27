package com.example.data.ocr

import java.util.Locale

data class BankInfo(
  val officialName: String,
  val category: String, // "Konvensional", "Syariah", "BPD", "Digital", "E-Wallet"
  val aliases: List<String>,
)

data class BankMatchResult(
  val officialName: String,
  val isConfident: Boolean,
  val originalQuery: String,
)

object BankRegistry {

  val ALL_BANKS: List<BankInfo> = listOf(
    // 1. Bank Konvensional Terbesar & Umum
    BankInfo("BCA", "Konvensional", listOf("bca", "bank central asia", "pt bank central asia", "bank central asia tbk", "m-bca", "mybca", "klikbca", "bca mobile", "central asia")),
    BankInfo("Bank Mandiri", "Konvensional", listOf("mandiri", "bank mandiri", "pt bank mandiri", "bank mandiri tbk", "livin", "livin' by mandiri", "livin by mandiri")),
    BankInfo("Bank BRI", "Konvensional", listOf("bri", "bank rakyat indonesia", "pt bank rakyat indonesia", "bank rakyat indonesia tbk", "brimo")),
    BankInfo("Bank BNI", "Konvensional", listOf("bni", "bank negara indonesia", "pt bank negara indonesia", "bank negara indonesia tbk", "bni mobile", "wondr", "wondr by bni")),
    BankInfo("Bank BTN", "Konvensional", listOf("btn", "bank tabungan negara", "pt bank tabungan negara", "btn mobile", "bale by btn")),
    BankInfo("CIMB Niaga", "Konvensional", listOf("cimb", "cimb niaga", "bank cimb niaga", "octo mobile", "octo clicks")),
    BankInfo("Bank Danamon", "Konvensional", listOf("danamon", "bank danamon", "pt bank danamon indonesia", "d-bank", "d-bank pro")),
    BankInfo("Permata Bank", "Konvensional", listOf("permata", "bank permata", "pt bank permata tbk", "permata me", "permatanet")),
    BankInfo("Maybank Indonesia", "Konvensional", listOf("maybank", "bank maybank", "maybank indonesia", "m2u")),
    BankInfo("Panin Bank", "Konvensional", listOf("panin", "bank panin", "pt bank panin tbk")),
    BankInfo("OCBC", "Konvensional", listOf("ocbc", "ocbc nisp", "bank ocbc nisp", "one mobile")),
    BankInfo("Bank Mega", "Konvensional", listOf("mega", "bank mega", "pt bank mega tbk", "m-smile")),
    BankInfo("Bank Sinarmas", "Konvensional", listOf("sinarmas", "bank sinarmas", "simobi", "simobiplus")),
    BankInfo("Bank UOB Indonesia", "Konvensional", listOf("uob", "bank uob", "uob indonesia", "tmrw", "tmrw by uob")),
    BankInfo("Bank BTPN", "Konvensional", listOf("btpn", "bank btpn", "pt bank btpn tbk")),
    BankInfo("KB Bukopin", "Konvensional", listOf("bukopin", "bank bukopin", "kb bukopin", "kb bank")),
    BankInfo("Bank DBS Indonesia", "Konvensional", listOf("dbs", "bank dbs", "digibank", "digibank by dbs")),
    BankInfo("Bank HSBC Indonesia", "Konvensional", listOf("hsbc", "bank hsbc", "hsbc indonesia")),
    BankInfo("Standard Chartered", "Konvensional", listOf("standard chartered", "stanchart")),
    BankInfo("Citibank", "Konvensional", listOf("citibank", "citi")),
    BankInfo("Bank Commonwealth", "Konvensional", listOf("commonwealth", "bank commonwealth")),
    BankInfo("Bank Woori Saudara", "Konvensional", listOf("bws", "bank woori saudara", "woori saudara")),
    BankInfo("Bank Mayapada", "Konvensional", listOf("mayapada", "bank mayapada")),
    BankInfo("Bank MNC", "Konvensional", listOf("mnc", "bank mnc", "mnc bank", "motionbank")),
    BankInfo("Bank Artha Graha", "Konvensional", listOf("artha graha", "bank artha graha", "ag mobile")),
    BankInfo("Bank Maspion", "Konvensional", listOf("maspion", "bank maspion")),
    BankInfo("Bank Mestika", "Konvensional", listOf("mestika", "bank mestika", "bank mestika dharma")),
    BankInfo("Bank Shinhan", "Konvensional", listOf("shinhan", "bank shinhan", "shinhan bank")),
    BankInfo("Bank Ina Perdana", "Konvensional", listOf("bank ina", "ina perdana", "bina")),
    BankInfo("Bank Victoria", "Konvensional", listOf("bank victoria", "victoria")),
    BankInfo("Bank Bumi Arta", "Konvensional", listOf("bumi arta", "bank bumi arta")),
    BankInfo("Bank SBI Indonesia", "Konvensional", listOf("sbi", "bank sbi", "bank sbi indonesia")),
    BankInfo("Bank JTrust", "Konvensional", listOf("jtrust", "j trust", "bank jtrust", "j trust bank")),
    BankInfo("Bank Sahabat Sampoerna", "Konvensional", listOf("sampoerna", "bank sampoerna", "bank sahabat sampoerna")),
    BankInfo("Bank Capital", "Konvensional", listOf("bank capital", "capital indonesia")),
    BankInfo("Bank Ganesha", "Konvensional", listOf("ganesha", "bank ganesha")),
    BankInfo("Bank Index", "Konvensional", listOf("bank index", "index selindo")),
    BankInfo("Bank MAS", "Konvensional", listOf("bank mas", "multiarta sentosa")),
    BankInfo("Bank Resona Perdania", "Konvensional", listOf("resona perdania", "bank resona")),
    BankInfo("Bank CCB Indonesia", "Konvensional", listOf("ccb", "ccb indonesia", "china construction bank")),
    BankInfo("Bank ICBC Indonesia", "Konvensional", listOf("icbc", "icbc indonesia")),
    BankInfo("Bank of China", "Konvensional", listOf("bank of china", "boc")),

    // 2. Bank Syariah
    BankInfo("Bank BSI", "Syariah", listOf("bsi", "bank syariah indonesia", "pt bank syariah indonesia", "bsi mobile", "hasanah")),
    BankInfo("Bank Muamalat", "Syariah", listOf("muamalat", "bank muamalat", "pt bank muamalat indonesia", "muamalat din")),
    BankInfo("BCA Syariah", "Syariah", listOf("bca syariah", "bank bca syariah")),
    BankInfo("Bank Mega Syariah", "Syariah", listOf("mega syariah", "bank mega syariah", "m-syariah")),
    BankInfo("KB Bukopin Syariah", "Syariah", listOf("bukopin syariah", "kb bukopin syariah")),
    BankInfo("Bank BJB Syariah", "Syariah", listOf("bjb syariah", "bank bjb syariah", "mobile maslahah")),
    BankInfo("Panin Dubai Syariah", "Syariah", listOf("panin syariah", "panin dubai syariah", "bank panin dubai syariah")),
    BankInfo("Bank Aladin Syariah", "Syariah", listOf("aladin", "bank aladin", "aladin syariah", "bank aladin syariah")),
    BankInfo("Bank BTPN Syariah", "Syariah", listOf("btpn syariah", "bank btpn syariah")),
    BankInfo("Bank Danamon Syariah", "Syariah", listOf("danamon syariah", "bank danamon syariah")),
    BankInfo("Permata Bank Syariah", "Syariah", listOf("permata syariah", "bank permata syariah")),
    BankInfo("CIMB Niaga Syariah", "Syariah", listOf("cimb syariah", "cimb niaga syariah")),

    // 3. Bank Pembangunan Daerah (BPD)
    BankInfo("Bank BJB", "BPD", listOf("bjb", "bank bjb", "bank jabar banten", "pt bank bjb", "digi by bjb")),
    BankInfo("Bank DKI", "BPD", listOf("dki", "bank dki", "jakone", "jakone mobile")),
    BankInfo("Bank Jatim", "BPD", listOf("jatim", "bank jatim", "pt bank jatim", "jconnect")),
    BankInfo("Bank Jateng", "BPD", listOf("jateng", "bank jateng", "bpd jateng", "bima mobile")),
    BankInfo("BPD DIY", "BPD", listOf("bpd diy", "bank bpd diy", "diy")),
    BankInfo("Bank Sumut", "BPD", listOf("sumut", "bank sumut", "bpd sumut")),
    BankInfo("Bank Nagari", "BPD", listOf("nagari", "bank nagari", "bpd sumbar")),
    BankInfo("Bank Riau Kepri Syariah", "BPD", listOf("riau kepri", "bank riau kepri", "brk syariah")),
    BankInfo("Bank Sumsel Babel", "BPD", listOf("sumsel babel", "bank sumsel babel", "bpd sumsel")),
    BankInfo("Bank Lampung", "BPD", listOf("lampung", "bank lampung")),
    BankInfo("Bank Jambi", "BPD", listOf("jambi", "bank jambi")),
    BankInfo("Bank Bengkulu", "BPD", listOf("bengkulu", "bank bengkulu")),
    BankInfo("Bank Kalbar", "BPD", listOf("kalbar", "bank kalbar")),
    BankInfo("Bank Kalsel", "BPD", listOf("kalsel", "bank kalsel")),
    BankInfo("Bank Kaltimtara", "BPD", listOf("kaltimtara", "bank kaltimtara")),
    BankInfo("Bank Kalteng", "BPD", listOf("kalteng", "bank kalteng")),
    BankInfo("Bank Sulselbar", "BPD", listOf("sulselbar", "bank sulselbar")),
    BankInfo("Bank SulutGo", "BPD", listOf("sulutgo", "bank sulutgo", "bsg")),
    BankInfo("Bank Sultra", "BPD", listOf("sultra", "bank sultra")),
    BankInfo("Bank Sulteng", "BPD", listOf("sulteng", "bank sulteng")),
    BankInfo("Bank NTB Syariah", "BPD", listOf("ntb syariah", "bank ntb", "bank ntb syariah")),
    BankInfo("Bank NTT", "BPD", listOf("ntt", "bank ntt")),
    BankInfo("Bank Maluku Malut", "BPD", listOf("maluku malut", "bank maluku", "bank maluku malut")),
    BankInfo("Bank Papua", "BPD", listOf("papua", "bank papua")),
    BankInfo("Bank Aceh Syariah", "BPD", listOf("bank aceh", "aceh syariah", "bank aceh syariah", "action mobile")),

    // 4. Bank Digital
    BankInfo("SeaBank", "Digital", listOf("seabank", "bank seabank", "pt bank seabank indonesia", "seabank indonesia")),
    BankInfo("Bank Jago", "Digital", listOf("jago", "bank jago", "pt bank jago tbk", "kantong jago")),
    BankInfo("Jenius (BTPN)", "Digital", listOf("jenius", "btpn", "bank btpn", "jenius btpn")),
    BankInfo("Allo Bank", "Digital", listOf("allo", "allo bank", "pt allo bank indonesia")),
    BankInfo("Bank Neo Commerce", "Digital", listOf("neo", "bank neo", "bank neo commerce", "bnc", "neobank")),
    BankInfo("Krom Bank", "Digital", listOf("krom", "krom bank")),
    BankInfo("Superbank", "Digital", listOf("superbank", "bank superbank")),
    BankInfo("Blu by BCA Digital", "Digital", listOf("blu", "blu bca", "bca digital", "blu by bca")),
    BankInfo("Line Bank", "Digital", listOf("line bank", "hana bank", "keb hana", "pt bank keb hana indonesia")),
    BankInfo("Bank Raya", "Digital", listOf("bank raya", "raya", "bri agroniaga")),
    BankInfo("Bank Saqu", "Digital", listOf("bank saqu", "saqu", "bank jasa jakarta")),
    BankInfo("Digibank by DBS", "Digital", listOf("digibank", "digibank by dbs")),
    BankInfo("TMRW by UOB", "Digital", listOf("tmrw", "tmrw by uob")),
    BankInfo("Hijra Bank", "Digital", listOf("hijra", "hijra bank")),

    // 5. Dompet Digital (E-Wallet)
    BankInfo("DANA", "E-Wallet", listOf("dana", "dana id", "dompet digital indonesia")),
    BankInfo("GoPay", "E-Wallet", listOf("gopay", "gojek", "go-pay", "gopay id")),
    BankInfo("OVO", "E-Wallet", listOf("ovo", "ovo cash", "ovo id")),
    BankInfo("ShopeePay", "E-Wallet", listOf("shopeepay", "shopee pay", "spay")),
    BankInfo("LinkAja", "E-Wallet", listOf("linkaja", "link aja")),
    BankInfo("AstraPay", "E-Wallet", listOf("astrapay", "astra pay")),
    BankInfo("i.saku", "E-Wallet", listOf("isaku", "i.saku", "i-saku")),
  )

  /**
   * Cleans raw text, matches against official names and aliases, and normalizes casing/spacing.
   * If a strong match is found, returns the official bank name with isConfident = true.
   * CRITICAL: Does NOT guess arbitrary text as a bank if it does not match known dictionary aliases.
   */
  fun matchBank(rawText: String): BankMatchResult {
    if (rawText.isBlank()) {
      return BankMatchResult(officialName = "", isConfident = false, originalQuery = "")
    }

    val rawLower = rawText.trim().lowercase(Locale.ROOT)
    val cleaned = cleanBankText(rawText)
    val lower = cleaned.lowercase(Locale.ROOT)

    val candidates = listOf(rawLower, lower).distinct().filter { it.isNotBlank() }

    for (cand in candidates) {
      val deSpaced = if (cand.split(" ").all { it.length <= 1 }) cand.replace(" ", "") else cand.replace(Regex("\\s+"), " ")

      // 1. Exact alias match (highest confidence)
      for (bank in ALL_BANKS) {
        if (bank.aliases.any { it.equals(cand, ignoreCase = true) || it.equals(deSpaced, ignoreCase = true) }) {
          return BankMatchResult(officialName = bank.officialName, isConfident = true, originalQuery = rawText)
        }
      }

      // 2. Substring match with sorted aliases (longer phrases first, e.g. "bca syariah" before "bca")
      val allAliasesSorted = ALL_BANKS.flatMap { b -> b.aliases.map { alias -> Pair(alias, b) } }
        .sortedByDescending { it.first.length }

      for ((alias, bank) in allAliasesSorted) {
        val wordRegex = Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE)
        if (wordRegex.containsMatchIn(cand) || wordRegex.containsMatchIn(deSpaced)) {
          return BankMatchResult(officialName = bank.officialName, isConfident = true, originalQuery = rawText)
        }
        if (alias.length >= 5 && (cand.contains(alias) || deSpaced.contains(alias))) {
          return BankMatchResult(officialName = bank.officialName, isConfident = true, originalQuery = rawText)
        }
      }
    }

    // No strong dictionary match found: Do NOT guess arbitrary text as bank
    return BankMatchResult(
      officialName = "",
      isConfident = false,
      originalQuery = rawText
    )
  }

  fun cleanBankText(text: String): String {
    return text.replace(Regex("(?i)^(bank\\s*tujuan|ke\\s*bank|tujuan\\s*transfer|metode\\s*transfer|nama\\s*bank|bank\\s*penerima|transfer\\s*ke|ke)[:\\-\\s]+"), "")
      .replace(Regex("(?i)\\b(pt|tbk|persero)\\b"), "")
      .replace(Regex("[^a-zA-Z0-9\\s.'\\-()]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()
  }
}
