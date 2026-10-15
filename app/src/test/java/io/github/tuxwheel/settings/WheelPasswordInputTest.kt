package io.github.tuxwheel.settings

import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.models.PasswordOperation
import org.junit.Test

class WheelPasswordInputTest {
    @Test fun `setting requires a matching confirmation`() {
        assertThat(validWheelPasswordInput(PasswordOperation.SET, "", "0012", "0012")).isTrue()
        assertThat(validWheelPasswordInput(PasswordOperation.SET, "", "0012", "")).isFalse()
        assertThat(validWheelPasswordInput(PasswordOperation.SET, "", "0012", "0034")).isFalse()
    }

    @Test fun `changing requires current password and matching new passwords`() {
        assertThat(validWheelPasswordInput(PasswordOperation.CHANGE, "0012", "0034", "0034")).isTrue()
        assertThat(validWheelPasswordInput(PasswordOperation.CHANGE, "", "0034", "0034")).isFalse()
        assertThat(validWheelPasswordInput(PasswordOperation.CHANGE, "0012", "0034", "")).isFalse()
        assertThat(validWheelPasswordInput(PasswordOperation.CHANGE, "0012", "0034", "0056")).isFalse()
    }

    @Test fun `passwords must contain exactly four ASCII digits`() {
        listOf("", "123", "12345", "abcd", "１２３４").forEach { invalid ->
            assertThat(validWheelPasswordInput(PasswordOperation.SET, "", invalid, invalid)).isFalse()
            assertThat(validWheelPasswordInput(PasswordOperation.CHANGE, invalid, "0034", "0034")).isFalse()
            assertThat(validWheelPasswordInput(PasswordOperation.CHANGE, "0012", invalid, invalid)).isFalse()
            assertThat(validWheelPasswordInput(PasswordOperation.REMOVE, invalid, "", "")).isFalse()
        }
    }

    @Test fun `removal only requires the current password`() {
        assertThat(validWheelPasswordInput(PasswordOperation.REMOVE, "0012", "", "")).isTrue()
    }
}
