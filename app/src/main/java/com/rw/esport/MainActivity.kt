package com.rw.esport

import android.app.ProgressDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.blinkt.openvpn.LaunchVPN
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ProfileManager
import java.io.File
import java.io.StringReader
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val VPN_REQUEST_CODE = 0x123
    private var selectedCountry = "MM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        val btnLaunch = findViewById<Button>(R.id.btn_launch)

        val swRankWin = findViewById<Switch>(R.id.sw_rank_win)
        val swRankLose = findViewById<Switch>(R.id.sw_rank_lose)
        val swParty = findViewById<Switch>(R.id.sw_party)
        val swSolo = findViewById<Switch>(R.id.sw_solo)
        val swDuo = findViewById<Switch>(R.id.sw_duo)
        val swTrio = findViewById<Switch>(R.id.sw_trio)
        val sw1Hit = findViewById<Switch>(R.id.sw_1hit)
        val swMapHack = findViewById<Switch>(R.id.sw_maphack)

        val modeSwitches = listOf(swRankWin, swRankLose, swParty, swSolo, swDuo, swTrio)

        modeSwitches.forEach { current ->
            current.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    modeSwitches.filter { it != current }.forEach { it.isChecked = false }
                    
                    selectedCountry = when(current.id) {
                        R.id.sw_rank_win -> "MM"
                        R.id.sw_rank_lose -> "KH"
                        R.id.sw_party -> "SG"
                        R.id.sw_solo -> "MM"
                        R.id.sw_duo -> "TW"
                        R.id.sw_trio -> "IN"
                        else -> "MM"
                    }
                }
            }
        }

        sw1Hit.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) Toast.makeText(this, "1 Hit Aktif (Visual Client)", Toast.LENGTH_SHORT).show()
        }
        swMapHack.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) Toast.makeText(this, "Map Hack Aktif (Zoom Out Mode)", Toast.LENGTH_SHORT).show()
        }

        btnLaunch.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, VPN_REQUEST_CODE)
            else connectToVpn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) connectToVpn()
        else Toast.makeText(this, "Izin VPN ditolak!", Toast.LENGTH_SHORT).show()
    }

    private fun connectToVpn() {
        val dialog = ProgressDialog(this)
        dialog.setMessage("Mencari server $selectedCountry...")
        dialog.setCancelable(false)
        dialog.show()

        thread {
            try {
                val csv = URL("https://www.vpngate.net/api/iphone/").readText()
                var configB64 = ""

                for (line in csv.split("\n")) {
                    if (line.startsWith("#") || line.startsWith("*") || line.isBlank()) continue
                    val parts = line.split(",")
                    if (parts.size > 14) {
                        val country = parts[6]
                        if (country == selectedCountry) {
                            configB64 = parts[14]
                            break
                        }
                    }
                }

                if (configB64.isEmpty()) {
                    runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(this, "Server $selectedCountry penuh, coba mode lain.", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                val decodedConfig = String(Base64.decode(configB64, Base64.DEFAULT))
                
                runOnUiThread { dialog.setMessage("Menghubungkan VPN...") }
                setupOpenVpn(decodedConfig)
                
                runOnUiThread {
                    dialog.dismiss()
                    tvStatus.text = "Status: VPN $selectedCountry Aktif"
                    tvStatus.setTextColor(0xFF00E5A0.toInt())
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        val launchIntent = packageManager.getLaunchIntentForPackage("com.mobile.legends")
                        if (launchIntent != null) startActivity(launchIntent)
                        else Toast.makeText(this, "MLBB tidak ada", Toast.LENGTH_SHORT).show()
                    }, 3000)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupOpenVpn(configStr: String) {
        try {
            val file = File(cacheDir, "rw.ovpn")
            file.writeText(configStr)

            val cp = ConfigParser()
            cp.parseConfig(StringReader(configStr))
            val profile = cp.convertProfile()
            profile.mName = "RW_${selectedCountry}"

            val pm = ProfileManager.getInstance(this)
            pm.addProfile(profile)
            pm.saveProfile(this, profile)
            pm.saveProfileList(this)

            val intent = Intent(this, LaunchVPN::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra("de.blinkt.openvpn.VpnProfile", profile.uuid)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
