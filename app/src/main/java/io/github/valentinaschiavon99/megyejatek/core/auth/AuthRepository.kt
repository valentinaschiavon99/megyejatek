package io.github.valentinaschiavon99.megyejatek.core.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class Player(val uid: String, val username: String)

enum class AuthError { InvalidUsername, WeakPassword, UsernameTaken, WrongCredentials, TooManyAttempts, Network, Unavailable, Unknown }

sealed interface AuthResult {
    data class Success(val player: Player) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/**
 * Username + password accounts on Firebase Authentication.
 *
 * Players never give an e-mail address: the username is mapped to an internal address
 * (see [CredentialPolicy.internalEmail]), which also makes usernames unique, case-insensitively.
 * Passwords are only ever passed straight to Firebase; the app never stores or logs them.
 */
@Singleton
class AuthRepository @Inject constructor(@ApplicationContext context: Context) {

    /** False when the app was built without google-services.json: it then runs offline-only. */
    val isAvailable: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? = if (isAvailable) FirebaseAuth.getInstance() else null

    val currentPlayer: Player? get() = auth?.currentUser?.toPlayer()

    val player: Flow<Player?> = callbackFlow {
        val a = auth
        if (a == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toPlayer()) }
        a.addAuthStateListener(listener)
        awaitClose { a.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    suspend fun register(username: String, password: String): AuthResult {
        val a = auth ?: return AuthResult.Failure(AuthError.Unavailable)
        val name = username.trim()
        if (CredentialPolicy.usernameProblem(name) != null) return AuthResult.Failure(AuthError.InvalidUsername)
        if (CredentialPolicy.passwordProblems(password, name).isNotEmpty()) {
            return AuthResult.Failure(AuthError.WeakPassword)
        }
        return runCatchingAuth {
            val user = a.createUserWithEmailAndPassword(CredentialPolicy.internalEmail(name), password).await().user
                ?: throw IllegalStateException("no user after sign-up")
            try {
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
                FirebaseFirestore.getInstance().collection(USERS).document(user.uid).set(
                    mapOf(
                        "username" to name,
                        "usernameLower" to CredentialPolicy.normalizedUsername(name),
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } catch (e: Exception) {
                // Don't leave a half-created account behind (it could never post scores).
                runCatching { user.delete().await() }
                throw e
            }
            Player(user.uid, name)
        }
    }

    suspend fun login(username: String, password: String): AuthResult {
        val a = auth ?: return AuthResult.Failure(AuthError.Unavailable)
        val name = username.trim()
        if (CredentialPolicy.usernameProblem(name) != null || password.isEmpty()) {
            return AuthResult.Failure(AuthError.WrongCredentials)
        }
        return runCatchingAuth {
            val user = a.signInWithEmailAndPassword(CredentialPolicy.internalEmail(name), password).await().user
                ?: throw IllegalStateException("no user after sign-in")
            user.toPlayer()
        }
    }

    fun logout() {
        auth?.signOut()
    }

    /**
     * Deletes the account and everything stored online for it: its scores, its profile and the
     * Firebase user. Firebase requires a recent sign-in for this, so the password is asked again.
     * Returns the deleted uid, so local data can be removed too.
     */
    suspend fun deleteAccount(password: String): Result<String> {
        val a = auth ?: return Result.failure(IllegalStateException("unavailable"))
        val user = a.currentUser ?: return Result.failure(IllegalStateException("signed out"))
        return try {
            val email = user.email ?: throw IllegalStateException("no email")
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            val db = FirebaseFirestore.getInstance()
            // Scores first, then the profile, then the login itself (the rules let owners delete their own).
            while (true) {
                val page = db.collection("scores").whereEqualTo("uid", user.uid).limit(400).get().await()
                if (page.isEmpty) break
                val batch = db.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            db.collection(USERS).document(user.uid).delete().await()
            val uid = user.uid
            user.delete().await()
            Result.success(uid)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(WrongPasswordException())
        } catch (e: FirebaseException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        }
    }

    class WrongPasswordException : Exception("wrong password")

    private inline fun runCatchingAuth(block: () -> Player): AuthResult = try {
        AuthResult.Success(block())
    } catch (e: FirebaseAuthWeakPasswordException) {
        AuthResult.Failure(AuthError.WeakPassword)
    } catch (e: FirebaseAuthUserCollisionException) {
        AuthResult.Failure(AuthError.UsernameTaken)
    } catch (e: FirebaseAuthInvalidUserException) {
        // Same message as a wrong password: don't reveal which usernames exist.
        AuthResult.Failure(AuthError.WrongCredentials)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        AuthResult.Failure(AuthError.WrongCredentials)
    } catch (e: FirebaseTooManyRequestsException) {
        AuthResult.Failure(AuthError.TooManyAttempts)
    } catch (e: FirebaseNetworkException) {
        AuthResult.Failure(AuthError.Network)
    } catch (e: FirebaseException) {
        AuthResult.Failure(AuthError.Unknown)
    } catch (e: IllegalStateException) {
        AuthResult.Failure(AuthError.Unknown)
    }

    private fun FirebaseUser.toPlayer() = Player(
        uid = uid,
        username = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty().substringBefore('@'),
    )

    companion object {
        const val USERS = "users"
    }
}
