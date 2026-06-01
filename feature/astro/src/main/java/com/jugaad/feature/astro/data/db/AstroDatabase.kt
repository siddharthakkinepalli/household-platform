package com.jugaad.feature.astro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jugaad.core.security.keystore.AstroKeyProvider
import com.jugaad.feature.astro.data.db.converter.AstroTypeConverters
import com.jugaad.feature.astro.data.db.dao.DailyTransitDao
import com.jugaad.feature.astro.data.db.dao.FeedbackDao
import com.jugaad.feature.astro.data.db.dao.UserProfileDao
import com.jugaad.feature.astro.data.db.entity.ClosedLoopFeedbackEntity
import com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity
import com.jugaad.feature.astro.data.db.entity.UserProfileEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        UserProfileEntity::class,
        DailyTransitCacheEntity::class,
        ClosedLoopFeedbackEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AstroTypeConverters::class)
abstract class AstroDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyTransitDao(): DailyTransitDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        private const val DB_NAME = "astro_encrypted.db"

        /**
         * Builds a SQLCipher-encrypted Room database.
         *
         * The passphrase is acquired from [AstroKeyProvider] (Keystore-backed) and
         * zeroed immediately after being consumed by [SupportFactory]. This ensures
         * the plaintext passphrase has the minimum possible lifetime in heap memory.
         *
         * Called exclusively from [com.jugaad.feature.astro.di.AstroDatabaseModule].
         * Do not call directly — use the Hilt-injected [AstroDatabase] singleton.
         */
        internal fun build(context: Context, keyProvider: AstroKeyProvider): AstroDatabase {
            // Load SQLCipher native library before Room initialization
            System.loadLibrary("sqlcipher")

            val passphrase: ByteArray = keyProvider.acquirePassphrase()
            val factory = SupportFactory(passphrase)
            passphrase.fill(0)  // Zero-fill: passphrase must not persist in heap

            return Room.databaseBuilder(
                context.applicationContext,
                AstroDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
