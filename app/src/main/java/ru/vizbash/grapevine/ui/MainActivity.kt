package ru.vizbash.grapevine.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import ru.vizbash.grapevine.GrapevineService
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as GrapevineService.GrapevineBinder).getService()
            service.bluetoothService.start();
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)

        ui.contactList.layoutManager = LinearLayoutManager(this)
        ui.contactList.adapter = ConcatAdapter(
            SectionAdapter(getString(R.string.contacts)),
            ContactAdapter { openChat() },
            SectionAdapter(getString(R.string.nodes)),
            NeighborAdapter(),
        )
        val contactDecoration = DividerItemDecoration(ui.contactList.context, DividerItemDecoration.VERTICAL)
        ui.contactList.addItemDecoration(contactDecoration)

        val intent = Intent(this, GrapevineService::class.java)
        startService(intent)
        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)

        setContentView(ui.root)
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.appbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            Toast.makeText(this, R.string.settings, Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}