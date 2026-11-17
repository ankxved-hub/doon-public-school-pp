package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SchoolUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SchoolRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveUser_andUpdateProfile_updatesPhoneAndPasswordInUsersCollection() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SchoolRepository(firestore)
        val newUser = SchoolUser(
            ownerUid = uid,
            role = "TEACHER",
            userId = "TCH-${UUID.randomUUID().toString().take(6)}",
            password = "initialPass",
            fullName = "Mrs. Sunita Singh",
            phone = "9837000001",
            wing = "Middle School",
            className = "Class VII",
            section = "A",
            subject = "Science",
        )

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) { repository.saveUser(newUser) }
        assertTrue(saveRes.isSuccess)
        val docId = saveRes.getOrThrow()

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateUserProfile(docId, "9911223344", "updatedPass99")
        }
        assertTrue(updateRes.isSuccess)

        val users = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUsers(uid).first { list ->
                list.any { it.id == docId && it.phone == "9911223344" && it.password == "updatedPass99" }
            }
        }
        assertTrue(users.any { it.id == docId })
    }

    @Test
    fun crossUserAccess_bobCannotUpdateAliceUserProfile() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SchoolRepository(firestore)
        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveUser(
                SchoolUser(
                    ownerUid = aliceUid,
                    role = "STUDENT",
                    userId = "DPS-${UUID.randomUUID().toString().take(6)}",
                    password = "stud123",
                    fullName = "Aarav Test",
                    phone = "9412510001",
                    wing = "Upper School",
                    className = "Class X",
                    section = "A",
                    parentName = "Mr. Test",
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SchoolRepository(firestore)
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.updateUserProfile(docId, "0000000000", "hacked")
        }
        assertTrue(updateResult.isFailure)
        val firestoreException = updateResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeUsers_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = SchoolRepository(firestore)
        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { repository.observeUsers("some_uid").first() }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_dps@test.com"
        const val BOB_EMAIL = "bob_dps@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
