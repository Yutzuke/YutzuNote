package app.yutzunote

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.niedermann.owncloud.notes.persistence.NotesDatabase
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Punto de entrada de YutzuNote:
 * - observa la tabla de notas y escribe los cambios en la carpeta ~2 s después del último cambio;
 * - programa un trabajo periódico (15 min) que importa cambios externos y hace la copia diaria.
 */
object YutzuSync {
    private const val TAG = "YutzuSync"
    private const val WORK = "yutzunote-periodic"
    private const val DEBOUNCE_MS = 2000L

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null
    @Volatile private var initialized = false

    @JvmStatic
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        NotesDatabase.getInstance(app).invalidationTracker.addObserver(object : InvalidationTracker.Observer("Note") {
            override fun onInvalidated(tables: MutableSet<String>) = schedule(app, importExternal = false, delay = DEBOUNCE_MS)
        })
        schedulePeriodic(app)
        schedule(app, importExternal = true, delay = 1500)
    }

    /** Sincronización completa (importa y exporta) lo antes posible, p. ej. al abrir la app. */
    @JvmStatic
    fun requestSync(context: Context) = schedule(context.applicationContext, importExternal = true, delay = 300)

    @Synchronized
    private fun schedule(app: Context, importExternal: Boolean, delay: Long) {
        if (!YutzuPrefs.isEnabled(app)) return
        wantImport = wantImport || importExternal
        pending?.cancel(false)
        pending = executor.schedule({
            val doImport = synchronized(this) { wantImport.also { wantImport = false } }
            runCatching { FolderSync.sync(app, doImport) }.onFailure { Log.e(TAG, "sync", it) }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private var wantImport = false

    /** Ejecuta en segundo plano y devuelve el resultado al hilo principal. */
    fun <T> runAsync(context: Context, block: () -> T, done: (Result<T>) -> Unit) {
        val main = android.os.Handler(context.mainLooper)
        executor.execute {
            val r = runCatching(block)
            main.post { done(r) }
        }
    }

    @JvmStatic
    fun schedulePeriodic(app: Context) {
        val req = PeriodicWorkRequest.Builder(PeriodicWorker::class.java, 15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    class PeriodicWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
        override fun doWork(): Result {
            val app = applicationContext
            if (YutzuPrefs.isEnabled(app)) FolderSync.sync(app, importExternal = true)
            YutzuBackup.autoBackupIfDue(app)
            return Result.success()
        }
    }
}
