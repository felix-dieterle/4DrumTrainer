package com.drumtrainer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.drumtrainer.model.LessonProgress
import com.drumtrainer.model.Student
import java.time.LocalDate

/**
 * SQLite database helper that manages two tables:
 *
 * * **students** – student profiles (name, birth date, current level).
 * * **progress** – one row per lesson attempt.
 *
 * Intentionally kept lightweight to avoid requiring Room annotation processing
 * at this stage of the project; the schema can be migrated to Room entities later.
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_STUDENTS)
        db.execSQL(CREATE_PROGRESS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_STUDENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROGRESS")
        onCreate(db)
    }

    // ── Student CRUD ──────────────────────────────────────────────────────────

    /** Inserts a new student and returns the row ID. */
    fun insertStudent(student: Student): Long {
        val cv = ContentValues().apply {
            put(COL_S_NAME,          student.name)
            put(COL_S_BIRTH_DATE,    student.birthDate.toString())
            put(COL_S_CURRENT_LEVEL, student.currentLevel)
            put(COL_S_CREATED_AT,    student.createdAt.toString())
        }
        return writableDatabase.insert(TABLE_STUDENTS, null, cv)
    }

    /** Returns all stored students, ordered by name. */
    fun getAllStudents(): List<Student> {
        val students = mutableListOf<Student>()
        val cursor = readableDatabase.query(
            TABLE_STUDENTS, null, null, null, null, null, "$COL_S_NAME ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                students.add(
                    Student(
                        id           = it.getLong(it.getColumnIndexOrThrow(COL_S_ID)),
                        name         = it.getString(it.getColumnIndexOrThrow(COL_S_NAME)),
                        birthDate    = LocalDate.parse(it.getString(it.getColumnIndexOrThrow(COL_S_BIRTH_DATE))),
                        currentLevel = it.getInt(it.getColumnIndexOrThrow(COL_S_CURRENT_LEVEL)),
                        createdAt    = LocalDate.parse(it.getString(it.getColumnIndexOrThrow(COL_S_CREATED_AT)))
                    )
                )
            }
        }
        return students
    }

    /** Updates the student's current curriculum level. */
    fun updateStudentLevel(studentId: Long, newLevel: Int) {
        val cv = ContentValues().apply { put(COL_S_CURRENT_LEVEL, newLevel) }
        writableDatabase.update(TABLE_STUDENTS, cv, "$COL_S_ID = ?", arrayOf(studentId.toString()))
    }

    // ── Progress CRUD ─────────────────────────────────────────────────────────

    /** Inserts a lesson-attempt record and returns the row ID. */
    fun insertProgress(progress: LessonProgress): Long {
        val cv = ContentValues().apply {
            put(COL_P_STUDENT_ID,   progress.studentId)
            put(COL_P_LESSON_ID,    progress.lessonId)
            put(COL_P_DATE,         progress.attemptDate.toString())
            put(COL_P_RHYTHM_SCORE, progress.rhythmScore)
            put(COL_P_PITCH_SCORE,  progress.pitchScore)
            put(COL_P_OVERALL,      progress.overallScore)
            put(COL_P_DURATION,     progress.durationSec)
            put(COL_P_PASSED,       if (progress.passed) 1 else 0)
        }
        return writableDatabase.insert(TABLE_PROGRESS, null, cv)
    }

    /** Returns the IDs of all lessons passed by [studentId]. */
    fun getPassedLessonIds(studentId: Long): Set<Long> {
        val ids = mutableSetOf<Long>()
        val cursor = readableDatabase.query(
            TABLE_PROGRESS,
            arrayOf(COL_P_LESSON_ID),
            "$COL_P_STUDENT_ID = ? AND $COL_P_PASSED = 1",
            arrayOf(studentId.toString()),
            COL_P_LESSON_ID, null, null
        )
        cursor.use { while (it.moveToNext()) ids.add(it.getLong(0)) }
        return ids
    }

    /** Returns all attempts for a specific lesson by a specific student. */
    fun getAttemptsForLesson(studentId: Long, lessonId: Long): List<LessonProgress> {
        val list = mutableListOf<LessonProgress>()
        val cursor = readableDatabase.query(
            TABLE_PROGRESS, null,
            "$COL_P_STUDENT_ID = ? AND $COL_P_LESSON_ID = ?",
            arrayOf(studentId.toString(), lessonId.toString()),
            null, null, "$COL_P_DATE ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val rhythmScore = it.getInt(it.getColumnIndexOrThrow(COL_P_RHYTHM_SCORE))
                val pitchScore  = it.getInt(it.getColumnIndexOrThrow(COL_P_PITCH_SCORE))
                list.add(
                    LessonProgress(
                        id           = it.getLong(it.getColumnIndexOrThrow(COL_P_ID)),
                        studentId    = it.getLong(it.getColumnIndexOrThrow(COL_P_STUDENT_ID)),
                        lessonId     = it.getLong(it.getColumnIndexOrThrow(COL_P_LESSON_ID)),
                        attemptDate  = LocalDate.parse(it.getString(it.getColumnIndexOrThrow(COL_P_DATE))),
                        rhythmScore  = rhythmScore,
                        pitchScore   = pitchScore,
                        overallScore = it.getInt(it.getColumnIndexOrThrow(COL_P_OVERALL)),
                        durationSec  = it.getInt(it.getColumnIndexOrThrow(COL_P_DURATION)),
                        passed       = it.getInt(it.getColumnIndexOrThrow(COL_P_PASSED)) == 1
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val DATABASE_NAME    = "drumtrainer.db"
        private const val DATABASE_VERSION = 1

        // students table
        private const val TABLE_STUDENTS      = "students"
        private const val COL_S_ID            = "_id"
        private const val COL_S_NAME          = "name"
        private const val COL_S_BIRTH_DATE    = "birth_date"
        private const val COL_S_CURRENT_LEVEL = "current_level"
        private const val COL_S_CREATED_AT    = "created_at"

        // progress table
        private const val TABLE_PROGRESS      = "progress"
        private const val COL_P_ID            = "_id"
        private const val COL_P_STUDENT_ID    = "student_id"
        private const val COL_P_LESSON_ID     = "lesson_id"
        private const val COL_P_DATE          = "attempt_date"
        private const val COL_P_RHYTHM_SCORE  = "rhythm_score"
        private const val COL_P_PITCH_SCORE   = "pitch_score"
        private const val COL_P_OVERALL       = "overall_score"
        private const val COL_P_DURATION      = "duration_sec"
        private const val COL_P_PASSED        = "passed"

        private const val CREATE_STUDENTS = """
            CREATE TABLE $TABLE_STUDENTS (
                $COL_S_ID            INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_S_NAME          TEXT    NOT NULL,
                $COL_S_BIRTH_DATE    TEXT    NOT NULL,
                $COL_S_CURRENT_LEVEL INTEGER NOT NULL DEFAULT 0,
                $COL_S_CREATED_AT    TEXT    NOT NULL
            )
        """

        private const val CREATE_PROGRESS = """
            CREATE TABLE $TABLE_PROGRESS (
                $COL_P_ID            INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_P_STUDENT_ID    INTEGER NOT NULL,
                $COL_P_LESSON_ID     INTEGER NOT NULL,
                $COL_P_DATE          TEXT    NOT NULL,
                $COL_P_RHYTHM_SCORE  INTEGER NOT NULL,
                $COL_P_PITCH_SCORE   INTEGER NOT NULL,
                $COL_P_OVERALL       INTEGER NOT NULL,
                $COL_P_DURATION      INTEGER NOT NULL,
                $COL_P_PASSED        INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY ($COL_P_STUDENT_ID) REFERENCES $TABLE_STUDENTS($COL_S_ID)
            )
        """
    }
}
