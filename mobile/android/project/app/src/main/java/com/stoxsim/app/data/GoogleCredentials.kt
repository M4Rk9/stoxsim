package com.stoxsim.app.data

import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

internal data class GoogleChallenge(val clientId: String, val binding: String, val nonce: String)
internal class GoogleSignInFailure(message: String) : Exception(message)

/** Google credentials and challenge bindings exist only for this foreground attempt. */
internal object GoogleCredentials {
    suspend fun request(context: Context, challenge: GoogleChallenge): String {
        try {
            val option = GetSignInWithGoogleOption.Builder(challenge.clientId).setNonce(challenge.nonce).build()
            val result = CredentialManager.create(context).getCredential(MutableContextWrapper(context),
                GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = result.credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw GoogleSignInFailure("Google returned an unsupported credential. Try again.")
            }
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (cancelled: GetCredentialCancellationException) {
            throw GoogleSignInFailure("Google sign-in was cancelled. You can try again.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: GetCredentialException) {
            throw GoogleSignInFailure("Google sign-in is unavailable. Check your Google account and Google Play services, then try again.")
        } catch (failure: IllegalArgumentException) {
            throw GoogleSignInFailure("Google sign-in could not be completed. Try again.")
        }
    }
    suspend fun clear(context: Context) {
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* StoxSim session revocation is independent of provider UI state. */ }
    }
}
