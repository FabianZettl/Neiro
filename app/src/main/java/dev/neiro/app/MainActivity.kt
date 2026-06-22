package dev.neiro.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.neiro.app.data.repository.ConnectRepository
import dev.neiro.app.di.ApplicationScope
import dev.neiro.app.ui.navigation.NieroNavGraph
import dev.neiro.app.ui.theme.DynamicNieroTheme
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var connectRepository: ConnectRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* accepted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        connectRepository.start(appScope)

        // POST_NOTIFICATIONS is required on API 33+ for media playback notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            DynamicNieroTheme {
                NieroNavGraph()
            }
        }
    }
}
