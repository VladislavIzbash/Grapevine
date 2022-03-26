package ru.vizbash.grapevine.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityMainBinding
import ru.vizbash.grapevine.databinding.DrawerHeaderBinding
import ru.vizbash.grapevine.service.foreground.ForegroundService
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_ENABLE_TRANSPORT
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_GET_TRANSPORT_STATE
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_TRANSPORT_HARDWARE_STATE_CHANGED
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_TRANSPORT_STATE_CHANGED
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.EXTRA_STATE
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.EXTRA_TRANSPORT_TYPE
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.TRANSPORT_BLUETOOTH
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.TRANSPORT_WIFI

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding
    private val model: MainViewModel by viewModels()

    private val locationHelper = LocationHelper(this)

    private lateinit var wifiSwitch: SwitchMaterial
    private lateinit var bluetoothSwitch: SwitchMaterial

    private val transportStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TRANSPORT_STATE_CHANGED -> {
                    val state = intent.getBooleanExtra(EXTRA_STATE, false)
                    when (intent.getIntExtra(EXTRA_TRANSPORT_TYPE, -1)) {
                        TRANSPORT_BLUETOOTH -> bluetoothSwitch.isChecked = state
                        TRANSPORT_WIFI -> wifiSwitch.isChecked = state
                    }
                }
                ACTION_TRANSPORT_HARDWARE_STATE_CHANGED -> {
                    val state = intent.getBooleanExtra(EXTRA_STATE, false)
                    when (intent.getIntExtra(EXTRA_TRANSPORT_TYPE, -1)) {
                        TRANSPORT_BLUETOOTH -> bluetoothSwitch.isEnabled = state
                        TRANSPORT_WIFI -> wifiSwitch.isEnabled = state
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        locationHelper.register {
            Snackbar.make(
                ui.root,
                R.string.error_need_location_permission,
                Snackbar.LENGTH_SHORT,
            ).show()
        }

        val navController = setupNavigation()

        initDrawerHeader()
        initDrawerMenu(navController)
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter().apply {
            addAction(ACTION_TRANSPORT_STATE_CHANGED)
            addAction(ACTION_TRANSPORT_HARDWARE_STATE_CHANGED)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(transportStateReceiver, filter)

        val intent = Intent(this, ForegroundService::class.java).apply {
            action = ACTION_GET_TRANSPORT_STATE
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onStop() {
        super.onStop()

        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(transportStateReceiver)
    }

    private fun initDrawerHeader() {
        val headerView = ui.navView.getHeaderView(0)
        val headerUi = DrawerHeaderBinding.bind(headerView)

        ViewCompat.setOnApplyWindowInsetsListener(headerView) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }

        headerUi.username.text = model.profile.username
        model.profile.photo?.let {
            headerUi.userPhoto.setImageBitmap(it)
        }
    }

    private fun initDrawerMenu(navController: NavController) {
        ui.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.item_logout -> {
                    model.disableAutoLogin()
                    finish()
                    true
                }
                else -> {
                    val handled = NavigationUI.onNavDestinationSelected(item, navController)
                    if (handled) {
                        ui.root.closeDrawer(ui.navView)
                    }
                    handled
                }
            }
        }

        wifiSwitch = ui.navView.menu.findItem(R.id.item_wifi_switch).actionView
                as SwitchMaterial
        bluetoothSwitch = ui.navView.menu.findItem(R.id.item_bluetooth_switch).actionView
                as SwitchMaterial

        bluetoothSwitch.setOnClickListener {
            val intent = Intent(this, ForegroundService::class.java).apply {
                action = ACTION_ENABLE_TRANSPORT
                putExtra(EXTRA_TRANSPORT_TYPE, TRANSPORT_BLUETOOTH)
                putExtra(EXTRA_STATE, bluetoothSwitch.isChecked)
            }

            if (bluetoothSwitch.isChecked) {
                bluetoothSwitch.isChecked = false
                locationHelper.requestPermissions {
                    startService(intent)
                }
            } else {
                startService(intent)
            }
        }
    }

    private fun setupNavigation(): NavController {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                as NavHostFragment
        val navController = navHost.navController

        val topLevel = setOf(
            R.id.fragment_chat_list,
            R.id.fragment_people_around,
            R.id.fragment_global_chat,
        )
        val appBarConfig = AppBarConfiguration(topLevel, ui.root)

        ui.toolbar.setupWithNavController(navController, appBarConfig)
        ui.navView.setupWithNavController(navController)

        return navController
    }
}