package com.stoxsim.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stoxsim.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

internal data class AppState(
    val restoring: Boolean = true, val restoreFailed: Boolean = false,
    val working: Boolean = false, val user: User? = null,
    val region: String = "INDIA", val portfolio: Portfolio? = null,
    val instruments: List<Instrument> = emptyList(), val searched: Boolean = false,
    val error: String? = null, val notice: String? = null
)

internal class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val api = (application as StoxSimApplication).api
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()

    init { restore() }

    fun restore() {
        if (mutable.value.working) return
        mutable.update { it.copy(restoring = true, restoreFailed = false, working = true, error = null) }
        viewModelScope.launch {
            try {
                mutable.update { it.copy(user = api.restore(), restoring = false) }
            } catch (_: SessionExpired) {
                mutable.update { it.copy(user = null, restoring = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(restoring = false, restoreFailed = true, error = "Cannot reconnect to StoxSim. Check your connection and retry.") }
            } finally {
                mutable.update { it.copy(working = false) }
            }
        }
    }

    fun authenticate(email: String, password: String, name: String?) = runAction {
        val user = api.authenticate(email, password, name)
        mutable.value = AppState(restoring = false, working = true, user = user)
    }

    fun forgot(email: String) = runAction {
        api.forgotPassword(email)
        mutable.update { it.copy(notice = "If an account exists for this email, a reset link has been sent. Open the link in your email to finish resetting your password.") }
    }

    fun refreshUser() = runAction { mutable.update { it.copy(user = api.currentUser()) } }

    fun loadPortfolio() = runAction {
        val result = api.portfolio(mutable.value.region)
        mutable.update { it.copy(portfolio = result) }
    }

    fun search(query: String) = runAction {
        mutable.update { it.copy(instruments = emptyList(), searched = false) }
        val result = api.search(mutable.value.region, query)
        mutable.update { it.copy(instruments = result, searched = true) }
    }

    fun changeRegion(region: String) {
        if (!mutable.value.working && region in listOf("INDIA", "UNITED_STATES")) {
            mutable.update { it.copy(region = region, portfolio = null, instruments = emptyList(), searched = false, error = null, notice = null) }
        }
    }

    fun clearMessage() { mutable.update { it.copy(error = null, notice = null) } }

    fun logout() = runAction {
        val revoked = api.logout()
        mutable.value = AppState(restoring = false, working = true,
            notice = if (revoked) "Signed out." else "Signed out on this device. Server sign-out could not be confirmed. You can revoke this session in your account settings on stoxsim.com.")
    }

    private fun runAction(action: suspend () -> Unit) {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SessionExpired) {
                mutable.value = AppState(restoring = false, working = true, error = "Your session has expired. Please sign in again.")
            } catch (failure: Exception) {
                val message = when (failure) {
                    is ApiFailure -> failure.message
                    is IOException -> if (failure.message == "Email or password is incorrect.") failure.message else "Could not complete the request. Check your connection and try again."
                    else -> "Could not load this information. Please try again."
                }
                mutable.update { it.copy(error = message) }
            } finally {
                mutable.update { it.copy(working = false) }
            }
        }
    }
}
