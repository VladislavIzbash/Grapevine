package ru.vizbash.grapevine.ui.main

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.*
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
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
            model.disableAutologin()
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

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
        setupActionBarWithNavController(navController, appBarConfig)

        val header = DrawerHeaderBinding.bind(ui.navView.getHeaderView(0))
        val photo = model.currentProfile.entity.photo
        if (photo != null) {
            header.ivPhoto.setImageBitmap(photo)
        }
        header.tvUsername.text = model.currentProfile.entity.username

        startBluetooth()
        startService(Intent(this, GrapevineService::class.java))
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.navHostFragment)
        return navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
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