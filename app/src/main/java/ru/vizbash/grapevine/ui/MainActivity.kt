package ru.vizbash.grapevine.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.*
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityMainBinding
import ru.vizbash.grapevine.databinding.DrawerHeaderBinding

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
        val photo = model.currentProfile.base.photo
        if (photo != null) {
            header.ivPhoto.setImageBitmap(photo)
        }
        header.tvUsername.text = model.currentProfile.base.username
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.navHostFragment)
        return navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
    }
}