package ru.vizbash.grapevine.ui.main

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityMainBinding
import ru.vizbash.grapevine.databinding.DrawerHeaderBinding
import ru.vizbash.grapevine.service.ForegroundService
import ru.vizbash.grapevine.ui.login.LoginActivity

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfig: AppBarConfiguration

    private lateinit var ui: ActivityMainBinding
    private val model: MainViewModel by viewModels()

    private lateinit var serviceBinder: ForegroundService.GrapevineBinder

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = (binder as ForegroundService.GrapevineBinder)
            model.service = serviceBinder.grapevineService
            onBound()
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    @Navigator.Name("logout")
    private inner class LogoutNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)

        override fun navigate(
            destination: NavDestination,
            args: Bundle?,
            navOptions: NavOptions?,
            navigatorExtras: Extras?,
        ): NavDestination? {
            unbindService(serviceConnection)
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

        val intent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun onBound() {
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

        ViewCompat.setOnApplyWindowInsetsListener(headerView) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }

        startBluetooth()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.networkError.filterNotNull().collect {
                    Snackbar.make(ui.root, R.string.error_network, Snackbar.LENGTH_LONG).apply {
                        setTextColor(getColor(R.color.error))
                    }.show()
                }
            }
        }
    }

    private fun startBluetooth() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }

        if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
            registerForActivityResult(RequestPermission()) { granted ->
                if (granted) {
                    serviceBinder.setBluetoothEnabled(true)
                }
            }.launch(permission)
        } else {
            serviceBinder.setBluetoothEnabled(true)
        }
    }
}