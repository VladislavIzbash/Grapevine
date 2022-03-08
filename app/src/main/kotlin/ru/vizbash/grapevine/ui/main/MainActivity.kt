package ru.vizbash.grapevine.ui.main

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityMainBinding
import ru.vizbash.grapevine.databinding.DrawerHeaderBinding
import ru.vizbash.grapevine.service.ForegroundService
import ru.vizbash.grapevine.ui.login.LoginActivity

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ServiceConnection {
    private lateinit var appBarConfig: AppBarConfiguration

    private lateinit var ui: ActivityMainBinding
    private val model: MainViewModel by viewModels()

    private lateinit var wifiSwitch: SwitchMaterial
    private lateinit var bluetoothSwitch: SwitchMaterial

    private lateinit var serviceBinder: ForegroundService.ServiceBinder

    private lateinit var requestLocation: ActivityResultLauncher<Array<String>>

    @Navigator.Name("logout")
    private inner class LogoutNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)

        override fun navigate(
            destination: NavDestination,
            args: Bundle?,
            navOptions: NavOptions?,
            navigatorExtras: Extras?,
        ): NavDestination? {
            unbindService(this@MainActivity)
            stopService(Intent(this@MainActivity, ForegroundService::class.java))

            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish()
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        setSupportActionBar(ui.toolbar)

        requestLocation = registerForActivityResult(RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION]!!) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (granted[Manifest.permission.ACCESS_BACKGROUND_LOCATION]!!) {
                        serviceBinder.setWifiUserEnabled(true)
                    }
                } else {
                    serviceBinder.setWifiUserEnabled(true)
                }
            }
        }

        wifiSwitch = ui.navView.menu.findItem(R.id.wifiMenuItem).actionView as SwitchMaterial
        bluetoothSwitch = ui.navView.menu.findItem(R.id.bluetoothMenuItem).actionView as SwitchMaterial

        val intent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, this, BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        serviceBinder = (service as ForegroundService.ServiceBinder)
        model.service = serviceBinder.grapevineService

        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController
        navController.navigatorProvider.addNavigator(LogoutNavigator())
        navHost.navController.setGraph(R.navigation.main_nav)

        ui.navView.setupWithNavController(navController)

        val topLevel = setOf(
            R.id.contactsFragment,
            R.id.chatsFragment,
            R.id.peopleAroundFragment,
        )
        appBarConfig = AppBarConfiguration(topLevel, ui.drawerLayout)
        ui.toolbar.setupWithNavController(navController, appBarConfig)

        // Иначе в начале отображается имя приложения
        supportActionBar?.title = navController.currentBackStackEntry?.destination?.label

        val headerView = ui.navView.getHeaderView(0)
        val header = DrawerHeaderBinding.bind(headerView)

        val photo = model.service.currentProfile.photo
        if (photo != null) {
            header.ivPhoto.setImageBitmap(photo)
        }
        header.tvUsername.text = model.service.currentProfile.username

        // Для позиционирования под статусбаром
        ViewCompat.setOnApplyWindowInsetsListener(headerView) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.networkError.filterNotNull().collect {
                        Snackbar.make(ui.root, R.string.error_network, Snackbar.LENGTH_LONG).apply {
                            setTextColor(getColor(R.color.error))
                        }.show()
                    }
                }
                launch {
                    serviceBinder.bluetoothEnabled
                        .combine(serviceBinder.bluetoothHardwareEnabled, ::Pair)
                        .collect { (enabled, hwEnabled) ->
                            bluetoothSwitch.isChecked = enabled
                            bluetoothSwitch.isEnabled = hwEnabled
                        }
                }
                launch {
                    serviceBinder.wifiEnabled
                        .combine(serviceBinder.wifiHardwareEnabled, ::Pair)
                        .collect { (enabled, hwEnabled) ->
                            wifiSwitch.isChecked = enabled
                            wifiSwitch.isEnabled = hwEnabled
                        }
                }
            }
        }

        bluetoothSwitch.setOnClickListener {
            if (bluetoothSwitch.isChecked) {
                startBluetooth()
            } else {
                serviceBinder.setBluetoothUserEnabled(false)
            }
        }
        wifiSwitch.setOnClickListener {
            if (wifiSwitch.isChecked) {
                startBluetooth()
            } else {
                serviceBinder.setWifiUserEnabled(false)
            }
        }

        if (bluetoothSwitch.isChecked) {
            startBluetooth()
        }
        if (wifiSwitch.isChecked) {
            startWifi()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {}

    private fun startBluetooth() {
        serviceBinder.setBluetoothUserEnabled(true)
        // TODO: other persmissiopns
    }

    private fun startWifi() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            serviceBinder.setWifiUserEnabled(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestLocation.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ))
        } else {
            requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
}