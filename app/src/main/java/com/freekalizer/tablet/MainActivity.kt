package com.freekalizer.tablet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.freekalizer.tablet.audio.AndroidAudioEngineController
import com.freekalizer.tablet.audio.AudioDeviceRepository
import com.freekalizer.ui.Dfx69TabletUiBlueprint

class MainActivity : AppCompatActivity() {
    private lateinit var repo: AudioDeviceRepository
    private val engineController = AndroidAudioEngineController(this)

    private lateinit var permissionStatus: TextView
    private lateinit var inputSpinner: Spinner
    private lateinit var outputSpinner: Spinner
    private lateinit var routeStatus: TextView

    private var inputItems: List<Pair<String, Int?>> = emptyList()
    private var outputItems: List<Pair<String, Int?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repo = AudioDeviceRepository(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            val zoneCount = Dfx69TabletUiBlueprint.mvp.zones.size
            text = "Freekalizer Tablet\nUI blueprint zones: $zoneCount"
            textSize = 20f
        }

        permissionStatus = TextView(this)
        val requestPerm = Button(this).apply {
            text = "Grant Mic Permission (RECORD_AUDIO)"
            setOnClickListener { requestMicPermission() }
        }

        inputSpinner = Spinner(this)
        outputSpinner = Spinner(this)

        routeStatus = TextView(this)
        val start = Button(this).apply {
            text = "Start Monitoring"
            setOnClickListener {
                if (!isMicPermissionGranted()) {
                    requestMicPermission()
                    return@setOnClickListener
                }
                engineController.setPreferredDevices(
                    repo.selectedInputId,
                    repo.selectedOutputId
                )
                engineController.startMonitoring()
            }
        }
        val stop = Button(this).apply {
            text = "Stop"
            setOnClickListener { engineController.stopMonitoring() }
        }

        container.addView(header)
        container.addView(permissionStatus)
        container.addView(requestPerm)

        container.addView(TextView(this).apply { text = "Input Device" })
        container.addView(inputSpinner)
        container.addView(TextView(this).apply { text = "Output Device" })
        container.addView(outputSpinner)
        container.addView(routeStatus)
        container.addView(start)
        container.addView(stop)

        setContentView(container)

        updatePermissionUi()
        bindSpinners()
    }

    override fun onStart() {
        super.onStart()
        repo.start { _ ->
            updateDeviceUi()
            syncEngineControllerToSelection(rebind = true)
        }
    }

    override fun onStop() {
        super.onStop()
        repo.stop()
    }

    private fun bindSpinners() {
        inputSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                repo.setSelectedInput(inputItems.getOrNull(position)?.second)
                updateRouteStatus()
                syncEngineControllerToSelection(rebind = true)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        outputSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                repo.setSelectedOutput(outputItems.getOrNull(position)?.second)
                updateRouteStatus()
                syncEngineControllerToSelection(rebind = true)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun updateDeviceUi() {
        val devices = repo.snapshotDevices()

        inputItems = listOf("System default" to null) + devices
            .filter { it.isInput }
            .map { it.label to it.id }
        outputItems = listOf("System default" to null) + devices
            .filter { it.isOutput }
            .map { it.label to it.id }

        inputSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            inputItems.map { it.first }
        )
        outputSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            outputItems.map { it.first }
        )

        updateRouteStatus()
    }

    private fun updateRouteStatus() {
        routeStatus.text = buildString {
            append("Selected input id: ${repo.selectedInputId ?: "default"}\n")
            append("Selected output id: ${repo.selectedOutputId ?: "default"}\n")
            append("Device count: ${repo.snapshotDevices().size}")
        }
    }

    private fun updatePermissionUi() {
        val granted = isMicPermissionGranted()
        permissionStatus.text = "Mic permission: ${if (granted) "GRANTED" else "NOT GRANTED"}"
    }

    private fun isMicPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_MIC
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            updatePermissionUi()
        }
    }

    private fun syncEngineControllerToSelection(rebind: Boolean) {
        engineController.setPreferredDevices(repo.selectedInputId, repo.selectedOutputId)
        if (rebind) engineController.rebindIfRunning()
        updateRouteStatus()
    }

    companion object {
        private const val REQ_MIC = 1001
    }
}
