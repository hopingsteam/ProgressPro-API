package com.progressp.service.student

import com.progressp.database.IDatabaseFactory
import com.progressp.models.student.Student
import com.progressp.models.student.StudentSession
import com.progressp.models.student.StudentSessionStatus
import com.progressp.models.student.StudentsSessionsTable
import com.progressp.models.user.User
import com.progressp.util.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.ArrayList
import java.util.UUID

interface IStudentSessionService {
    suspend fun userAll(token: String): ArrayList<StudentSession.Page>
    suspend fun userCreate(token: String, sessionProps: StudentSession.New): StudentSession.Response
    suspend fun userUpdate(token: String, sessionProps: StudentSession.New): StudentSession.Response
}

class StudentSessionService(private val databaseFactory: IDatabaseFactory) : IStudentSessionService {

    private fun getStudentSession(id: String) =
        StudentSession.findById(UUID.fromString(id)) ?: throw StudentSessionNotFound(id)

    override suspend fun userAll(token: String): ArrayList<StudentSession.Page> {
        val userId = getUserDataFromJWT(token, "id") as String
        val list = ArrayList<StudentSession.Page>()
        return databaseFactory.dbQuery {
            StudentSession.find {
                (StudentsSessionsTable.instructorId eq User.findById(UUID.fromString(userId))!!.id) and
                (StudentsSessionsTable.status inList listOf(
                    StudentSessionStatus.STARTED.code,
                    StudentSessionStatus.PAID.code,
                    StudentSessionStatus.CLOSED.code)
                )
            }.orderBy(StudentsSessionsTable.createdAt to SortOrder.DESC).forEach {
                    list.add(
                        StudentSession.Page.fromDbRow(it)
                    )
                }
            list
        }
    }

    override suspend fun userCreate(token: String, sessionProps: StudentSession.New): StudentSession.Response {
        val tokenUserId = getUserDataFromJWT(token, "id") as String

        if (!Preconditions(databaseFactory).checkIfCurrencyExists(sessionProps.currencyCode))
            throw CurrencyCodeNotFound(sessionProps.currencyCode)
        if (!Preconditions(databaseFactory).checkIfValueIsValid(sessionProps.value))
            throw StudentSessionTotalInvalid(sessionProps.value)
        if (!Preconditions(databaseFactory).checkIfValueIsValid(sessionProps.meetings))
            throw StudentSessionTotalInvalid(sessionProps.meetings)
        if (!Preconditions(databaseFactory).checkIfStudentExists(sessionProps.studentId))
            throw StudentNotFound(sessionProps.studentId)
        if (!Preconditions(databaseFactory).checkIfUserCanUpdateStudent(tokenUserId, sessionProps.studentId))
            throw StudentNotYours(tokenUserId, sessionProps.studentId)

        return databaseFactory.dbQuery {
            val studentSession = StudentSession.new {
                instructorId = User.findById(UUID.fromString(tokenUserId))!!.id
                studentId = Student.findById(UUID.fromString(sessionProps.studentId))!!.id
                status = StudentSessionStatus.STARTED.code
                name = sessionProps.name
                meetings = sessionProps.meetings
                value = sessionProps.value
                currencyCode = sessionProps.currencyCode
                startAt = LocalDate.parse(sessionProps.startAt)
                endAt = LocalDate.parse(sessionProps.endAt)
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
            StudentSession.Response.fromDbRow(studentSession)
        }
    }

    override suspend fun userUpdate(token: String, sessionProps: StudentSession.New): StudentSession.Response {
        val tokenUserId = getUserDataFromJWT(token, "id") as String

        if (!Preconditions(databaseFactory).checkIfCurrencyExists(sessionProps.currencyCode))
            throw CurrencyCodeNotFound(sessionProps.currencyCode)
        if (!Preconditions(databaseFactory).checkIfValueIsValid(sessionProps.value))
            throw StudentSessionTotalInvalid(sessionProps.value)
        if (!Preconditions(databaseFactory).checkIfValueIsValid(sessionProps.meetings))
            throw StudentSessionTotalInvalid(sessionProps.meetings)
        if (!Preconditions(databaseFactory).checkIfSessionStatusExists(sessionProps.status))
            throw StudentSessionStatusInvalid(tokenUserId, sessionProps.status)
        if (!Preconditions(databaseFactory).checkIfStudentExists(sessionProps.studentId))
            throw StudentNotFound(sessionProps.studentId)
        if (!Preconditions(databaseFactory).checkIfUserCanUpdateStudentSession(tokenUserId, sessionProps.id!!))
            throw StudentNotYours(tokenUserId, sessionProps.id)

        return databaseFactory.dbQuery {
            val studentSession = getStudentSession(sessionProps.id)
            studentSession.apply {
                instructorId = User.findById(UUID.fromString(tokenUserId))!!.id
                studentId = Student.findById(UUID.fromString(sessionProps.studentId))!!.id
                status = sessionProps.status
                name = sessionProps.name
                meetings = sessionProps.meetings
                value = sessionProps.value
                currencyCode = sessionProps.currencyCode
                startAt = LocalDate.parse(sessionProps.startAt)
                endAt = LocalDate.parse(sessionProps.endAt)
                updatedAt = LocalDateTime.now()
            }
            StudentSession.Response.fromDbRow(studentSession)
        }
    }
}