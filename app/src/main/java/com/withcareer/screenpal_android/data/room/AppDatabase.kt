package com.withcareer.screenpal_android.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InstructionSetEntity::class,
        InstructionStepEntity::class,
        UserSkillEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun instructionDao(): InstructionDao
    abstract fun userSkillDao(): UserSkillDao

    companion object {
        /**
         * 数据库迁移：版本 6 -> 版本 7
         * 为 instruction_sets 表新增 totalDuration 字段
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `instruction_sets` ADD COLUMN `totalDuration` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 数据库迁移：版本 1 -> 版本 2
         * 新增 user_skills 表
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_skills` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `targetApps` TEXT NOT NULL,
                        `instruction` TEXT NOT NULL,
                        `allowedTools` TEXT NOT NULL DEFAULT '[]',
                        `version` TEXT NOT NULL DEFAULT '1.0.0',
                        `author` TEXT NOT NULL DEFAULT '我',
                        `tags` TEXT NOT NULL DEFAULT '[]',
                        `isPublished` INTEGER NOT NULL DEFAULT 0,
                        `remoteId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * 数据库迁移：版本 2 -> 版本 3
         * 新增 targetAppNames 字段，用于存储包名到应用名的映射
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `user_skills` ADD COLUMN `targetAppNames` TEXT"
                )
            }
        }

        /**
         * 数据库迁移：版本 3 -> 版本 4
         * 新增 isImported 字段，用于标记是否从云端导入
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `user_skills` ADD COLUMN `isImported` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 数据库迁移：版本 4 -> 版本 5
         * 将 version 字段从 TEXT 改为 INTEGER
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite 不支持直接修改列类型，需要重建表
                // 1. 创建新表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_skills_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `targetApps` TEXT NOT NULL,
                        `targetAppNames` TEXT,
                        `instruction` TEXT NOT NULL,
                        `allowedTools` TEXT NOT NULL DEFAULT '[]',
                        `version` INTEGER NOT NULL DEFAULT 1,
                        `author` TEXT NOT NULL DEFAULT '我',
                        `tags` TEXT NOT NULL DEFAULT '[]',
                        `isPublished` INTEGER NOT NULL DEFAULT 0,
                        `remoteId` TEXT,
                        `isImported` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 2. 复制数据，转换 version 字段（从字符串提取主版本号，如 "1.0.0" -> 1）
                // 提取版本号字符串中第一个数字部分（到第一个点之前），如果转换失败则使用默认值 1
                database.execSQL(
                    """
                    INSERT INTO `user_skills_new` (
                        `id`, `name`, `description`, `targetApps`, `targetAppNames`,
                        `instruction`, `allowedTools`, `version`, `author`, `tags`,
                        `isPublished`, `remoteId`, `isImported`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`, `name`, `description`, `targetApps`, `targetAppNames`,
                        `instruction`, `allowedTools`,
                        COALESCE(
                            CAST(
                                CASE
                                    WHEN INSTR(`version`, '.') > 0
                                    THEN SUBSTR(`version`, 1, INSTR(`version`, '.') - 1)
                                    ELSE `version`
                                END AS INTEGER
                            ),
                            1
                        ),
                        `author`, `tags`,
                        `isPublished`, `remoteId`, `isImported`, `createdAt`, `updatedAt`
                    FROM `user_skills`
                    """.trimIndent()
                )

                // 3. 删除旧表
                database.execSQL("DROP TABLE `user_skills`")

                // 4. 重命名新表
                database.execSQL("ALTER TABLE `user_skills_new` RENAME TO `user_skills`")
            }
        }

        /**
         * 数据库迁移：版本 5 -> 版本 6
         * 为 instruction_steps 表新增 timestamp 和 delayBefore 字段
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `instruction_steps` ADD COLUMN `timestamp` INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE `instruction_steps` ADD COLUMN `delayBefore` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 数据库迁移：版本 7 -> 版本 8
         * 为 user_skills 表新增 instructionSets 字段，用于存储技能内嵌的按键集
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `user_skills` ADD COLUMN `instructionSets` TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `user_skills` ADD COLUMN `reviewStatus` TEXT")
                database.execSQL("ALTER TABLE `user_skills` ADD COLUMN `reviewId` TEXT")
                database.execSQL("ALTER TABLE `user_skills` ADD COLUMN `reviewMessage` TEXT")
                database.execSQL("ALTER TABLE `user_skills` ADD COLUMN `reviewUpdatedAt` INTEGER")
            }
        }
    }
}
