package com.workday.torque.app

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.workday.torque.app.ui.login.LoginActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginTest {

    @get:Rule
    val activityRule = ActivityTestRule(LoginActivity::class.java)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(WRITE_EXTERNAL_STORAGE)

    @MediumTest
    @Test
    fun username() {
        onView(withId(R.id.username)).perform(typeText("torqueUser"))
    }

    @MediumTest
    @Test
    fun password() {
        onView(withId(R.id.password)).perform(typeText("torqueUser"))
    }
}
