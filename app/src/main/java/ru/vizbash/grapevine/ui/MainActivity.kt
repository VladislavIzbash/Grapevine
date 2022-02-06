package ru.vizbash.grapevine.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import ru.vizbash.grapevine.GrapevineService

class MainActivity : AppCompatActivity() {
//    private lateinit var ui: ActivityMainBinding

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as GrapevineService.GrapevineBinder).getService()
            service.bluetoothService.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        ui = ActivityMainBinding.inflate(layoutInflater)

        val intent = Intent(this, GrapevineService::class.java)
        startService(intent)
        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)

//        setContentView(ui.root)
    }

//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.appbar, menu)
//        return true
//    }
}