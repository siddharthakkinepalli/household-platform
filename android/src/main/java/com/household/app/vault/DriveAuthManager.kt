package com.household.app.vault

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class DriveAuthManager(context: Context) {
    private val appContext = context.applicationContext

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(appContext, gso)

    private val gsoFull = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE))
        .build()

    private val googleSignInClientFull = GoogleSignIn.getClient(appContext, gsoFull)

    fun signInIntent(): Intent = googleSignInClient.signInIntent

    fun fullDriveSignInIntent(): Intent = googleSignInClientFull.signInIntent

    fun lastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(appContext)

    fun hasFullDriveScope(): Boolean {
        val account = lastSignedInAccount() ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE))
    }

    fun signOut() {
        googleSignInClient.signOut()
    }
}
