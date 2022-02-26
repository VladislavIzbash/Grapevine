package ru.vizbash.grapevine.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import ru.vizbash.grapevine.GrapevineService
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityMainBinding
import ru.vizbash.grapevine.databinding.DrawerHeaderBinding
import ru.vizbash.grapevine.network.bluetooth.BluetoothService

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfig: AppBarConfiguration

    private val model: MainViewModel by viewModels()

    @Navigator.Name("logout")
    private inner class LogoutNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)

        override fun navigate(
            destination: NavDestination,
            args: Bundle?,
            navOptions: NavOptions?,
            navigatorExtras: Extras?,
        ): NavDestination? {
            finish()
            stopService(Intent(this@MainActivity, BluetoothService::class.java))
            stopService(Intent(this@MainActivity, GrapevineService::class.java))
            model.disableAutologin()
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        setSupportActionBar(ui.toolbar)

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

        val photo = model.currentProfile.entity.photo
        if (photo != null) {
            header.ivPhoto.setImageBitmap(photo)
        }
        header.tvUsername.text = model.currentProfile.entity.username

        ViewCompat.setOnApplyWindowInsetsListener(headerView) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }

        startBluetooth()
        model.startGrapevineNetwork()
        startService(Intent(this, GrapevineService::class.java))

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
                    startService(Intent(this, BluetoothService::class.java))
                }
            }.launch(permission)
        } else {
            startService(Intent(this, BluetoothService::class.java))
        }
    }
}