package com.example.evinspector

import android.app.Application
import androidx.room.Room
import com.example.evinspector.data.AppDatabase
import com.example.evinspector.data.ChargerConfigStore
import com.example.evinspector.data.InspectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class InspectorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Hand-rolled dependency container; small enough that a DI framework would be overkill. */
class AppContainer(app: Application) {

    /**
     * Long-lived scope for writes that must finish even if the screen that triggered them closes
     * (auto-save on the way out, photo registration, Save & Return). It runs on a single thread so
     * writes start in the order they were issued, which keeps "last edit wins" true.
     */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { r -> Thread(r, "inspector-writes") }.asCoroutineDispatcher(),
    )

    // Plain Room in app-private storage. See README for how to swap in SQLCipher if policy requires it.
    private val database: AppDatabase =
        Room.databaseBuilder(app, AppDatabase::class.java, "inspector.db").build()

    val repository: InspectionRepository = InspectionRepository(app, database, appScope)
    val configStore: ChargerConfigStore = ChargerConfigStore(app)
}
